package org.nypl.simplified.app.profiles;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;

import org.joda.time.LocalDate;
import org.nypl.simplified.app.AnalyticsUtilities;
import org.nypl.simplified.app.R;
import org.nypl.simplified.app.Simplified;
import org.nypl.simplified.app.SimplifiedActivity;
import org.nypl.simplified.app.catalog.MainCatalogActivity;
import org.nypl.simplified.app.utilities.ErrorDialogUtilities;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileDeletionEvent;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileID;
import org.nypl.simplified.books.profiles.ProfileReadableType;
import org.nypl.simplified.observable.ObservableSubscriptionType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.nypl.simplified.app.Simplified.WantActionBar.WANT_NO_ACTION_BAR;
import static org.nypl.simplified.books.profiles.ProfileDeletionEvent.*;

/**
 * An activity that allows users to pick from a list of profiles, or to create a new profile.
 */

public final class ProfileSelectionActivity extends SimplifiedActivity {

  private static final Logger LOG = LogUtilities.getLog(ProfileSelectionActivity.class);

  private Button button;
  private ListView list;
  private ProfileArrayAdapter list_adapter;
  private ArrayList<ProfileReadableType> list_items;
  private ObservableSubscriptionType<ProfileEvent> profile_event_subscription;

  public ProfileSelectionActivity() {

  }

  @Override
  protected void onCreate(final Bundle state) {

    this.setTheme(Simplified.getCurrentTheme(WANT_NO_ACTION_BAR));
    super.onCreate(state);

    this.setContentView(R.layout.profiles_selection);

    this.list_items = new ArrayList<>();
    this.reloadProfiles();

    final ProfileSelectionListenerType listener =
      new ProfileSelectionListenerType() {
        @Override
        public void onProfileSelected(ProfileReadableType profile) {
          onSelectedProfile(profile);
        }

        @Override
        public void onProfileWantEdit(ProfileReadableType profile) {
          openEditDialog(profile);
        }

        @Override
        public void onProfileWantDelete(ProfileReadableType profile) {
          openDeleteDialog(profile);
        }
      };

    this.list_adapter = new ProfileArrayAdapter(this, this.list_items, listener);
    this.list = NullCheck.notNull(this.findViewById(R.id.profileSelectionList));
    this.list.setAdapter(this.list_adapter);

    this.button = NullCheck.notNull(this.findViewById(R.id.profileSelectionCreate));
    this.button.setOnClickListener(view -> openCreationDialog());

    final ProfilesControllerType profiles = Simplified.getProfilesController();
    profiles.profileIdleTimer().stop();

    this.profile_event_subscription =
      profiles.profileEvents()
        .subscribe(this::onProfileEvent);
  }

  private void onProfileEvent(ProfileEvent event) {
    reloadProfiles();

    if (event instanceof ProfileDeletionFailed) {
      final ProfileDeletionFailed failed = (ProfileDeletionFailed) event;
      ErrorDialogUtilities.showError(
        this,
        LOG,
        this.getResources().getString(R.string.profiles_deletion_error),
        failed.getException());
    }
  }

  private void openDeleteDialog(final ProfileReadableType profile) {
    new AlertDialog.Builder(this)
      .setTitle(R.string.profiles_delete)
      .setMessage(R.string.profiles_delete_confirm)
      .setNegativeButton(R.string.profiles_cancel_button, (dialog, which) -> dialog.dismiss())
      .setPositiveButton(R.string.profiles_delete_button, (dialog, which) -> {

        /*
         * XXX: Extremely ugly hack because we can't refer to a profile that's deleted.
         * Modern rewrites of this application correctly publish analytics events internally
         * in all cases.
         */

        Simplified.getAnalyticsController()
          .logToAnalytics(AnalyticsUtilities.INSTANCE.profileDeleted(profile));

        Simplified.getProfilesController().profileDelete(profile.id());
      })
      .show();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.profile_event_subscription.unsubscribe();
  }

  private void onSelectedProfile(
    final ProfileReadableType profile) {

    LOG.debug("selected profile: {} ({})", profile.id(), profile.displayName());
    final ProfilesControllerType profiles = Simplified.getProfilesController();

    Simplified.getAnalyticsController()
      .logToAnalytics(AnalyticsUtilities.INSTANCE.profileSelected(profile));

    if (Simplified.getNetworkConnectivity().isNetworkAvailable()) {
      String deviceId = Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);
      Simplified.getAnalyticsController().attemptToPushAnalytics(deviceId);
    }

