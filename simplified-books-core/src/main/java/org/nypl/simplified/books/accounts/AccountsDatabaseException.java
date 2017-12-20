package org.nypl.simplified.books.accounts;

import com.io7m.jnull.NullCheck;

import java.util.List;

/**
 * An exception that indicates that an operation on the accounts database failed.
 */

public final class AccountsDatabaseException extends Exception {

  private final List<Exception> causes;

  /**
   * Construct an exception.
   *
   * @param message The exception message
   * @param causes  The list of causes
   */

  public AccountsDatabaseException(
      final String message,
      final List<Exception> causes) {
    super(NullCheck.notNull(message, "Message"));
    this.causes = NullCheck.notNull(causes, "Causes");
  }

  /**
   * @return The list of exceptions raised that caused this exception
   */

  public List<Exception> causes() {
    return this.causes;
  }
}
