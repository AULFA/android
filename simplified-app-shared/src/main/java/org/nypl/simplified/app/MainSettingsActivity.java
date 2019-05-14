package org.nypl.simplified.app;

import android.content.res.Resources;
import android.os.Bundle;

import org.nypl.simplified.books.core.LogUtilities;
import org.slf4j.Logger;

/**
 * The activity displaying the settings for the application.
 */

public final class MainSettingsActivity extends NavigationDrawerActivity {

  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(MainSettingsActivity.class);
  }

  /**
   * Construct an activity.
   */
  public MainSettingsActivity() {

  }

  @Override
  protected boolean navigationDrawerShouldShowIndicator() {
    return true;
  }

  @Override
  protected String navigationDrawerGetActivityTitle(final Resources resources) {
    return resources.getString(R.string.settings);
  }

  @Override
  public void onCreate(final Bundle saved_instance_state) {
    super.onCreate(saved_instance_state);

    getFragmentManager()
      .beginTransaction()
      .replace(R.id.content_frame, new MainSettingsFragment())
      .commit();
  }
}
