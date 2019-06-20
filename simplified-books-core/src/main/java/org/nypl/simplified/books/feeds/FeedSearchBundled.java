package org.nypl.simplified.books.feeds;

/**
 * <p>The type of bundled searchers.</p>
 *
 * <p>This is actually just a marker type: When the application matches on a
 * value of type {@link FeedSearchType} and receives a {@code FeedSearchBundled},
 * then it knows to perform a search on a bundled feed.</p>
 */

public final class FeedSearchBundled implements FeedSearchType
{
  /**
   * Construct a local searcher.
   */

  public FeedSearchBundled()
  {

  }

  @Override public <A, E extends Exception> A matchSearch(
    final FeedSearchMatcherType<A, E> m)
    throws E
  {
    return m.onFeedSearchBundled(this);
  }
}
