package org.nypl.simplified.app.profiles;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnreachableCodeException;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.jetbrains.annotations.NotNull;
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
import java.util.concurrent.ExecutionException;

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

  private Button finishButton;
  private DatePicker date;
  private EditText name;
  private RadioGroup genderRadioGroup;
  private RadioButton genderNonBinaryRadioButton;
  private EditText genderNonBinaryEditText;
  private @Nullable
  ProfileReadableType profile;
  private TextView title;
  private RadioGroup roleRadioGroup;
  private RadioButton roleOtherRadioButton;
  private EditText roleEditText;
  private RadioGroup pilotSchoolRadioGroup;
  private Spinner pilotSchoolSpinner;
  private ViewGroup pilotSchoolLayout;

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

    this.title = this.findViewById(R.id.profileCreateTitle);
    if (this.profile != null) {
      this.title.setText(R.string.profiles_modify_title);
    }

    this.finishButton = Objects.requireNonNull(this.findViewById(R.id.profileCreationCreate));
    this.finishButton.setEnabled(false);
    this.finishButton.setOnClickListener(view -> {
      view.setEnabled(false);
      createOrModifyProfile();
    });

    this.date =
      Objects.requireNonNull(this.findViewById(R.id.profileCreationDateSelection));
    this.name =
      Objects.requireNonNull(this.findViewById(R.id.profileCreationEditName));

    this.genderRadioGroup =
      Objects.requireNonNull(this.findViewById(R.id.profileGenderRadioGroup));
    this.genderNonBinaryRadioButton =
      Objects.requireNonNull(this.findViewById(R.id.profileGenderNonBinaryRadioButton));
    this.genderNonBinaryEditText =
      Objects.requireNonNull(this.findViewById(R.id.profileGenderNonBinaryEditText));

    this.roleRadioGroup =
      Objects.requireNonNull(this.findViewById(R.id.profileRoleRadioGroup));
    this.roleOtherRadioButton =
      Objects.requireNonNull(this.findViewById(R.id.profileRoleOtherRadioButton));
    this.roleEditText =
      Objects.requireNonNull(this.findViewById(R.id.profileRoleEditText));

    this.pilotSchoolLayout =
      Objects.requireNonNull(this.findViewById(R.id.profilePilotSchool));
    this.pilotSchoolRadioGroup =
      Objects.requireNonNull(this.findViewById(R.id.profilePilotSchoolRadioGroup));
    this.pilotSchoolSpinner =
      Objects.requireNonNull(this.findViewById(R.id.profilePilotSchoolYesSpinner));

    this.pilotSchoolRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
      if (checkedId == R.id.profilePilotSchoolYesRadioButton) {
        this.pilotSchoolSpinner.setEnabled(true);
      } else {
        this.pilotSchoolSpinner.setEnabled(false);
        this.pilotSchoolSpinner.setSelection(-1);
      }
    });
    this.pilotSchoolRadioGroup.check(R.id.profilePilotSchoolNoRadioButton);

    this.name.addTextChangedListener(this);

    this.genderNonBinaryEditText.addTextChangedListener(this);
    this.genderNonBinaryEditText.setOnFocusChangeListener((View view, boolean hasFocus) -> {
      if (hasFocus) {
        this.genderNonBinaryRadioButton.setChecked(true);
      }
    });
    this.genderRadioGroup.setOnCheckedChangeListener((group, id) -> {
      if (id == R.id.profileGenderNonBinaryRadioButton) {
        this.genderNonBinaryEditText.requestFocus();
      } else {
        this.genderNonBinaryEditText.clearFocus();
      }
      this.updateUIState();
    });

    this.roleEditText.addTextChangedListener(this);
    this.roleEditText.setOnFocusChangeListener((View view, boolean hasFocus) -> {
      if (hasFocus) {
        this.roleOtherRadioButton.setChecked(true);
      }
    });
    this.roleRadioGroup.setOnCheckedChangeListener((group, id) -> {
      if (id == R.id.profileRoleOtherRadioButton) {
        this.roleEditText.requestFocus();
      } else {
        this.roleEditText.clearFocus();
      }
      this.updateUIState();
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
            this.genderNonBinaryEditText.setText(gender);
            break;
        }
      }

      final OptionType<String> roleOpt = preferences.role();
      if (roleOpt.isSome()) {
        final String role = ((Some<String>) roleOpt).get();
        switch (role) {
          case "parent":
            this.roleRadioGroup.check(R.id.profileRoleParetRadioButton);
            break;
          case "student":
            this.roleRadioGroup.check(R.id.profileRoleStudentRadioButton);
            break;
          case "teacher":
            this.roleRadioGroup.check(R.id.profileRoleTeacherRadioButton);
            break;
          default:
            this.roleRadioGroup.check(R.id.profileRoleOtherRadioButton);
            this.roleEditText.setText(role);
            break;
        }
      }

      /*
       * Find the school in the pilot program spinner, if any.
       */

      final OptionType<String> schoolOpt = preferences.school();
      if (schoolOpt.isSome()) {
        final String school = ((Some<String>) schoolOpt).get();
        final SpinnerAdapter adapter = this.pilotSchoolSpinner.getAdapter();
        for (int index = 0; index < adapter.getCount(); ++index) {
          final String adapterItem = adapter.getItem(index).toString();
          if (adapterItem.equals(school)) {
            LOG.debug("set school spinner to index {}", index);
            this.pilotSchoolSpinner.setSelection(index, true);
            this.pilotSchoolRadioGroup.check(R.id.profilePilotSchoolYesRadioButton);
            break;
          }
        }
      }

      this.finishButton.setText(R.string.profiles_modify);
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
      () -> this.finishButton.setEnabled(true));

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

  private Unit onProfileEventPreferencesChangeFailed(final ProfilePreferencesChangeFailed event) {
    LOG.debug("onProfileEventPreferencesChangeFailed: {}", event);

    ErrorDialogUtilities.showErrorWithRunnable(
      this,
      LOG,
      event.getException().getMessage(),
      null,
      () -> this.finishButton.setEnabled(true));

    return Unit.unit();
  }

  private Unit onProfileEventPreferencesChangeSucceeded(
    final ProfilePreferencesChangeSucceeded event) {
    UIThread.runOnUIThread(this::openSelectionActivity);
    return Unit.unit();
  }

  private void openSelectionActivity() {
    final Intent i = new Intent(this, ProfileSelectionActivity.class);
    this.startActivity(i);
    this.finish();
  }

  private void createOrModifyProfile() {
    final String nameText = name.getText().toString().trim();

    final String genderText = getGenderText();
    final String roleText = getRoleText();
    final OptionType<String> school = getSchool();

    final LocalDate dateValue = this.date.getDate();
    LOG.debug("name:   {}", nameText);
    LOG.debug("gender: {}", genderText);
    LOG.debug("date:   {}", dateValue);
    LOG.debug("role:   {}", roleText);
    LOG.debug("school: {}", school);

    final AccountProviderCollection providers = Simplified.getAccountProviders();
    final ProfilesControllerType profiles = Simplified.getProfilesController();
    final ListeningExecutorService exec = Simplified.getBackgroundTaskExecutor();

    if (this.profile == null) {
      final FluentFuture<ProfileCreationEvent> taskCreateProfile =
        FluentFuture.from(profiles.profileCreate(
          providers.providerDefault(),
          nameText,
          genderText,
          dateValue));

      taskCreateProfile.addListener(() -> {
        try {
          final ProfileCreationEvent event = taskCreateProfile.get();
          if (event instanceof ProfileCreationEvent.ProfileCreationSucceeded) {
            final ListenableFuture<ProfilePreferencesChanged> taskUpdatePreferences =
              updatePreferences(
                genderText,
                roleText,
                school,
                dateValue,
                profiles,
                ((ProfileCreationSucceeded) event).id());
          }
        } catch (ExecutionException e) {
          LOG.error("execution: ", e);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }, exec);

      taskCreateProfile
        .catching(Exception.class, e -> ProfileCreationFailed.of(nameText, ERROR_GENERAL, Option.some(e)), exec)
        .transform(this::onProfileEvent, exec);
      return;
    }

    final ProfileID profileID =
      this.profile.id();

    final ListenableFuture<ProfilePreferencesChanged> taskUpdatePreferences =
      updatePreferences(genderText, roleText, school, dateValue, profiles, profileID);
    final ListenableFuture<ProfilePreferencesChanged> taskUpdateName =
      profiles.profileDisplayNameUpdateFor(profileID, nameText);

    final FluentFuture<Unit> task =
      FluentFuture.from(taskUpdatePreferences)
        .transformAsync(input -> taskUpdateName, exec)
        .catching(Exception.class, e -> new ProfilePreferencesChangeFailed(profileID, e), exec)
        .transform(this::onProfileEvent, exec);
  }

  private ListenableFuture<ProfilePreferencesChanged> updatePreferences(
    final String genderText,
    final String roleText,
    final OptionType<String> school,
    final LocalDate dateValue,
    final ProfilesControllerType profiles,
    final ProfileID profileID) {
    return profiles.profilePreferencesUpdateFor(
      profileID,
      preferences -> preferences.toBuilder()
        .setDateOfBirth(dateValue)
        .setGender(genderText)
        .setRole(roleText)
        .setSchool(school)
        .build());
  }

  private OptionType<String> getSchool() {
    if (this.roleRadioGroup.getCheckedRadioButtonId() == R.id.profileRoleStudentRadioButton) {
      if (this.pilotSchoolRadioGroup.getCheckedRadioButtonId() == R.id.profilePilotSchoolYesRadioButton) {
        return Option.of(this.pilotSchoolSpinner.getSelectedItem().toString());
      }
    }
    return Option.none();
  }

  private String getGenderText() {
    final String gender_text;
    if (this.genderRadioGroup.getCheckedRadioButtonId() == R.id.profileGenderFemaleRadioButton) {
      gender_text = "female";
    } else if (this.genderRadioGroup.getCheckedRadioButtonId() == R.id.profileGenderMaleRadioButton) {
      gender_text = "male";
    } else if (this.genderRadioGroup.getCheckedRadioButtonId() == R.id.profileGenderNonBinaryRadioButton) {
      gender_text = this.genderNonBinaryEditText.getText().toString().toLowerCase().trim();
    } else {
      throw new UnreachableCodeException();
    }
    return gender_text;
  }

  private String getRoleText() {
    final String role_text;
    if (this.roleRadioGroup.getCheckedRadioButtonId() == R.id.profileRoleParetRadioButton) {
      role_text = "parent";
    } else if (this.roleRadioGroup.getCheckedRadioButtonId() == R.id.profileRoleStudentRadioButton) {
      role_text = "student";
    } else if (this.roleRadioGroup.getCheckedRadioButtonId() == R.id.profileRoleTeacherRadioButton) {
      role_text = "teacher";
    } else if (this.roleRadioGroup.getCheckedRadioButtonId() == R.id.profileRoleOtherRadioButton) {
      role_text = this.roleEditText.getText().toString().toLowerCase().trim();
    } else {
      throw new UnreachableCodeException();
    }
    return role_text;
  }

  private void updateUIState() {
    if (this.roleRadioGroup.getCheckedRadioButtonId() == R.id.profileRoleStudentRadioButton) {
      this.pilotSchoolLayout.setVisibility(View.VISIBLE);
    } else {
      this.pilotSchoolLayout.setVisibility(View.GONE);
    }

    updateFinishButton();
  }

  private void updateFinishButton() {
    final boolean isNameOK =
      !this.name.getText().toString().trim().isEmpty();

    final boolean isGenderNonBinaryEmpty =
      this.genderNonBinaryEditText.getText().toString().trim().isEmpty();
    final boolean isGenderAnyRadioButtonChecked =
      this.genderRadioGroup.getCheckedRadioButtonId() != -1;
    final boolean isGenderNonBinaryRadioButtonChecked =
      this.genderNonBinaryRadioButton.isChecked();

    final boolean isGenderOk;
    if (isGenderAnyRadioButtonChecked) {
      if (isGenderNonBinaryRadioButtonChecked) {
        isGenderOk = !isGenderNonBinaryEmpty;
      } else {
        isGenderOk = true;
      }
    } else {
      isGenderOk = false;
    }

    final boolean isRoleOtherEmpty =
      this.roleEditText.getText().toString().trim().isEmpty();
    final boolean isRoleAnyRadioButtonChecked =
      this.roleRadioGroup.getCheckedRadioButtonId() != -1;
    final boolean isRoleOtherRadioButtonChecked =
      this.roleOtherRadioButton.isChecked();

    final boolean isRoleOk;
    if (isRoleAnyRadioButtonChecked) {
      if (isRoleOtherRadioButtonChecked) {
        isRoleOk = !isRoleOtherEmpty;
      } else {
        isRoleOk = true;
      }
    } else {
      isRoleOk = false;
    }

    this.finishButton.setEnabled(isNameOK && isRoleOk && isGenderOk);
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
    this.updateUIState();
  }

  @Override
  public void afterTextChanged(final Editable editable) {

  }
}
