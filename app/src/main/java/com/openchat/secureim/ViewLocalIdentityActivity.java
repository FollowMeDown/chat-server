package com.openchat.secureim;

import android.os.Bundle;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import com.openchat.secureim.crypto.IdentityKeyUtil;
import com.openchat.imservice.crypto.IdentityKeyParcelable;

public class ViewLocalIdentityActivity extends ViewIdentityActivity {

  public void onCreate(Bundle bundle) {
    getIntent().putExtra(ViewIdentityActivity.IDENTITY_KEY,
                         new IdentityKeyParcelable(IdentityKeyUtil.getIdentityKey(this)));
    getIntent().putExtra(ViewIdentityActivity.TITLE,
                         getString(R.string.ViewIdentityActivity_my_identity_fingerprint));
    super.onCreate(bundle);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    MenuInflater inflater = this.getSupportMenuInflater();
    inflater.inflate(R.menu.local_identity, menu);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
        case android.R.id.home:finish(); return true;
    }

    return false;
  }
}