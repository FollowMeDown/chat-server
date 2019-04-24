package com.openchat.secureim.contacts;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.support.v4.widget.CursorAdapter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.openchat.secureim.R;
import com.openchat.secureim.util.BitmapWorkerRunnable;
import com.openchat.secureim.util.BitmapWorkerRunnable.AsyncDrawable;
import com.openchat.secureim.util.TaggedFutureTask;
import com.openchat.secureim.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;

public class ContactSelectionListAdapter extends    CursorAdapter
                                         implements StickyListHeadersAdapter
{
  private final static String TAG = "ContactListAdapter";

  private final static ExecutorService photoResolver = Util.newSingleThreadedLifoExecutor();

  private final static int STYLE_ATTRIBUTES[] = new int[]{R.attr.contact_selection_push_user,
                                                          R.attr.contact_selection_lay_user,
                                                          R.attr.contact_selection_label_text};

  private int TYPE_COLUMN        = -1;
  private int NAME_COLUMN        = -1;
  private int NUMBER_COLUMN      = -1;
  private int NUMBER_TYPE_COLUMN = -1;
  private int LABEL_COLUMN       = -1;
  private int ID_COLUMN          = -1;

  private final Context        context;
  private final boolean        multiSelect;
  private final LayoutInflater li;
  private final TypedArray     drawables;
  private final int            scaledPhotoSize;

  private final HashMap<Long, ContactAccessor.ContactData> selectedContacts = new HashMap<>();

  public ContactSelectionListAdapter(Context context, Cursor cursor, boolean multiSelect) {
    super(context, cursor, 0);
    this.context             = context;
    this.li                  = LayoutInflater.from(context);
    this.drawables           = context.obtainStyledAttributes(STYLE_ATTRIBUTES);
    this.multiSelect         = multiSelect;
    this.scaledPhotoSize     = context.getResources().getDimensionPixelSize(R.dimen.contact_selection_photo_size);
  }

  public static class ViewHolder {
    public CheckBox  checkBox;
    public TextView  name;
    public TextView  number;
    public ImageView contactPhoto;
    public int       position;
  }

  public static class DataHolder {
    public int    type;
    public String name;
    public String number;
    public int    numberType;
    public String label;
    public long   id;
  }

  public static class HeaderViewHolder {
    TextView text;
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    final View v                 = li.inflate(R.layout.push_contact_selection_list_item, parent, false);
    final ViewHolder holder      = new ViewHolder();

    if (v != null) {
      holder.name         = (TextView) v.findViewById(R.id.name);
      holder.number       = (TextView) v.findViewById(R.id.number);
      holder.checkBox     = (CheckBox) v.findViewById(R.id.check_box);
      holder.contactPhoto = (ImageView) v.findViewById(R.id.contact_photo_image);

      if (!multiSelect) holder.checkBox.setVisibility(View.GONE);

      v.setTag(R.id.holder_tag, holder);
      v.setTag(R.id.contact_info_tag, new DataHolder());
    }
    return v;
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    final DataHolder contactData = (DataHolder) view.getTag(R.id.contact_info_tag);
    final ViewHolder holder      = (ViewHolder) view.getTag(R.id.holder_tag);
    if (holder == null) {
      Log.w(TAG, "ViewHolder was null. This should not happen.");
      return;
    }
    if (contactData == null) {
      Log.w(TAG, "DataHolder was null. This should not happen.");
      return;
    }
    if (ID_COLUMN < 0) {
      populateColumnIndices(cursor);
    }

    contactData.type       = cursor.getInt(TYPE_COLUMN);
    contactData.name       = cursor.getString(NAME_COLUMN);
    contactData.number     = cursor.getString(NUMBER_COLUMN);
    contactData.numberType = cursor.getInt(NUMBER_TYPE_COLUMN);
    contactData.label      = cursor.getString(LABEL_COLUMN);
    contactData.id         = cursor.getLong(ID_COLUMN);

    if (contactData.type != ContactsDatabase.PUSH_TYPE) {
      holder.name.setTextColor(drawables.getColor(1, 0xff000000));
      holder.number.setTextColor(drawables.getColor(1, 0xff000000));
    } else {
      holder.name.setTextColor(drawables.getColor(0, 0xa0000000));
      holder.number.setTextColor(drawables.getColor(0, 0xa0000000));
    }

    if (selectedContacts.containsKey(contactData.id)) {
      holder.checkBox.setChecked(true);
    } else {
      holder.checkBox.setChecked(false);
    }

    holder.name.setText(contactData.name);

    if (contactData.number == null || contactData.number.isEmpty()) {
      holder.name.setEnabled(false);
      holder.number.setText("");
    } else if (contactData.type == ContactsDatabase.PUSH_TYPE) {
      holder.number.setText(contactData.number);
    } else {
      final CharSequence label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.getResources(),
                                                                                     contactData.numberType, contactData.label);
      final CharSequence numberWithLabel = contactData.number + "  " + label;
      final Spannable    numberLabelSpan = new SpannableString(numberWithLabel);
      numberLabelSpan.setSpan(new ForegroundColorSpan(drawables.getColor(2, 0xff444444)), contactData.number.length(), numberWithLabel.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
      holder.number.setText(numberLabelSpan);
    }
    holder.contactPhoto.setImageDrawable(ContactPhotoFactory.getLoadingPhoto(context));
    if (contactData.id > -1) loadBitmap(contactData.number, holder.contactPhoto);
  }

  @Override
  public View getHeaderView(int i, View convertView, ViewGroup viewGroup) {
    final Cursor c = getCursor();
    final HeaderViewHolder holder;
    if (convertView == null) {
      holder = new HeaderViewHolder();
      convertView = li.inflate(R.layout.push_contact_selection_list_header, viewGroup, false);
      holder.text = (TextView) convertView.findViewById(R.id.text);
      convertView.setTag(holder);
    } else {
      holder = (HeaderViewHolder) convertView.getTag();
    }
    c.moveToPosition(i);

    final int type = c.getInt(c.getColumnIndexOrThrow(ContactsDatabase.TYPE_COLUMN));
    final int headerTextRes;
    switch (type) {
    case 1:  headerTextRes = R.string.contact_selection_list__header_openchatservice_users; break;
    default: headerTextRes = R.string.contact_selection_list__header_other;            break;
    }
    holder.text.setText(headerTextRes);
    return convertView;
  }

  @Override
  public long getHeaderId(int i) {
    final Cursor c = getCursor();
    c.moveToPosition(i);
    return c.getInt(c.getColumnIndexOrThrow(ContactsDatabase.TYPE_COLUMN));
  }

  public boolean cancelPotentialWork(String number, ImageView imageView) {
    final TaggedFutureTask<?> bitmapWorkerTask = AsyncDrawable.getBitmapWorkerTask(imageView);

    if (bitmapWorkerTask != null) {
      final Object tag = bitmapWorkerTask.getTag();
      if (tag != null && !tag.equals(number)) {
        bitmapWorkerTask.cancel(true);
      } else {
        return false;
      }
    }
    return true;
  }

  public void loadBitmap(String number, ImageView imageView) {
    if (cancelPotentialWork(number, imageView)) {
      final BitmapWorkerRunnable runnable = new BitmapWorkerRunnable(context, imageView, number, scaledPhotoSize);
      final TaggedFutureTask<?> task      = new TaggedFutureTask<Void>(runnable, null, number);
      final AsyncDrawable asyncDrawable   = new AsyncDrawable(task);

      imageView.setImageDrawable(asyncDrawable);
      if (!task.isCancelled()) photoResolver.execute(new FutureTask<Void>(task, null));
    }
  }

  public Map<Long,ContactAccessor.ContactData> getSelectedContacts() {
    return selectedContacts;
  }

  private void populateColumnIndices(final Cursor cursor) {
    this.TYPE_COLUMN        = cursor.getColumnIndexOrThrow(ContactsDatabase.TYPE_COLUMN);
    this.NAME_COLUMN        = cursor.getColumnIndexOrThrow(ContactsDatabase.NAME_COLUMN);
    this.NUMBER_COLUMN      = cursor.getColumnIndexOrThrow(ContactsDatabase.NUMBER_COLUMN);
    this.NUMBER_TYPE_COLUMN = cursor.getColumnIndexOrThrow(ContactsDatabase.NUMBER_TYPE_COLUMN);
    this.LABEL_COLUMN       = cursor.getColumnIndexOrThrow(ContactsDatabase.LABEL_COLUMN);
    this.ID_COLUMN          = cursor.getColumnIndexOrThrow(ContactsDatabase.ID_COLUMN);
  }
}
