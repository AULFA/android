package org.nypl.simplified.books.controller

import org.nypl.simplified.books.profiles.ProfileDeletionEvent
import org.nypl.simplified.books.profiles.ProfileDeletionEvent.ProfileDeletionFailed
import org.nypl.simplified.books.profiles.ProfileDeletionEvent.ProfileDeletionSucceeded
import org.nypl.simplified.books.profiles.ProfileEvent
import org.nypl.simplified.books.profiles.ProfileID
import org.nypl.simplified.books.profiles.ProfileNonexistentException
import org.nypl.simplified.books.profiles.ProfilesDatabaseType
import org.nypl.simplified.observable.ObservableType
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable

/**
 * A task that deletes a profile from the database.
 */

class ProfileDeletionTask(
  private val profiles: ProfilesDatabaseType,
  private val profileEvents: ObservableType<ProfileEvent>,
  private val profileID: ProfileID) : Callable<ProfileDeletionEvent> {

  private val logger =
    LoggerFactory.getLogger(ProfileDeletionTask::class.java)

  private fun execute(): ProfileDeletionEvent {
    return try {
      val profile =
        this.profiles.profiles().get(this.profileID)
          ?: throw ProfileNonexistentException("No such profile: $profileID")

      profile.delete()
      ProfileDeletionSucceeded(this.profileID)
    } catch (e: Exception) {
      this.logger.error("failed to delete profile: ", e)
      ProfileDeletionFailed(this.profileID, e)
    }
  }

  override fun call(): ProfileDeletionEvent {
    val event = this.execute()
    this.profileEvents.send(event)
    return event
  }
}
