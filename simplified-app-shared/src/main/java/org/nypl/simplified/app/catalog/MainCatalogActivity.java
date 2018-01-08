package org.nypl.simplified.app.catalog;

import com.io7m.junreachable.UnreachableCodeException;
import org.nypl.simplified.app.R;
import org.nypl.simplified.app.SimplifiedPart;
import org.nypl.simplified.books.core.BooksFeedSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main catalog feed activity, responsible for displaying different types of
 * feeds.
 */

public final class MainCatalogActivity extends CatalogFeedActivity
{
  private static final Logger LOG = LoggerFactory.getLogger(MainCatalogActivity.class);

  @Override
  protected Logger log() {
    return LOG;
  }

  /**
   * Construct an activity.
   */

  public MainCatalogActivity()
  {

  }

  @Override protected BooksFeedSelection getLocalFeedTypeSelection()
  {
    /*
     * This activity does not display local feeds. To ask it to do so is an
     * error!
     */

    throw new UnreachableCodeException();
  }

  @Override protected SimplifiedPart navigationDrawerGetPart()
  {
    return SimplifiedPart.PART_CATALOG;
  }

  @Override protected String catalogFeedGetEmptyText()
  {
    return this.getResources().getString(R.string.catalog_empty_feed);
  }
}
