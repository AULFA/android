package org.nypl.simplified.app.profiles;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnreachableCodeException;

import org.joda.time.LocalDate;
import org.nypl.simplified.app.R;
import org.nypl.simplified.app.Simplified;
import org.nypl.simplified.app.SimplifiedActivity;
import org.nypl.simplified.app.utilities.ErrorDialogUtilities;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.accounts.AccountProviderCollection;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileCreationEvent;
import org.nypl.simplified.books.profiles.ProfileCreationEvent.ProfileCreationFailed;
import org.nypl.simplified.books.profiles.ProfileCreationEvent.ProfileCreationSucceeded;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileID;
import org.nypl.simplified.books.profiles.ProfilePreferences;
import org.nypl.simplified.books.profiles.ProfilePreferencesChanged;
import org.nypl.simplified.books.profiles.ProfileReadableType;
import org.nypl.simplified.datepicker.DatePicker;
import org.slf4j.Logger;

import java.util.Objects;

import static org.nypl.simplified.app.Simplified.WantActionBar.WANT_NO_ACTION_BAR;
import static org.nypl.simplified.books.profiles.ProfileCreationEvent.ProfileCreationFailed.ErrorCode.ERROR_GENERAL;
import static org.nypl.simplified.books.profiles.ProfilePreferencesChanged.ProfilePreferencesChangeFailed;
import static org.nypl.simplified.books.profiles.ProfilePreferencesChanged.ProfilePreferencesChangeSucceeded;

/**
 * An activity that allows for the creation or modification of profiles.
 */

public final class ProfileCreationActivity extends SimplifiedActivity implements TextWatcher {

  private static final Logger LOG = LogUtilities.getLog(ProfileCreationActivity.class);

  private static final String PROFILE_ID_KEY =
    "org.nypl.simplified.app.profiles.ProfileCreationActivity.profileId";

  private Button button;
  private DatePicker date;
  private EditText name;
  private RadioGroup genderRadioGroup;
  private RadioButton nonBinaryRadioButton;
  private EditText nonBinaryEditText;
  private @Nullable
  ProfileReadableType profile;

  /**
   * Start a new activity for the given profile.
   *
   * @param from      The parent activity
   * @param profileID The profile ID
   */

  public static void startActivity(
    final Activity from,
    final ProfileID profileID) {

    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(profileID, "profileID");

    final Bundle b = new Bundle();
    b.putInt(ProfileCreationActivity.PROFILE_ID_KEY, profileID.id());
    final Intent i = new Intent(from, ProfileCreationActivity.class);
    i.putExtras(b);
    from.startActivity(i);
  }

  public ProfileCreationActivity() {

  }

