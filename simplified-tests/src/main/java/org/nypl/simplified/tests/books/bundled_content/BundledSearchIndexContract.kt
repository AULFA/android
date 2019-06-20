package org.nypl.simplified.tests.books.bundled_content

import org.junit.Assert
import org.junit.Test
import org.nypl.simplified.books.bundled_content.BundledSearchIndex
import org.slf4j.Logger
import java.io.ByteArrayInputStream
import java.net.URI

abstract class BundledSearchIndexContract {

  abstract val logger: Logger

  @Test
  fun testEmpty() {
    val index =
      ByteArrayInputStream(ByteArray(0)).use { stream ->
        BundledSearchIndex.open(stream)
      }

    val results = index.search(listOf("a", "b", "c"))
    results.forEach { result -> this.logger.debug("result: {}", result) }

    Assert.assertEquals(0, results.size)
  }

  @Test
  fun testIndex() {
    val index =
      BundledSearchIndex.open(
        stream = BundledSearchIndexContract::class.java.getResourceAsStream(
          "/org/nypl/simplified/tests/books/bundled_content/index.txt")!!)

    val results =
      index.search(listOf("Siva", "And", "Teddy", "Jungle", "Adventure"))
    results.forEach { result -> this.logger.debug("result: {}", result) }

    Assert.assertEquals(40, results.size)
    Assert.assertEquals(
      URI.create("simplified-bundled://feeds/D8038E93C488F0385F94D4F42E0FF481D9946C609254E600CF5EEEB7E51C14B1.atom"),
      results[0].uri)
  }

}
