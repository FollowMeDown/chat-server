package com.openchat.secureim;

import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import com.openchat.secureim.contacts.ContactSelectionListAdapter;
import com.openchat.secureim.contacts.ContactSelectionListItem;
import com.openchat.secureim.contacts.ContactsCursorLoader;
import com.openchat.secureim.util.OpenchatServicePreferences;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

public class ContactSelectionListFragment extends    Fragment
                                          implements LoaderManager.LoaderCallbacks<Cursor>
{
  private static final String TAG = ContactSelectionListFragment.class.getSimpleName();

  private TextView emptyText;

  private Map<Long, String>         selectedContacts;
  private OnContactSelectedListener onContactSelectedListener;
  private StickyListHeadersListView listView;
  private SwipeRefreshLayout        swipeRefresh;
  private String                    cursorFilter;

  private boolean multi = false;

  @Override
  public void onActivityCreated(Bundle icicle) {
    super.onCreate(icicle);
    initializeCursor();
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.contact_selection_list_fragment, container, false);

    emptyText      = (TextView)                  view.findViewById(android.R.id.empty);
    swipeRefresh   = (SwipeRefreshLayout)        view.findViewById(R.id.swipe_refresh);
    listView       = (StickyListHeadersListView) view.findViewById(android.R.id.list);
    listView.setFocusable(true);
    listView.setFastScrollEnabled(true);
    listView.setDrawingListUnderStickyHeader(false);
    listView.setOnItemClickListener(new ListClickListener());

    swipeRefresh.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN);

    return view;
  }

  public List<String> getSelectedContacts() {
    if (selectedContacts == null) return null;

    List<String> selected = new LinkedList<>();
    selected.addAll(selectedContacts.values());

    return selected;
  }

  public void setMultiSelect(boolean multi) {
    this.multi = multi;
  }

  private void initializeCursor() {
    ContactSelectionListAdapter adapter = new ContactSelectionListAdapter(getActivity(), null, multi);
    selectedContacts = adapter.getSelectedContacts();
    listView.setAdapter(adapter);
    this.getLoaderManager().initLoader(0, null, this);
  }

  public void setQueryFilter(String filter) {
    this.cursorFilter = filter;
    this.getLoaderManager().restartLoader(0, null, this);
  }

  public void resetQueryFilter() {
    setQueryFilter(null);
    swipeRefresh.setRefreshing(false);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    boolean pushOnly    = getActivity().getIntent().getBooleanExtra(ContactSelectionActivity.PUSH_ONLY_EXTRA, false);
    boolean supportsSms = OpenchatServicePreferences.isSmsEnabled(getActivity());

    return new ContactsCursorLoader(getActivity(), !pushOnly && supportsSms, cursorFilter);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    ((CursorAdapter) listView.getAdapter()).changeCursor(data);
    emptyText.setText(R.string.contact_selection_group_activity__no_contacts);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    ((CursorAdapter) listView.getAdapter()).changeCursor(null);
  }

  private class ListClickListener implements AdapterView.OnItemClickListener {
    @Override
    public void onItemClick(AdapterView<?> l, View v, int position, long id) {
      ContactSelectionListItem contact = (ContactSelectionListItem)v;

      if (!multi || !selectedContacts.containsKey(contact.getContactId())) {
        selectedContacts.put(contact.getContactId(), contact.getNumber());
        contact.setChecked(true);
        if (onContactSelectedListener != null) onContactSelectedListener.onContactSelected(contact.getNumber());
      } else {
        selectedContacts.remove(contact.getContactId());
        contact.setChecked(false);
      }
    }
  }

  public void setOnContactSelectedListener(OnContactSelectedListener onContactSelectedListener) {
    this.onContactSelectedListener = onContactSelectedListener;
  }

  public void setOnRefreshListener(SwipeRefreshLayout.OnRefreshListener onRefreshListener) {
    this.swipeRefresh.setOnRefreshListener(onRefreshListener);
  }

  public interface OnContactSelectedListener {
    public void onContactSelected(String number);
  }
}
