package org.nypl.simplified.app

import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.Some
import org.joda.time.LocalDate
import org.nypl.simplified.books.profiles.ProfileReadableType

@Deprecated("Inserted as a temporary strategy to log analytics whilst this build still exists!")
object AnalyticsUtilities {

  private fun orEmpty(text: String?): String {
    return text ?: ""
  }

  private fun orEmptyOptional(
    text: OptionType<String>
  ): String {
    return if (text is Some<String>) {
      text.get()
    } else {
      ""
    }
  }

  private fun orEmptyOptionalDate(
    text: OptionType<LocalDate>
  ): String {
    return if (text is Some<LocalDate>) {
      text.get().toString()
    } else {
      ""
    }
  }

  private fun scrubCommas(text: String): String {
    return text.replace(",", "")
  }

  fun profileCreated(
    profile: ProfileReadableType
  ): String {
    val preferences = profile.preferences()
    val eventBuilder = StringBuilder(128)
    eventBuilder.append("profile_created,")
    eventBuilder.append(profile.id().id())
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(profile.displayName()))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.gender())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptionalDate(preferences.dateOfBirth())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.role())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.school())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.grade())))
    return eventBuilder.toString()
  }

  fun profileDeleted(
    profile: ProfileReadableType
  ): String {
    val preferences = profile.preferences()
    val eventBuilder = StringBuilder(128)
    eventBuilder.append("profile_deleted,")
    eventBuilder.append(profile.id().id())
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(profile.displayName()))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.gender())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptionalDate(preferences.dateOfBirth())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.role())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.school())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.grade())))
    return eventBuilder.toString()
  }

  fun profileChanged(
    profile: ProfileReadableType
  ): String {
    val preferences = profile.preferences()
    val eventBuilder = StringBuilder(128)
    eventBuilder.append("profile_modified,")
    eventBuilder.append(profile.id().id())
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(profile.displayName()))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.gender())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptionalDate(preferences.dateOfBirth())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.role())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.school())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.grade())))
    return eventBuilder.toString()
  }

  fun profileSelected(
    profile: ProfileReadableType
  ): String {
    val preferences = profile.preferences()
    val eventBuilder = StringBuilder(128)
    eventBuilder.append("profile_selected,")
    eventBuilder.append(profile.id().id())
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(profile.displayName()))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.gender())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptionalDate(preferences.dateOfBirth())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.role())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.school())))
    eventBuilder.append(',')
    eventBuilder.append(scrubCommas(orEmptyOptional(preferences.grade())))
    return eventBuilder.toString()
  }
}