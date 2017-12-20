package org.nypl.simplified.books.accounts;

import com.google.auto.value.AutoValue;

/**
 * <p>A description of the details of authentication.</p>
 */

@AutoValue
public abstract class AccountProviderAuthenticationDescription {

  /**
   * @return The required length of passcodes, or {@code 0} if no specific length is required
   */

  public abstract int passCodeLength();

  /**
   * @return {@code true} iff passcodes may contain letters
   */

  public abstract boolean passCodeMayContainLetters();

  /**
   * The type of mutable builders for account providers.
   */

  @AutoValue.Builder
  public abstract static class Builder {

    /**
     * @param length The pass code length
     * @return The current builder
     * @see #passCodeLength()
     */

    public abstract Builder setPassCodeLength(int length);

    /**
     * @param letters {@code  true} iff the pass code may contain letters
     * @return The current builder
     * @see #passCodeMayContainLetters()
     */

    public abstract Builder setPassCodeMayContainLetters(boolean letters);

    /**
     * @return The constructed account provider
     */

    public abstract AccountProviderAuthenticationDescription build();
  }

  /**
   * @return A new account provider builder
   */

  public static Builder builder() {
    return new AutoValue_AccountProviderAuthenticationDescription.Builder();
  }
}