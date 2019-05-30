package com.openchat.secureim.contacts;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Pair;

import com.openchat.secureim.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContactsDatabase {

  private static final String TAG = ContactsDatabase.class.getSimpleName();

  public static final String ID_COLUMN           = "_id";
  public static final String NAME_COLUMN         = "name";
  public static final String NUMBER_COLUMN       = "number";
  public static final String NUMBER_TYPE_COLUMN  = "number_type";
  public static final String LABEL_COLUMN        = "label";
  public static final String CONTACT_TYPE_COLUMN = "contact_type";

  public static final int NORMAL_TYPE = 0;
  public static final int PUSH_TYPE   = 1;

  private final Context context;

  public ContactsDatabase(Context context) {
    this.context  = context;
  }

  public synchronized void setRegisteredUsers(Account account, List<String> e164numbers)
      throws RemoteException, OperationApplicationException
  {
    Map<String, Long>                   currentContacts    = new HashMap<>();
    Set<String>                         registeredNumbers  = new HashSet<>(e164numbers);
    ArrayList<ContentProviderOperation> operations         = new ArrayList<>();
    Uri                                 currentContactsUri = RawContacts.CONTENT_URI.buildUpon()
                                                                                    .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
                                                                                    .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type).build();

    Cursor cursor = null;

    try {
      cursor = context.getContentResolver().query(currentContactsUri, new String[] {BaseColumns._ID, RawContacts.SYNC1}, null, null, null);

      while (cursor != null && cursor.moveToNext()) {
        currentContacts.put(cursor.getString(1), cursor.getLong(0));
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    for (String number : e164numbers) {
      if (!currentContacts.containsKey(number)) {
        addOpenchatServiceRawContact(operations, account, number);
      }
    }

    for (Map.Entry<String, Long> currentContactEntry : currentContacts.entrySet()) {
      if (!registeredNumbers.contains(currentContactEntry.getKey())) {
        removeOpenchatServiceRawContact(operations, account, currentContactEntry.getValue());
      }
    }

    if (!operations.isEmpty()) {
      context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operations);
    }
  }

  private void addOpenchatServiceRawContact(List<ContentProviderOperation> operations,
                                       Account account, String e164number)
  {
    int index   = operations.size();
    Uri dataUri = ContactsContract.Data.CONTENT_URI.buildUpon()
                                                   .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                                                   .build();

    operations.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                                           .withValue(RawContacts.ACCOUNT_NAME, account.name)
                                           .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                                           .withValue(RawContacts.SYNC1, e164number)
                                           .build());

    operations.add(ContentProviderOperation.newInsert(dataUri)
                                           .withValueBackReference(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, index)
                                           .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                           .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, e164number)
                                           .withValue(ContactsContract.Data.SYNC2, "__TS")
                                           .build());

    operations.add(ContentProviderOperation.newInsert(dataUri)
                                           .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index)
                                           .withValue(ContactsContract.Data.MIMETYPE, "vnd.android.cursor.item/vnd.com.openchat.secureim.contact")
                                           .withValue(ContactsContract.Data.DATA1, e164number)
                                           .withValue(ContactsContract.Data.DATA2, context.getString(R.string.app_name))
                                           .withValue(ContactsContract.Data.DATA3, String.format("Message %s", e164number))
                                           .withYieldAllowed(true)
                                           .build());
  }

  private void removeOpenchatServiceRawContact(List<ContentProviderOperation> operations,
                                          Account account, long rowId)
  {
    operations.add(ContentProviderOperation.newDelete(RawContacts.CONTENT_URI.buildUpon()
                                                                             .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
                                                                             .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
                                                                             .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
                                           .withYieldAllowed(true)
                                           .withSelection(BaseColumns._ID + " = ?", new String[] {String.valueOf(rowId)})
                                           .build());
  }

  public @NonNull Cursor querySystemContacts(String filter) {
    Uri uri;

    if (!TextUtils.isEmpty(filter)) {
      uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(filter));
    } else {
      uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
    }

    String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone._ID,
                                       ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                       ContactsContract.CommonDataKinds.Phone.NUMBER,
                                       ContactsContract.CommonDataKinds.Phone.TYPE,
                                       ContactsContract.CommonDataKinds.Phone.LABEL};

    String sort = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC";

    Map<String, String> projectionMap = new HashMap<String, String>() {{
      put(ID_COLUMN, ContactsContract.CommonDataKinds.Phone._ID);
      put(NAME_COLUMN, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
      put(NUMBER_COLUMN, ContactsContract.CommonDataKinds.Phone.NUMBER);
      put(NUMBER_TYPE_COLUMN, ContactsContract.CommonDataKinds.Phone.TYPE);
      put(LABEL_COLUMN, ContactsContract.CommonDataKinds.Phone.LABEL);
    }};

    Cursor cursor = context.getContentResolver().query(uri, projection,
                                                       ContactsContract.Data.SYNC2 + " IS NULL OR " +
                                                       ContactsContract.Data.SYNC2 + " != ?",
                                                       new String[] {"__TS"},
                                                       sort);

    return new ProjectionMappingCursor(cursor, projectionMap,
                                       new Pair<String, Object>(CONTACT_TYPE_COLUMN, NORMAL_TYPE));
  }

  public @NonNull Cursor queryOpenchatServiceContacts(String filter) {
    String[] projection = new String[] {ContactsContract.Data._ID,
                                        ContactsContract.Contacts.DISPLAY_NAME,
                                        ContactsContract.Data.DATA1};

    String  sort = ContactsContract.Contacts.DISPLAY_NAME + " COLLATE NOCASE ASC";

    Map<String, String> projectionMap = new HashMap<String, String>(){{
      put(ID_COLUMN, ContactsContract.Data._ID);
      put(NAME_COLUMN, ContactsContract.Contacts.DISPLAY_NAME);
      put(NUMBER_COLUMN, ContactsContract.Data.DATA1);
    }};

    Cursor cursor;

    if (TextUtils.isEmpty(filter)) {
      cursor = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                                                  projection,
                                                  ContactsContract.Data.MIMETYPE + " = ?",
                                                  new String[] {"vnd.android.cursor.item/vnd.com.openchat.secureim.contact"},
                                                  sort);
    } else {
      cursor = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                                                  projection,
                                                  ContactsContract.Data.MIMETYPE + " = ? AND " + ContactsContract.Contacts.DISPLAY_NAME + " LIKE ?",
                                                  new String[] {"vnd.android.cursor.item/vnd.com.openchat.secureim.contact",
                                                                "%" + filter + "%"},
                                                  sort);
    }

    return new ProjectionMappingCursor(cursor, projectionMap,
                                       new Pair<String, Object>(LABEL_COLUMN, "OpenchatService"),
                                       new Pair<String, Object>(NUMBER_TYPE_COLUMN, 0),
                                       new Pair<String, Object>(CONTACT_TYPE_COLUMN, PUSH_TYPE));

  }

  public Cursor getNewNumberCursor(String filter) {
    MatrixCursor newNumberCursor = new MatrixCursor(new String[] {ID_COLUMN, NAME_COLUMN, NUMBER_COLUMN, NUMBER_TYPE_COLUMN, LABEL_COLUMN, CONTACT_TYPE_COLUMN}, 1);
    newNumberCursor.addRow(new Object[]{-1L, context.getString(R.string.contact_selection_list__unknown_contact),
                                        filter, ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM,
                                        "\u21e2", NORMAL_TYPE});

    return newNumberCursor;
  }

  private static class ProjectionMappingCursor extends CursorWrapper {

    private final Map<String, String>    projectionMap;
    private final Pair<String, Object>[] extras;

    @SafeVarargs
    public ProjectionMappingCursor(Cursor cursor,
                                   Map<String, String> projectionMap,
                                   Pair<String, Object>... extras)
    {
      super(cursor);
      this.projectionMap = projectionMap;
      this.extras        = extras;
    }

    @Override
    public int getColumnCount() {
      return super.getColumnCount() + extras.length;
    }

    @Override
    public int getColumnIndex(String columnName) {
      for (int i=0;i<extras.length;i++) {
        if (extras[i].first.equals(columnName)) {
          return super.getColumnCount() + i;
        }
      }

      return super.getColumnIndex(projectionMap.get(columnName));
    }

    @Override
    public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
      int index = getColumnIndex(columnName);

      if (index == -1) throw new IllegalArgumentException("Bad column name!");
      else             return index;
    }

    @Override
    public String getColumnName(int columnIndex) {
      int baseColumnCount = super.getColumnCount();

      if (columnIndex >= baseColumnCount) {
        int offset = columnIndex - baseColumnCount;
        return extras[offset].first;
      }

      return getReverseProjection(super.getColumnName(columnIndex));
    }

    @Override
    public String[] getColumnNames() {
      String[] names    = super.getColumnNames();
      String[] allNames = new String[names.length + extras.length];

      for (int i=0;i<names.length;i++) {
        allNames[i] = getReverseProjection(names[i]);
      }

      for (int i=0;i<extras.length;i++) {
        allNames[names.length + i] = extras[i].first;
      }

      return allNames;
    }

    @Override
    public int getInt(int columnIndex) {
      if (columnIndex >= super.getColumnCount()) {
        int offset = columnIndex - super.getColumnCount();
        return (Integer)extras[offset].second;
      }

      return super.getInt(columnIndex);
    }

    @Override
    public String getString(int columnIndex) {
      if (columnIndex >= super.getColumnCount()) {
        int offset = columnIndex - super.getColumnCount();
        return (String)extras[offset].second;
      }

      return super.getString(columnIndex);
    }

    private @Nullable String getReverseProjection(String columnName) {
      for (Map.Entry<String, String> entry : projectionMap.entrySet()) {
        if (entry.getValue().equals(columnName)) {
          return entry.getKey();
        }
      }

      return null;
    }
  }
}
