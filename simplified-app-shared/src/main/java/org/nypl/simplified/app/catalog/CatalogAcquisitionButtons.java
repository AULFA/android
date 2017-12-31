package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnreachableCodeException;

import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.controller.BooksControllerType;
import org.nypl.simplified.books.core.FeedEntryOPDS;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;
import org.slf4j.Logger;

import java.util.List;

/**
 * Utility functions for configuring a set of acquisition buttons.
 */

public final class CatalogAcquisitionButtons {
  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(CatalogAcquisitionButtons.class);
  }

  private CatalogAcquisitionButtons() {
    throw new UnreachableCodeException();
  }

  /**
   * Given a feed entry, add all the required acquisition buttons to the given
   * view group.
   *
   * @param in_activity         The activity hosting the view
   * @param in_account_provider The current account provider
   * @param in_view_group       The view group
   * @param in_books            The books controller
   * @param in_entry            The feed entry
   */

  public static void addButtons(
      final Activity in_activity,
      final AccountProvider in_account_provider,
      final ViewGroup in_view_group,
      final BooksControllerType in_books,
      final FeedEntryOPDS in_entry) {
    NullCheck.notNull(in_activity, "Activity");
    NullCheck.notNull(in_account_provider, "Account provider");
    NullCheck.notNull(in_view_group, "View group");
    NullCheck.notNull(in_books, "Book controller");
    NullCheck.notNull(in_entry, "Entry");

    in_view_group.setVisibility(View.VISIBLE);
    in_view_group.removeAllViews();

    final BookID book_id = in_entry.getBookID();
    final OPDSAcquisitionFeedEntry eo = in_entry.getFeedEntry();

    final OptionType<OPDSAcquisition> a_opt =
        CatalogAcquisitionButtons.getPreferredAcquisition(
            book_id, eo.getAcquisitions());
    if (a_opt.isSome()) {
      final OPDSAcquisition a = ((Some<OPDSAcquisition>) a_opt).get();
      final CatalogAcquisitionButton b = new CatalogAcquisitionButton(
          in_activity, in_account_provider, in_books, book_id, a, in_entry);
      in_view_group.addView(b);
    }
  }

  /**
   * Return the preferred acquisition type, from the list of types.
   *
   * @param book_id      The book ID
   * @param acquisitions The list of acquisition types
   * @return The preferred acquisition, if any
   */

  public static OptionType<OPDSAcquisition> getPreferredAcquisition(
      final BookID book_id,
      final List<OPDSAcquisition> acquisitions) {
    NullCheck.notNull(book_id, "Book ID");
    NullCheck.notNull(acquisitions, "Acquisitions");

    if (acquisitions.isEmpty()) {
      LOG.debug("[{}]: no acquisitions, so no best acquisition!", book_id);
      return Option.none();
    }

    OPDSAcquisition best = NullCheck.notNull(acquisitions.get(0));
    for (final OPDSAcquisition current : acquisitions) {
      final OPDSAcquisition nn_current = NullCheck.notNull(current);
      if (CatalogAcquisitionButtons.priority(nn_current)
          > CatalogAcquisitionButtons.priority(best)) {
        best = nn_current;
      }
    }

    LOG.debug("[{}]: best acquisition of {} was {}", book_id, acquisitions, best);
    return Option.some(best);
  }

  private static int priority(
      final OPDSAcquisition a) {
    switch (a.getType()) {
      case ACQUISITION_BORROW:
        return 6;
      case ACQUISITION_OPEN_ACCESS:
        return 5;
      case ACQUISITION_GENERIC:
        return 4;
      case ACQUISITION_SAMPLE:
        return 3;
      case ACQUISITION_BUY:
        return 2;
      case ACQUISITION_SUBSCRIBE:
        return 1;
    }

    return 0;
  }
}
