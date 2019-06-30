package org.nypl.simplified.books.profiles

/**
 * The type of events raised on profile creation.
 */

sealed class ProfileDeletionEvent : ProfileEvent() {

  /**
   * The ID of the profile.
   */

  abstract val profileID: ProfileID

  /**
   * A profile was deleted successfully.
   */

  data class ProfileDeletionSucceeded(
    override val profileID: ProfileID)
    : ProfileDeletionEvent()

  /**
   * A profile could not be deleted.
   */

  data class ProfileDeletionFailed(
    override val profileID: ProfileID,

    /**
     * The exception raised.
     */

    val exception: Exception)
    : ProfileDeletionEvent()

}
