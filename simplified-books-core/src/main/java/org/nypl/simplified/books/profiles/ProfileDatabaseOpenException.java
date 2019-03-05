package org.nypl.simplified.books.profiles;

import java.util.List;

/**
 * An exception raised when opening a database.
 */

public final class ProfileDatabaseOpenException extends ProfileDatabaseException {

  /**
   * Construct an exception.
   *
   * @param message The exception message
   * @param causes  The list of causes
   */

  public ProfileDatabaseOpenException(
    final String message,
    final List<Exception> causes) {
    super(makeMessage(message, causes), causes);
    for (final Exception cause : causes) {
      this.addSuppressed(cause);
    }
  }

  private static String makeMessage(
    final String message,
    final List<Exception> causes) {
    StringBuilder sb = new StringBuilder(256);
    sb.append(message);
    sb.append("\n");

    for (final Exception cause : causes) {
      recursivelyAppend(sb, cause, 0);
    }
    return sb.toString();
  }

  private static void recursivelyAppend(StringBuilder sb, Throwable cause, int depth) {
    if (cause == null) {
      return;
    }

    for (int index = 0; index < depth; ++index) {
      sb.append(" ");
    }

    sb.append("  Cause: ");
    sb.append(cause.getClass().getName());
    sb.append(": ");
    sb.append(cause.getMessage());
    sb.append("\n");
    recursivelyAppend(sb, cause.getCause(), depth + 1);
  }
}
