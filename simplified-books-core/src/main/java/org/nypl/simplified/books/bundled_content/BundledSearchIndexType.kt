package org.nypl.simplified.books.bundled_content

import java.net.URI

/**
 * The type of bundled search indices.
 *
 * A search index maps a set of terms to zero or more URIs.
 */

interface BundledSearchIndexType {

  /**
   * Perform a search using the given terms.
   */

  fun search(terms: List<String>): List<ResultType>

  /**
   * Perform a search using the given terms.
   */

  fun searchRaw(terms: List<String>): List<URI> {
    return this.search(terms).map(ResultType::uri)
  }

  /**
   * A search result.
   */

  interface ResultType {

    val terms: Set<String>

    val uri: URI

    val relevancy: Int
  }

}
