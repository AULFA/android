package org.nypl.simplified.app.profiles;

import android.text.InputFilter;
import android.text.Spanned;

public final class ProfileRoleFilter implements InputFilter {

  public ProfileRoleFilter() {

  }

  @Override
  public CharSequence filter(
    final CharSequence source,
    final int start,
    final int end,
    final Spanned dest,
    final int dstart,
    final int dend) {

    final StringBuilder builder = new StringBuilder();
    for (int index = start; index < end; ++index) {
      final char c = source.charAt(index);
      if (Character.isLetterOrDigit(c)) {
        builder.append(c);
      }
    }

    final boolean allCharactersValid = (builder.length() == end - start);
    return allCharactersValid ? null : builder.toString();
  }
}