  @Override
  protected void onCreate(final Bundle state) {
    final Intent i = Objects.requireNonNull(this.getIntent());
    final Bundle extras = i.getExtras();
    if (extras != null) {
      if (!openProfileOrQuit(extras)) {
        return;
      }
    }

    // This activity is too tall for many phones in landscape mode.
    this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

    this.setTheme(Simplified.getCurrentTheme(WANT_NO_ACTION_BAR));
    super.onCreate(state);
    this.setContentView(R.layout.profiles_creation);

    this.button = Objects.requireNonNull(this.findViewById(R.id.profileCreationCreate));
    this.button.setEnabled(false);
    this.button.setOnClickListener(view -> {
      view.setEnabled(false);
      createOrModifyProfile();
    });

    this.date = Objects.requireNonNull(this.findViewById(R.id.profileCreationDateSelection));
    this.name = Objects.requireNonNull(this.findViewById(R.id.profileCreationEditName));
    this.genderRadioGroup = Objects.requireNonNull(this.findViewById(R.id.profileGenderRadioGroup));
    this.nonBinaryRadioButton = Objects.requireNonNull(
      this.findViewById(R.id.profileGenderNonBinaryRadioButton));
    this.nonBinaryEditText =
      Objects.requireNonNull(this.findViewById(R.id.profileGenderNonBinaryEditText));

    this.name.addTextChangedListener(this);
    this.nonBinaryEditText.addTextChangedListener(this);
    this.nonBinaryEditText.setOnFocusChangeListener((View view, boolean hasFocus) -> {
      if (hasFocus) {
        this.nonBinaryRadioButton.setChecked(true);
      }
    });
    this.genderRadioGroup.setOnCheckedChangeListener((group, id) -> {
      if (id == R.id.profileGenderNonBinaryRadioButton) {
        this.nonBinaryEditText.requestFocus();
      } else {
        this.nonBinaryEditText.clearFocus();
      }
      this.updateButtonEnabled();
    });

    if (this.profile != null) {
      this.name.setText(this.profile.displayName());

      final ProfilePreferences preferences = this.profile.preferences();
      final OptionType<LocalDate> dateOfBirthOpt = preferences.dateOfBirth();
      if (dateOfBirthOpt.isSome()) {
        final LocalDate dateOfBirth = ((Some<LocalDate>) dateOfBirthOpt).get();
        this.date.setDate(dateOfBirth);
      }

      final OptionType<String> genderOpt = preferences.gender();
      if (genderOpt.isSome()) {
        final String gender = ((Some<String>) genderOpt).get();
        switch (gender) {
          case "male":
            this.genderRadioGroup.check(R.id.profileGenderMaleRadioButton);
            break;
          case "female":
            this.genderRadioGroup.check(R.id.profileGenderFemaleRadioButton);
            break;
          default:
            this.genderRadioGroup.check(R.id.profileGenderNonBinaryRadioButton);
            this.nonBinaryEditText.setText(gender);
            break;
        }
      }

      this.button.setText(R.string.profiles_modify);
    }
  }

  private boolean openProfileOrQuit(Bundle extras) {
    final ProfileID id = ProfileID.create(extras.getInt(ProfileCreationActivity.PROFILE_ID_KEY));
    final ProfileReadableType profile =
      Simplified.getProfilesController()
        .profiles()
        .get(id);
    if (profile == null) {
      this.openSelectionActivity();
      return false;
    }
    this.profile = profile;
    return true;
  }

  private Unit onProfileCreationFailed(final ProfileCreationFailed e) {
    LOG.debug("onProfileCreationFailed: {}", e);

    ErrorDialogUtilities.showErrorWithRunnable(
      this,
      LOG,
      this.getResources().getString(messageForErrorCode(e.errorCode())),
      null,
      () -> this.button.setEnabled(true));

    return Unit.unit();
  }

  private int messageForErrorCode(final ProfileCreationFailed.ErrorCode code) {
    switch (code) {
      case ERROR_DISPLAY_NAME_ALREADY_USED:
        return R.string.profiles_creation_error_name_already_used;
      case ERROR_GENERAL:
        return R.string.profiles_creation_error_general;
    }
    throw new UnreachableCodeException();
  }

  private Unit onProfileCreationSucceeded(final ProfileCreationSucceeded e) {
    LOG.debug("onProfileCreationSucceeded: {}", e);
    UIThread.runOnUIThread(this::openSelectionActivity);
    return Unit.unit();
  }

  private Unit onProfileEvent(final ProfileEvent event) {
    LOG.debug("onProfileEvent: {}", event);
    if (event instanceof ProfileCreationEvent) {
      final ProfileCreationEvent event_create = (ProfileCreationEvent) event;
      return event_create.matchCreation(
        this::onProfileCreationSucceeded,
        this::onProfileCreationFailed);
    } else if (event instanceof ProfilePreferencesChanged) {
      if (this.profile != null) {
        return onProfileEventPreferencesChanged((ProfilePreferencesChanged) event);
      }
    }
    return Unit.unit();
  }

  private Unit onProfileEventPreferencesChanged(ProfilePreferencesChanged changed) {
    if (changed.getProfileID() == this.profile.id()) {
      if (changed instanceof ProfilePreferencesChangeSucceeded) {
        return onProfileEventPreferencesChangeSucceeded((ProfilePreferencesChangeSucceeded) changed);
      } else if (changed instanceof ProfilePreferencesChangeFailed) {
        return onProfileEventPreferencesChangeFailed((ProfilePreferencesChangeFailed) changed);
      }
      throw new UnreachableCodeException();
    }
    return Unit.unit();
  }

