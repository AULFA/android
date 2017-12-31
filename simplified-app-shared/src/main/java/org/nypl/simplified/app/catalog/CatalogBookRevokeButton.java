package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.res.Resources;
import android.view.View;

import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.app.R;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.controller.BooksControllerType;
import org.nypl.simplified.books.core.BookDatabaseEntrySnapshot;

/**
 * A button for revoking loans or holds.
 */

public final class CatalogBookRevokeButton extends CatalogLeftPaddedButton
  implements CatalogBookButtonType
{
  /**
   * Construct a button.
   *
   * @param in_activity    The host activity
   * @param in_book_id     The book ID
   * @param in_revoke_type The revocation type (to show the correct button text
   *                       and dialog messages)
   * @param in_books       The books
   */

  public CatalogBookRevokeButton(
    final Activity in_activity,
    final BookID in_book_id,
    final CatalogBookRevokeType in_revoke_type,
    final BooksControllerType in_books)
  {
    super(in_activity);
    NullCheck.notNull(in_book_id);
    NullCheck.notNull(in_revoke_type);

    throw new UnimplementedCodeException();

//    final OptionType<BookDatabaseEntrySnapshot> snap_opt =
//      in_books.bookGetDatabase().databaseGetEntrySnapshot(in_book_id);
//
//    if (in_books.accountIsDeviceActive() || ((Some<BookDatabaseEntrySnapshot>) snap_opt).get().getAdobeRights().isNone()) {
//
//      final Resources rr = NullCheck.notNull(in_activity.getResources());
//
//      this.getTextView().setTextSize(12.0f);
//      this.setBackgroundResource(R.drawable.simplified_button);
//      this.getTextView().setTextColor(getBrandingColor());
//
//      switch (in_revoke_type) {
//        case REVOKE_LOAN: {
//          this.getTextView().setText(
//            NullCheck.notNull(rr.getString(R.string.catalog_book_revoke_loan)));
//          this.getTextView().setContentDescription(
//            NullCheck.notNull(rr.getString(R.string.catalog_accessibility_book_revoke_loan)));
//          break;
//        }
//        case REVOKE_HOLD: {
//          this.getTextView().setText(
//            NullCheck.notNull(rr.getString(R.string.catalog_book_revoke_hold)));
//          this.getTextView().setContentDescription(
//            NullCheck.notNull(rr.getString(R.string.catalog_accessibility_book_revoke_hold)));
//          break;
//        }
//      }
//
//      this.setOnClickListener(
//        new OnClickListener() {
//          @Override
//          public void onClick(
//            final @Nullable View v) {
//            final CatalogBookRevokeDialog d =
//              CatalogBookRevokeDialog.newDialog(in_revoke_type);
//            d.setOnConfirmListener(
//              new Runnable() {
//                @Override
//                public void run() {
//                  final BooksType books = getBooksType();
//                  books.bookRevoke(in_book_id);
//                }
//              });
//            final FragmentManager fm = in_activity.getFragmentManager();
//            d.show(fm, "revoke-confirm");
//          }
//        });
//    }
//    else
//    {
//      super.removeView(this);
//    }

  }
}
