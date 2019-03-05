package org.nypl.simplified.books.book_database;

import com.io7m.jnull.NullCheck;

import java.util.List;

/**
 * An exception that indicates that an operation on a book database failed.
 */

public final class BookDatabaseException extends Exception {

  private final List<Exception> causes;

  /**
   * Construct an exception.
   *
   * @param message The exception message
   * @param causes  The list of causes
   */

  public BookDatabaseException(
      final String message,
      final List<Exception> causes) {
    super(makeMessage(message, causes));
    for (final Exception cause : causes) {
      this.addSuppressed(cause);
    }
    this.causes = causes;
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

  /**
   * @return The list of exceptions raised that caused this exception
   */

  public List<Exception> causes() {
    return this.causes;
  }
}
