package org.nypl.simplified.tests.local.books.analytics;

import org.nypl.simplified.tests.books.analytics.AnalyticsLoggerContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AnalyticsLoggerTest extends AnalyticsLoggerContract {

  @Override
  protected Logger logger() {
    return LoggerFactory.getLogger(AnalyticsLoggerTest.class);
  }
}
