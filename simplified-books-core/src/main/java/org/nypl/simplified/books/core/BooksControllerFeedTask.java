package org.nypl.simplified.books.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.nypl.simplified.books.core.FeedFacetPseudo.FacetType;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;
import org.nypl.simplified.opds.core.OPDSSearchLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnreachableCodeException;

@SuppressWarnings("synthetic-access") final class BooksControllerFeedTask implements
  Runnable
{
  private static final Logger LOG;

  static {
    LOG =
      NullCheck.notNull(LoggerFactory
        .getLogger(BooksControllerFeedTask.class));
  }

  private static void entriesLoad(
    final FeedWithoutGroups f,
    final List<BookDatabaseEntryType> dirs,
    final ArrayList<FeedEntryType> entries)
  {
    for (int index = 0; index < dirs.size(); ++index) {
      final BookDatabaseEntryReadableType dir =
        NullCheck.notNull(dirs.get(index));
      final BookID book_id = dir.getID();

      try {
        final OPDSAcquisitionFeedEntry data = dir.getData();
        entries.add(FeedEntryOPDS.fromOPDSAcquisitionFeedEntry(data));
      } catch (final Throwable x) {
        BooksControllerFeedTask.LOG.error(
          "unable to load book {} metadata: ",
          book_id,
          x);
        f.add(FeedEntryCorrupt.fromIDAndError(book_id, x));
      }
    }
  }

  private static void entriesSortForFacet(
    final List<FeedEntryType> entries,
    final FacetType facet_type)
  {
    switch (facet_type) {
      case SORT_BY_AUTHOR:
      {
        Collections.sort(entries, new Comparator<FeedEntryType>() {
          @Override public int compare(
            final @Nullable FeedEntryType o1,
            final @Nullable FeedEntryType o2)
          {
            final FeedEntryType o1_n = NullCheck.notNull(o1);
            final FeedEntryType o2_n = NullCheck.notNull(o2);

            if ((o1_n instanceof FeedEntryOPDS)
              && (o2_n instanceof FeedEntryOPDS)) {
              final FeedEntryOPDS fo1 = (FeedEntryOPDS) o1_n;
              final FeedEntryOPDS fo2 = (FeedEntryOPDS) o2_n;
              final List<String> authors1 = fo1.getFeedEntry().getAuthors();
              final List<String> authors2 = fo2.getFeedEntry().getAuthors();
              final boolean e0 = authors1.isEmpty();
              final boolean e1 = authors2.isEmpty();
              if (e0 && e1) {
                return 0;
              }
              if (e0) {
                return 1;
              }
              if (e1) {
                return -1;
              }

              final String author1 = NullCheck.notNull(authors1.get(0));
              final String author2 = NullCheck.notNull(authors2.get(0));
              return author1.compareTo(author2);
            }

            return 0;
          }
        });
        break;
      }
      case SORT_BY_TITLE:
      {
        Collections.sort(entries, new Comparator<FeedEntryType>() {
          @Override public int compare(
            final @Nullable FeedEntryType o1,
            final @Nullable FeedEntryType o2)
          {
            final FeedEntryType o1_n = NullCheck.notNull(o1);
            final FeedEntryType o2_n = NullCheck.notNull(o2);

            if ((o1_n instanceof FeedEntryOPDS)
              && (o2_n instanceof FeedEntryOPDS)) {
              final FeedEntryOPDS fo1 = (FeedEntryOPDS) o1_n;
              final FeedEntryOPDS fo2 = (FeedEntryOPDS) o2_n;
              final String title1 = fo1.getFeedEntry().getTitle();
              final String title2 = fo2.getFeedEntry().getTitle();
              return title1.compareTo(title2);
            }

            return 0;
          }
        });
        break;
      }
    }
  }

  private final BookDatabaseType                 books_database;
  private final FacetType                        facet_active;
  private final String                           facet_group;
  private final FeedFacetPseudoTitleProviderType facet_titles;
  private final String                           id;
  private final BookFeedListenerType             listener;
  private final String                           title;
  private final Calendar                         updated;
  private final URI                              uri;
  private final OptionType<String>               search;

  BooksControllerFeedTask(
    final BookDatabaseType in_books_database,
    final URI in_uri,
    final String in_id,
    final Calendar in_updated,
    final String in_title,
    final FeedFacetPseudo.FacetType in_facet_active,
    final String in_facet_group,
    final FeedFacetPseudoTitleProviderType in_facet_titles,
    final OptionType<String> in_search,
    final BookFeedListenerType in_listener)
  {
    this.books_database = NullCheck.notNull(in_books_database);
    this.uri = NullCheck.notNull(in_uri);
    this.id = NullCheck.notNull(in_id);
    this.updated = NullCheck.notNull(in_updated);
    this.title = NullCheck.notNull(in_title);
    this.facet_active = NullCheck.notNull(in_facet_active);
    this.facet_group = NullCheck.notNull(in_facet_group);
    this.facet_titles = NullCheck.notNull(in_facet_titles);
    this.search = NullCheck.notNull(in_search);
    this.listener = NullCheck.notNull(in_listener);
  }

  private FeedWithoutGroups feed()
  {
    final OptionType<URI> no_next = Option.none();

    final OPDSSearchLink search_link =
      new OPDSSearchLink(
        BooksController.LOCAL_SEARCH_TYPE,
        NullCheck.notNull(URI.create("generated-feed-search:unused")));
    final OptionType<OPDSSearchLink> some_search = Option.some(search_link);

    final Map<String, List<FeedFacetType>> facet_groups =
      new HashMap<String, List<FeedFacetType>>();
    final List<FeedFacetType> facets = new ArrayList<FeedFacetType>();

    for (final FeedFacetPseudo.FacetType v : FeedFacetPseudo.FacetType
      .values()) {
      final boolean active = v.equals(this.facet_active);
      final FeedFacetPseudo f =
        new FeedFacetPseudo(this.facet_titles.getTitle(v), active, v);
      facets.add(f);
    }
    facet_groups.put(this.facet_group, facets);

    final FeedWithoutGroups f =
      FeedWithoutGroups.newEmptyFeed(
        this.uri,
        this.id,
        this.updated,
        this.title,
        no_next,
        some_search,
        facet_groups,
        facets);

    final List<BookDatabaseEntryType> dirs =
      this.books_database.getBookDatabaseEntries();

    final ArrayList<FeedEntryType> entries = new ArrayList<FeedEntryType>();

    BooksControllerFeedTask.entriesLoad(f, dirs, entries);
    BooksControllerFeedTask.entriesSearch(entries, this.search);
    BooksControllerFeedTask.entriesSortForFacet(entries, this.facet_active);

    for (int index = 0; index < entries.size(); ++index) {
      f.add(entries.get(index));
    }

    return f;
  }

  private static boolean entriesSearchFeedEntryOPDSMatches(
    final List<String> terms_upper,
    final FeedEntryOPDS e)
  {
    for (int index = 0; index < terms_upper.size(); ++index) {
      final String term_upper = terms_upper.get(index);
      final OPDSAcquisitionFeedEntry ee = e.getFeedEntry();
      final String e_title = ee.getTitle().toUpperCase();
      if (e_title.contains(term_upper)) {
        return true;
      }

      final List<String> authors = ee.getAuthors();
      for (final String a : authors) {
        if (a.toUpperCase().contains(term_upper)) {
          return true;
        }
      }
    }

    return false;
  }

  private static void entriesSearch(
    final List<FeedEntryType> entries,
    final OptionType<String> in_search)
  {
    if (in_search.isSome()) {
      final Some<String> some_search = (Some<String>) in_search;
      final String term = some_search.get();
      final List<String> terms_upper =
        BooksControllerFeedTask.entriesSearchTermsSplitUpper(term);

      final FeedEntryMatcherType<Boolean, UnreachableCodeException> matcher =
        new FeedEntryMatcherType<Boolean, UnreachableCodeException>() {
          @Override public Boolean onFeedEntryOPDS(
            final FeedEntryOPDS e)
          {
            return BooksControllerFeedTask.entriesSearchFeedEntryOPDSMatches(
              terms_upper,
              e);
          }

          @Override public Boolean onFeedEntryCorrupt(
            final FeedEntryCorrupt e)
          {
            return Boolean.FALSE;
          }
        };

      final Iterator<FeedEntryType> iter = entries.iterator();
      while (iter.hasNext()) {
        final FeedEntryType e = iter.next();
        final Boolean ok = e.matchFeedEntry(matcher);
        if (ok.booleanValue() == false) {
          iter.remove();
        }
      }
    }
  }

  private static List<String> entriesSearchTermsSplitUpper(
    final String term)
  {
    final String[] terms = term.split("\\s+");
    final List<String> terms_upper = new ArrayList<String>();
    for (int index = 0; index < terms.length; ++index) {
      terms_upper.add(terms[index].toUpperCase());
    }
    return terms_upper;
  }

  @Override public void run()
  {
    try {
      this.listener.onBookFeedSuccess(this.feed());
    } catch (final Throwable x) {
      this.listener.onBookFeedFailure(x);
    }
  }
}