package org.nypl.simplified.tests.books.analytics;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.nypl.simplified.books.analytics.AnalyticsLogger;
import org.nypl.simplified.http.core.HTTP;
import org.nypl.simplified.http.core.HTTPResultOK;
import org.nypl.simplified.http.core.HTTPType;
import org.nypl.simplified.tests.http.MockingHTTP;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

public abstract class AnalyticsLoggerContract {

  private ThreadFactory threads;
  private File directory;
  private Logger logger;

  protected abstract Logger logger();

  @Before
  public void testSetup() throws IOException {
    this.threads = runnable -> {
      Thread thread = new Thread(runnable);
      thread.setName("analytics");
      return thread;
    };

    this.logger = this.logger();
    this.directory = File.createTempFile("analytics", "dir");
    this.directory.delete();
    this.directory.mkdirs();

    this.logger.debug("directory: {}", this.directory);
  }

  @Test
  public void testLogAndWrite() throws Exception {
    final MockingHTTP http = new MockingHTTP();

    final AtomicBoolean checkedOK =
      new AtomicBoolean(false);

    http.addResponse(
      URI.create(AnalyticsLogger.ANALYTICS_SERVER_URI),
      new HTTPResultOK<>(
        "OK",
        200, new
        ByteArrayInputStream(new byte[0]),
        0L,
        new HashMap<>(),
        0L),
       data -> this.checkData(checkedOK, data));

    final AnalyticsLogger logger =
      AnalyticsLogger.create(http, this.threads, this.directory);

    logger.logToAnalytics("nothing");

    final ListenableFuture<?> future =
      logger.writeToAnalyticsServer("28136a26-1e3a-4a9b-9272-f6a6607357ba");

    future.get(10L, TimeUnit.SECONDS);
    Assert.assertTrue("Decompressed data", checkedOK.get());
  }

  @Test
  public void testLogLots() throws Exception {
    final MockingHTTP http = new MockingHTTP();

    final AtomicBoolean checkedOK =
      new AtomicBoolean(false);

    http.addResponse(
      URI.create(AnalyticsLogger.ANALYTICS_SERVER_URI),
      new HTTPResultOK<>(
        "OK",
        200, new
        ByteArrayInputStream(new byte[0]),
        0L,
        new HashMap<>(),
        0L),
      data -> this.checkData(checkedOK, data));

    final AnalyticsLogger logger =
      AnalyticsLogger.create(http, this.threads, this.directory);

    /*
     * Write 20mb of data to the log.
     */

    final StringBuilder buffer = new StringBuilder(1024);
    for (int index = 0; index < 1_000; ++index) {
      buffer.append('a');
    }
    final String line = buffer.toString();
    for (int index = 0; index < 20_000; ++index) {
      logger.logToAnalytics(line);
    }

    final ListenableFuture<?> future =
      logger.writeToAnalyticsServer("28136a26-1e3a-4a9b-9272-f6a6607357ba");

    future.get(10L, TimeUnit.SECONDS);
    Assert.assertTrue("Decompressed data", checkedOK.get());
  }

  /**
   * Check that data is gzipped.
   *
   * @param bytes The input bytes.
   */

  private void checkData(final AtomicBoolean checked, byte[] bytes) {
    long total = 0L;
    try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
      try (GZIPInputStream gzip = new GZIPInputStream(input)) {
        byte[] buffer = new byte[4096];
        while (true) {
          int r = gzip.read(buffer);
          if (r == -1) {
            break;
          }
          total += r;
        }
        checked.set(true);
        this.logger.debug("read {} bytes", total);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @Ignore
  public void testOneLine() throws Exception {
    final HTTPType http = HTTP.newHTTP();

    final AnalyticsLogger logger =
      AnalyticsLogger.create(http, this.threads, this.directory);

    logger.logToAnalytics("test");

    final ListenableFuture<?> future =
      logger.writeToAnalyticsServer("28136a26-1e3a-4a9b-9272-f6a6607357ba");

    future.get(10L, TimeUnit.SECONDS);
  }
}
