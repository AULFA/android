package org.nypl.simplified.tests.http;

import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.ProcedureType;

import org.nypl.simplified.http.core.HTTPAuthType;
import org.nypl.simplified.http.core.HTTPResultType;
import org.nypl.simplified.http.core.HTTPType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * A trivial implementation of the {@link HTTPType} that simply returns preconfigured responses
 * when requests are made of given URIs.
 */

public final class MockingHTTP implements HTTPType {

  private static final Logger LOG = LoggerFactory.getLogger(MockingHTTP.class);
  private final HashMap<URI, List<Response>> responses;

  public MockingHTTP() {
    this.responses = new HashMap<>();
  }

  private static final class Response {
    private HTTPResultType<InputStream> result;
    private ProcedureType<byte[]> check;

    public Response(
      final HTTPResultType<InputStream> result,
      final ProcedureType<byte[]> check) {
      this.result = Objects.requireNonNull(result, "result");
      this.check = Objects.requireNonNull(check, "check");
    }
  }

  /**
   * Set that the next request made for {@code uri} will receive {@code result}.
   *
   * @param uri    The request
   * @param result The result
   */

  public void addResponse(
    final URI uri,
    final HTTPResultType<InputStream> result) {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(result, "result");

    addResponse(uri, result, x -> {
    });
  }

  /**
   * Set that the next request made for {@code uri} will receive {@code result}.
   *
   * @param uri      The request
   * @param result   The result
   * @param received A function that will receive the data sent by the client
   */

  public void addResponse(
    final URI uri,
    final HTTPResultType<InputStream> result,
    final ProcedureType<byte[]> received) {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(result, "result");
    Objects.requireNonNull(received, "received");

    synchronized (this.responses) {
      final List<Response> xs;
      if (this.responses.containsKey(uri)) {
        xs = this.responses.get(uri);
      } else {
        xs = new ArrayList<>();
      }
      xs.add(new Response(result, received));
      this.responses.put(uri, xs);
    }
  }

  /**
   * Set that the next request made for {@code uri} will receive {@code result}.
   *
   * @param uri    The request
   * @param result The result
   */

  public void addResponse(
    final String uri,
    final HTTPResultType<InputStream> result) {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(result, "result");
    addResponse(URI.create(uri), result);
  }

  /**
   * Set that the next request made for {@code uri} will receive {@code result}.
   *
   * @param uri      The request
   * @param result   The result
   * @param received A function that will receive the data sent by the client
   */

  public void addResponse(
    final String uri,
    final HTTPResultType<InputStream> result,
    ProcedureType<byte[]> received) {
    Objects.requireNonNull(uri, "uri");
    Objects.requireNonNull(result, "result");
    Objects.requireNonNull(received, "received");
    addResponse(URI.create(uri), result);
  }

  @Override
  public HTTPResultType<InputStream> get(
    final OptionType<HTTPAuthType> auth,
    final URI uri,
    final long offset) {

    LOG.debug("get: {} {} {}", auth, uri, offset);
    return response(uri, new byte[0]);
  }

  private HTTPResultType<InputStream> response(final URI uri, final byte[] sent) {
    synchronized (this.responses) {
      final List<Response> xs = this.responses.get(uri);
      if (xs != null && !xs.isEmpty()) {
        final Response response = xs.remove(0);
        response.check.call(sent);
        return response.result;
      }
      throw new IllegalStateException("No responses available for " + uri);
    }
  }

  @Override
  public HTTPResultType<InputStream> put(
    final OptionType<HTTPAuthType> auth,
    final URI uri) {

    LOG.debug("put: {} {}", auth, uri);
    return response(uri, new byte[0]);
  }

  @Override
  public HTTPResultType<InputStream> post(
    final OptionType<HTTPAuthType> auth,
    final URI uri,
    final byte[] data,
    final String content_type) {

    LOG.debug("post: {} {} {} {}", auth, uri, data, content_type);
    return response(uri, data);
  }

  @Override
  public HTTPResultType<InputStream> delete(
    final OptionType<HTTPAuthType> auth,
    final URI uri,
    final String content_type) {

    LOG.debug("delete: {} {} {}", auth, uri, content_type);
    return response(uri, new byte[0]);
  }

  @Override
  public HTTPResultType<InputStream> head(
    final OptionType<HTTPAuthType> auth,
    final URI uri) {

    LOG.debug("head: {} {}", auth, uri);
    return response(uri, new byte[0]);
  }
}
