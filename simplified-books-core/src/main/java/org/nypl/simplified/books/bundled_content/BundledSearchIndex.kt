package org.nypl.simplified.books.bundled_content

import org.apache.commons.text.similarity.LevenshteinDistance
import org.nypl.simplified.books.bundled_content.BundledSearchIndexType.ResultType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI

/**
 * The default search index implementation for bundled content.
 */

class BundledSearchIndex private constructor(
  private val distance: LevenshteinDistance,
  private val index: Map<String, List<URI>>)
  : BundledSearchIndexType {

  private data class Result(
    override val terms: MutableSet<String> = mutableSetOf(),
    override val uri: URI,
    override var relevancy: Int): ResultType

  override fun search(terms: List<String>): List<ResultType> {
    val results = mutableMapOf<URI, Result>()

    fun accumulateResult(
      term: String,
      uri: URI,
      score: Int) {
      val result = results[uri] ?: Result(mutableSetOf(), uri, 0)
      result.relevancy += score
      result.terms.add(term)
      results[uri] = result
    }

    for (rawTerm in terms) {
      val term = rawTerm.trim().toUpperCase()
      val size = term.length / 3
      for ((key, uris) in this.index) {
        var score = 0
        if (key.startsWith(term)) {
          score += 2
        }
        if (this.distance.apply(term, key) <= size) {
          score += 1
        }

        if (score > 0) {
          for (uri in uris) {
            accumulateResult(term, uri, score)
          }
        }
      }
    }

    return results.values.toList().sortedBy { result -> -result.relevancy }
  }

  companion object {

    /**
     * Open a search index from the given stream.
     */

    fun open(stream: InputStream): BundledSearchIndexType {
      val index = mutableMapOf<String, MutableList<URI>>()
      return BufferedReader(InputStreamReader(stream, "UTF-8")).use { reader ->
        var longest = 0
        while (true) {
          val line = reader.readLine() ?: break
          val segments = line.split(" ")
          if (segments.size == 2) {
            val term = segments[0]
            val uri = URI(segments[1])
            val list = index[term] ?: mutableListOf()
            list.add(uri)
            index[term] = list
            longest = Math.max(longest, term.length)
          }
        }
        BundledSearchIndex(LevenshteinDistance(longest), index.toMap())
      }
    }

  }
}