  private Unit onProfileEventPreferencesChangeFailed(
    final ProfilePreferencesChangeFailed event) {
    return Unit.unit();
  }

  private Unit onProfileEventPreferencesChangeSucceeded(
    final ProfilePreferencesChangeSucceeded event) {
    this.openSelectionActivity();
    return Unit.unit();
  }

  private void openSelectionActivity() {
    final Intent i = new Intent(this, ProfileSelectionActivity.class);
    this.startActivity(i);
    this.finish();
  }

  private void createOrModifyProfile() {
    final String name_text = name.getText().toString().trim();
    final String gender_text;
    if (this.genderRadioGroup.getCheckedRadioButtonId() == R.id.profileGenderFemaleRadioButton) {
      gender_text = "female";
    } else if (this.genderRadioGroup.getCheckedRadioButtonId() == R.id.profileGenderMaleRadioButton) {
      gender_text = "male";
    } else if (this.genderRadioGroup.getCheckedRadioButtonId() == R.id.profileGenderNonBinaryRadioButton) {
      gender_text = this.nonBinaryEditText.getText().toString().toLowerCase().trim();
    } else {
      throw new UnreachableCodeException();
    }
    final LocalDate date_value = this.date.getDate();
    LOG.debug("name: {}", name_text);
    LOG.debug("gender: {}", gender_text);
    LOG.debug("date: {}", date_value);

    final AccountProviderCollection providers = Simplified.getAccountProviders();
    final ProfilesControllerType profiles = Simplified.getProfilesController();
    final ListeningExecutorService exec = Simplified.getBackgroundTaskExecutor();

    if (this.profile == null) {
      final ListenableFuture<ProfileCreationEvent> task =
        profiles.profileCreate(
          providers.providerDefault(),
          name_text,
          gender_text,
          date_value);

      FluentFuture.from(task)
        .catching(Exception.class, e -> ProfileCreationFailed.of(name_text, ERROR_GENERAL, Option.some(e)), exec)
        .transform(this::onProfileEvent, exec);
      return;
    }

    final ListenableFuture<ProfilePreferencesChanged> taskUpdatePreferences =
      profiles.profilePreferencesUpdateFor(
        this.profile.id(),
        preferences -> preferences.toBuilder()
          .setDateOfBirth(date_value)
          .setGender(gender_text)
          .build());

    final ListenableFuture<ProfilePreferencesChanged> taskUpdateName =
      profiles.profileDisplayNameUpdateFor(
        this.profile.id(),
        name_text);

    final FluentFuture<Unit> task =
      FluentFuture.from(taskUpdatePreferences)
        .transformAsync(input -> taskUpdateName, exec)
        .catching(Exception.class, e -> new ProfilePreferencesChangeFailed(this.profile.id(), e), exec)
        .transform(this::onProfileEvent, exec);
  }

  private void updateButtonEnabled() {
    final boolean isNameEmpty = this.name.getText().toString().trim().isEmpty();
    final boolean isNonBinaryEmpty = this.nonBinaryEditText.getText().toString().trim().isEmpty();
    final boolean isAnyRadioButtonChecked = this.genderRadioGroup.getCheckedRadioButtonId() != -1;
    final boolean isNonBinaryRatioButtonChecked = this.nonBinaryRadioButton.isChecked();

    if (isNonBinaryRatioButtonChecked) {
      this.button.setEnabled(!isNameEmpty && !isNonBinaryEmpty);
    } else if (isAnyRadioButtonChecked) {
      this.button.setEnabled(!isNameEmpty);
    } else {
      this.button.setEnabled(false);
    }
  }

  @Override
  public void beforeTextChanged(
    final CharSequence text,
    final int i,
    final int i1,
    final int i2) {
  }

  @Override
  public void onTextChanged(
    final CharSequence text,
    final int i,
    final int i1,
    final int i2) {
    this.updateButtonEnabled();
  }

  @Override
  public void afterTextChanged(final Editable editable) {

  }
}
