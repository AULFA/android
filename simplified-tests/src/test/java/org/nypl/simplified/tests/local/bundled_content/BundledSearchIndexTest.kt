package org.nypl.simplified.tests.local.bundled_content

import org.nypl.simplified.tests.books.bundled_content.BundledSearchIndexContract
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BundledSearchIndexTest : BundledSearchIndexContract() {

  override val logger: Logger
    get() = LoggerFactory.getLogger(BundledSearchIndexTest::class.java)

}
