package org.nypl.simplified.app.utilities;

/**
 * Created by Skullbonez on 1/7/2018.
 *
 * *** These events and parameters need to match the Firebase server configuration!! ***
 */

public final class AnalyticEvents {
  public static class Event {
    public static String BOOK_OPENED = "BOOK_OPENED";
    public static String BOOK_DOWNLOADED = "BOOK_DOWNLOADED";
    public static String CATALOG_SEARCHED = "CATALOG_SEARCHED";
  }

  public static class StringParameter {
    public static String DEVICE_ID = "DEVICE_ID";
    public static String BOOK_ID = "BOOK_ID";
    public static String SEARCH_QUERY = "SEARCH_QUERY";
  }

  public static class NumericParameter {
    public static String CURRENT_PAGE_NUMBER = "CURRENT_PAGE_NUMBER";
  }
}