    FluentFuture.from(
      profiles.profileSelect(profile.id()))
      .addCallback(new FutureCallback<Unit>() {
        @Override
        public void onSuccess(final Unit result) {
          onProfileSelectionSucceeded(result);
        }

        @Override
        public void onFailure(final Throwable e) {
          LOG.error("profile selection failed: ", e);
          onProfileSelectionFailed(e);
        }
      }, Simplified.getBackgroundTaskExecutor());
  }

  private void onProfileSelectionFailed(final Throwable e) {
    LOG.error("onProfileSelectionFailed: {}", e);

    /*
     * XXX: What exactly can anyone do about this? It's made worse by the fact that if this
     * happens here, the user can't even get into the program to report a bug directly...
     */

    ErrorDialogUtilities.showError(
      this,
      LOG,
      this.getResources().getString(R.string.profiles_selection_error_general),
      null);
  }

  private void onProfileSelectionSucceeded(final Unit ignored) {
    LOG.debug("onProfileSelectionSucceeded");
    LOG.debug("starting profile idle timer");
    Simplified.getProfilesController().profileIdleTimer().start();
    UIThread.runOnUIThread(this::openCatalog);
  }

  private void reloadProfiles() {
    LOG.debug("reloading profiles");

    UIThread.runOnUIThread(() -> {
      final ProfilesControllerType profiles = Simplified.getProfilesController();
      list_items.clear();
      list_items.addAll(profiles.profiles().values());
      Collections.sort(list_items);
      list_adapter.notifyDataSetChanged();
    });
  }

  private interface ProfileSelectionListenerType {

    void onProfileSelected(
      ProfileReadableType profile);

    void onProfileWantEdit(
      ProfileReadableType profile);

    void onProfileWantDelete(
      ProfileReadableType profile);
  }

  private static final class ProfileArrayAdapter extends ArrayAdapter<ProfileReadableType> {

    private final List<ProfileReadableType> list_items;
    private final Context context;
    private final ProfileSelectionListenerType selectionListener;

    ProfileArrayAdapter(
      final Context in_context,
      final ArrayList<ProfileReadableType> objects,
      final ProfileSelectionListenerType listener) {
      super(in_context, R.layout.profiles_list_item, objects);
      this.context =
        NullCheck.notNull(in_context, "Context");
      this.list_items =
        NullCheck.notNull(objects, "Objects");
      this.selectionListener =
        Objects.requireNonNull(listener, "listener");
    }

    private static final class ViewHolder {
      private TextView text;
      private ImageView icon;
      private ImageView edit;
      private ImageView delete;

      ViewHolder() {

      }
    }

    @Override
    public View getView(
      final int position,
      final View convert_view,
      final ViewGroup parent_group) {

      View row_view = convert_view;
      if (row_view == null) {
        final LayoutInflater inflater =
          (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        row_view = inflater.inflate(R.layout.profiles_list_item, null);

        final ViewHolder view_holder = new ViewHolder();
        view_holder.text = row_view.findViewById(R.id.profileItemDisplayName);
        view_holder.icon = row_view.findViewById(R.id.profileItemIcon);
        view_holder.edit = row_view.findViewById(R.id.profileItemEdit);
        view_holder.delete = row_view.findViewById(R.id.profileItemDelete);
        row_view.setTag(view_holder);
      }

      final ViewHolder holder = (ViewHolder) row_view.getTag();
      final ProfileReadableType profile = this.list_items.get(position);

      holder.icon.setOnClickListener(v -> this.selectionListener.onProfileSelected(profile));
      holder.text.setOnClickListener(v -> this.selectionListener.onProfileSelected(profile));
      holder.edit.setOnClickListener(v -> this.selectionListener.onProfileWantEdit(profile));
      holder.delete.setOnClickListener(v -> this.selectionListener.onProfileWantDelete(profile));

      holder.text.setText(profile.displayName());
      LOG.trace("getView: [{}] profile: {}", position, profile.displayName());
      return row_view;
    }
  }

  private void openCatalog() {
    final Intent i = new Intent(this, MainCatalogActivity.class);
    this.startActivity(i);
    this.finish();
  }

  private void openCreationDialog() {
    final Intent i = new Intent(this, ProfileCreationActivity.class);
    this.startActivity(i);
  }

  private void openEditDialog(final ProfileReadableType profile) {
    ProfileCreationActivity.startActivity(this, profile.id());
  }
}
