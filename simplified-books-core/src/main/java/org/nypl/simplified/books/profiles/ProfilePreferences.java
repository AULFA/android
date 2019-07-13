package org.nypl.simplified.books.profiles;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;

import org.joda.time.LocalDate;
import org.nypl.simplified.books.reader.ReaderBookmarks;
import org.nypl.simplified.books.reader.ReaderPreferences;

/**
 * A set of preferences for a profile.
 */

@AutoValue
public abstract class ProfilePreferences {

  ProfilePreferences() {

  }

  /**
   * @return The gender of the reader (if one has been explicitly specified)
   */

  public abstract OptionType<String> gender();

  /**
   * @return The role of the reader (if one has been explicitly specified)
   */

  public abstract OptionType<String> role();

  /**
   * @return The grade of the reader (if one has been explicitly specified)
   */

  public abstract OptionType<String> grade();
  
  /**
   * @return The school of the reader (if one has been explicitly specified)
   */

  public abstract OptionType<String> school();

  /**
   * @return The date of birth of the reader (if one has been explicitly specified)
   */

  public abstract OptionType<LocalDate> dateOfBirth();

  /**
   * @return The current value as a mutable builder
   */

  public abstract Builder toBuilder();

  /**
   * @return The reader-specific preferences
   */

  public abstract ReaderPreferences readerPreferences();

  /**
   * @return The reader bookmarks
   */

  public abstract ReaderBookmarks readerBookmarks();

  /**
   * Update bookmarks.
   *
   * @param bookmarks The new bookmarks
   * @return A new set of preferences based on the current preferences but with the given bookmarks
   */

  public final ProfilePreferences withReaderBookmarks(final ReaderBookmarks bookmarks) {
    return this.toBuilder().setReaderBookmarks(bookmarks).build();
  }

  /**
   * A mutable builder for the type.
   */

  @AutoValue.Builder
  public abstract static class Builder {

    Builder() {

    }

    /**
     * @param bookmarks The reader bookmarks
     * @return The current builder
     * @see #readerBookmarks()
     */

    public abstract Builder setReaderBookmarks(
      ReaderBookmarks bookmarks);

    /**
     * @param prefs The reader preferences
     * @return The current builder
     * @see #readerPreferences()
     */

    public abstract Builder setReaderPreferences(
      ReaderPreferences prefs);

    /**
     * @param gender The gender
     * @return The current builder
     * @see #gender()
     */

    public abstract Builder setGender(
      OptionType<String> gender);

    /**
     * @param gender The gender
     * @return The current builder
     * @see #gender()
     */

    public final Builder setGender(final String gender) {
      return setGender(Option.some(gender));
    }

    /**
     * @param role The role
     * @return The current builder
     * @see #role()
     */

    public abstract Builder setRole(
      OptionType<String> role);
    
    /**
     * @param role The role
     * @return The current builder
     * @see #role()
     */

    public final Builder setRole(final String role) {
      return setRole(Option.some(role));
    }

    /**
     * @param grade The grade
     * @return The current builder
     * @see #grade()
     */

    public abstract Builder setGrade(
      OptionType<String> grade);

    /**
     * @param grade The grade
     * @return The current builder
     * @see #grade()
     */

    public final Builder setGrade(final String grade) {
      return setGrade(Option.some(grade));
    }
    
    /**
     * @param school The school
     * @return The current builder
     * @see #school()
     */

    public abstract Builder setSchool(
      OptionType<String> school);

    /**
     * @param school The school
     * @return The current builder
     * @see #school()
     */

    public final Builder setSchool(final String school) {
      return setSchool(Option.some(school));
    }
    
    /**
     * @param date The date
     * @return The current builder
     * @see #dateOfBirth()
     */

    public abstract Builder setDateOfBirth(
      OptionType<LocalDate> date);

    /**
     * @param date The date
     * @return The current builder
     * @see #dateOfBirth()
     */

    public final Builder setDateOfBirth(final LocalDate date) {
      return setDateOfBirth(Option.some(date));
    }

    /**
     * @return A profile description based on the given parameters
     */

    public abstract ProfilePreferences build();
  }

  /**
   * @return A new builder
   */

  public static ProfilePreferences.Builder builder() {
    return new AutoValue_ProfilePreferences.Builder()
      .setDateOfBirth(Option.none())
      .setGender(Option.none())
      .setGrade(Option.none())
      .setReaderBookmarks(ReaderBookmarks.create(ImmutableMap.of()))
      .setReaderPreferences(ReaderPreferences.builder().build())
      .setRole(Option.none())
      .setSchool(Option.none())
      ;
  }
}
