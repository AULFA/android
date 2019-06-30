package org.nypl.simplified.books.profiles

/**
 * The type of profile preferences events.
 */

sealed class ProfilePreferencesChanged : ProfileEvent() {

  /**
   * The ID of the profile to which the event refers
   */

  abstract val profileID: ProfileID

  /**
   * The profile preferences were changed successfully.
   */

  data class ProfilePreferencesChangeSucceeded(
    override val profileID: ProfileID,

    /**
     * `true` if the display name changed.
     */

    val changedDisplayName: Boolean,

    /**
     * `true` if the reader bookmarks changed.
     */

    val changedReaderBookmarks: Boolean,

    /**
     * `true` if the reader preferences changed.
     */

    val changedReaderPreferences: Boolean)
    : ProfilePreferencesChanged()

  /**
   * The profile preferences could not be changed.
   */

  data class ProfilePreferencesChangeFailed(
    override val profileID: ProfileID,

    /**
     * The exception raised.
     */

    val exception: Exception)
    : ProfilePreferencesChanged()
}
