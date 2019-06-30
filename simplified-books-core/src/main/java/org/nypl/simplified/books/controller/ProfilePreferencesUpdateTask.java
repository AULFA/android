package org.nypl.simplified.books.controller;

import com.google.common.base.Function;

import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileID;
import org.nypl.simplified.books.profiles.ProfileNonexistentException;
import org.nypl.simplified.books.profiles.ProfilePreferences;
import org.nypl.simplified.books.profiles.ProfilePreferencesChanged;
import org.nypl.simplified.books.profiles.ProfileType;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType;
import org.nypl.simplified.observable.ObservableType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Callable;

final class ProfilePreferencesUpdateTask implements Callable<ProfilePreferencesChanged> {

  private static final Logger LOG = LoggerFactory.getLogger(ProfilePreferencesUpdateTask.class);

  private final ProfilesDatabaseType profiles;
  private final ProfilesControllerType.PreferencesUpdateType preferences;
  private final ObservableType<ProfileEvent> events;
  private final ProfileID profileId;

  ProfilePreferencesUpdateTask(
    final ObservableType<ProfileEvent> events,
    final ProfileID profileId,
    final ProfilesDatabaseType profiles,
    final ProfilesControllerType.PreferencesUpdateType preferences) {

    this.events =
      Objects.requireNonNull(events, "Events");
    this.profileId =
      Objects.requireNonNull(profileId, "ProfileId");
    this.profiles =
      Objects.requireNonNull(profiles, "profiles");
    this.preferences =
      Objects.requireNonNull(preferences, "Preferences");
  }

  @Override
  public ProfilePreferencesChanged call()
    throws Exception {
    try {
      final ProfileType profile = this.profiles.profiles().get(this.profileId);
      if (profile == null) {
        throw new ProfileNonexistentException("No such profile: " + this.profileId.id());
      }

      final ProfilePreferences oldPreferences = profile.preferences();
      final ProfilePreferences newPreferences = this.preferences.update(oldPreferences);
      profile.preferencesUpdate(newPreferences);

      final ProfilePreferencesChanged.ProfilePreferencesChangeSucceeded event =
        new ProfilePreferencesChanged.ProfilePreferencesChangeSucceeded(
        this.profileId,
        false,
        !oldPreferences.readerBookmarks().equals(newPreferences.readerBookmarks()),
        !oldPreferences.readerPreferences().equals(newPreferences.readerPreferences()));

      this.events.send(event);
      return event;
    } catch (final IOException e) {
      LOG.error("could not update preferences: ", e);
      final ProfilePreferencesChanged.ProfilePreferencesChangeFailed event =
        new ProfilePreferencesChanged.ProfilePreferencesChangeFailed(this.profileId, e);
      this.events.send(event);
      return event;
    }
  }
}
