package org.nypl.simplified.books.analytics;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Unit;

import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.http.core.HTTPAuthBasic;
import org.nypl.simplified.http.core.HTTPAuthType;
import org.nypl.simplified.http.core.HTTPResultError;
import org.nypl.simplified.http.core.HTTPResultException;
import org.nypl.simplified.http.core.HTTPResultMatcherType;
import org.nypl.simplified.http.core.HTTPResultOKType;
import org.nypl.simplified.http.core.HTTPResultType;
import org.nypl.simplified.http.core.HTTPType;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

/**
 * Created by Skullbonez on 3/11/2018.
 * <p>
 * This logger is to be AS FAILSAFE AS POSSIBLE.
 * Silent failures are allowed here.  We want this
 * to be a "best effort" logger - it is not to
 * crash the app!
 */

public final class AnalyticsLogger {

  private static final Logger LOG = LogUtilities.getLog(AnalyticsLogger.class);

  public static final String ANALYTICS_SERVER_URI =
    "http://ec2-18-217-127-216.us-east-2.compute.amazonaws.com:8080/upload.log";

  private static final String ANALYTICS_SERVER_TOKEN =
    ".S23gLhfW/n:#CPD";
  private static final String LOG_FILE_NAME =
    "analytics_log.txt";
  private static final int LOG_FILE_SIZE_LIMIT =
    1024 * 1024 * 10;
  private static final int LOG_FILE_PUSH_LIMIT =
    1024 * 2;

  private final HTTPType http;
  private final ListeningExecutorService executor;
  private final File log_file_name;
  private BufferedWriter analytics_output = null;
  private final File directory_analytics;
  private boolean log_size_limit_reached = false;
  private AtomicBoolean is_logging_paused = new AtomicBoolean(false);

  private AnalyticsLogger(
    final HTTPType in_http,
    final ListeningExecutorService in_executor,
    final File in_directory_analytics) {
    this.http =
      Objects.requireNonNull(in_http, "in_http");
    this.executor =
      Objects.requireNonNull(in_executor, "in_executor");
    this.directory_analytics =
      Objects.requireNonNull(in_directory_analytics, "analytics");
    this.log_file_name =
      new File(this.directory_analytics, LOG_FILE_NAME);

    init();
  }

  public static AnalyticsLogger create(
    final HTTPType in_http,
    final ThreadFactory in_thread_factory,
    final File directory_analytics) {

    Objects.requireNonNull(in_http, "http");
    Objects.requireNonNull(in_thread_factory, "thread_factory");
    Objects.requireNonNull(directory_analytics, "directory_analytics");

    return new AnalyticsLogger(
      in_http,
      MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor(in_thread_factory)),
      directory_analytics);
  }

  private void init() {
    if (this.log_size_limit_reached) {
      // Don't bother trying to re-init if the log is full.
      return;
    }

    try {
      // Stop logging after 10MB (future releases will transmit then delete this file)
      if (this.log_file_name.length() < LOG_FILE_SIZE_LIMIT) {
        final FileWriter logWriter = new FileWriter(this.log_file_name, true);
        this.analytics_output = new BufferedWriter(logWriter);
      } else {
        this.log_size_limit_reached = true;
      }
    } catch (Exception e) {
      LOG.debug("Ignoring exception: init raised: ", e);
    }
  }

  private byte[] compressAndReadLogFile(File file) throws IOException {

    final byte[] buffer = new byte[4096];
    try (final ByteArrayOutputStream output = new ByteArrayOutputStream(LOG_FILE_SIZE_LIMIT / 10)) {
      try (final GZIPOutputStream gzip = new GZIPOutputStream(output)) {
        try (final InputStream input = new FileInputStream(this.log_file_name)) {
          while (true) {
            final int r = input.read(buffer);
            if (r == -1) {
              break;
            }
            gzip.write(buffer, 0, r);
          }
        }
        gzip.flush();
        gzip.finish();
      }

      return output.toByteArray();
    }
  }

  private void clearLogFile(final File file) throws IOException {
    try (final FileOutputStream ignored = new FileOutputStream(file)) {
      // Do nothing, just truncate the file
    }
  }

  public ListenableFuture<?> writeToAnalyticsServer(String deviceId) {
    return this.executor.submit(() -> {
      this.is_logging_paused.set(true);

      final OptionType<HTTPAuthType> auth =
        Option.some(HTTPAuthBasic.create(deviceId, ANALYTICS_SERVER_TOKEN));

      final HTTPResultType<InputStream> result;

      try {
        byte[] log_data = compressAndReadLogFile(this.log_file_name);
        LOG.debug("compressed data size: {}", log_data.length);

        if (log_data.length > 0) {
          result = this.http.post(auth, new URI(ANALYTICS_SERVER_URI), log_data, "application/json");
          result.matchResult(
            new HTTPResultMatcherType<InputStream, Unit, Exception>() {
              @Override
              public Unit onHTTPError(final HTTPResultError<InputStream> error) {
                return Unit.unit();
              }

              @Override
              public Unit onHTTPException(final HTTPResultException<InputStream> exception) {
                return Unit.unit();
              }

              @Override
              public Unit onHTTPOK(final HTTPResultOKType<InputStream> result) throws Exception {
                // Clear the log file.  Start logging from scratch.
                clearLogFile(log_file_name);
                return Unit.unit();
              }
            }
          );
        }
      } catch (Exception e) {
        LOG.error("error during analytics send: ", e);
      } finally {
        this.is_logging_paused.set(false);
      }
    });
  }

  public void attemptToPushAnalytics(String deviceId) {
    if (this.analytics_output == null) {
      init();
    }

    if (this.analytics_output != null && !this.is_logging_paused.get()) {
      try {
        File log_file = new File(this.directory_analytics, LOG_FILE_NAME);
        long len = log_file.length();
        // If over 50kb, push log file
        if (len > LOG_FILE_PUSH_LIMIT) {
          writeToAnalyticsServer(deviceId);
        }
      } catch (Exception e) {
        LOG.debug("Ignoring exception: attemptToPushAnalytics raised: ", e);
      }
    }
  }

  public void logToAnalytics(String message) {
    if (this.analytics_output == null) {
      init();
    }
    if (this.analytics_output != null && !this.is_logging_paused.get()) {
      try {
        String date_str = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,").format(new Date());
        this.analytics_output.write(date_str + message + "\n");
        this.analytics_output.flush();  // Make small synchronous additions for now
      } catch (Exception e) {
        LOG.debug("Ignoring exception: logToAnalytics raised: ", e);
      }
    }
  }
}
