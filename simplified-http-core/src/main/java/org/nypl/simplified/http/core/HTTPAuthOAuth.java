package org.nypl.simplified.http.core;

import com.google.auto.value.AutoValue;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * HTTP Basic Auth functions.
 */

@AutoValue
public abstract class HTTPAuthOAuth implements HTTPAuthType
{
  /**
   * Construct an OAuth value.
   *
   * @param token The Token
   */

  public static HTTPAuthOAuth create(HTTPOAuthToken token)
  {
    return new AutoValue_HTTPAuthOAuth(token);
  }

  /**
   * @return The OAuth token that will be used
   */

  public abstract HTTPOAuthToken token();

  @Override public void setConnectionParameters(
    final HttpURLConnection c)
    throws IOException
  {
    NullCheck.notNull(c);
    c.addRequestProperty("Authorization", "Bearer " + this.token().value());
  }

  @Override public <A, E extends Exception> A matchAuthType(
    final HTTPAuthMatcherType<A, E> m)
    throws E
  {
    return m.onAuthOAuth(this);
  }
}
