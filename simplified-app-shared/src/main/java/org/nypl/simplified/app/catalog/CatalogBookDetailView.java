package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.io7m.jfunctional.None;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnreachableCodeException;

import org.nypl.simplified.app.BookCoverProviderType;
import org.nypl.simplified.app.R;
import org.nypl.simplified.app.ScreenSizeInformationType;
import org.nypl.simplified.app.Simplified;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.assertions.Assertions;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.book_registry.BookRegistryReadableType;
import org.nypl.simplified.books.book_registry.BookStatusDownloadFailed;
import org.nypl.simplified.books.book_registry.BookStatusDownloadInProgress;
import org.nypl.simplified.books.book_registry.BookStatusDownloaded;
import org.nypl.simplified.books.book_registry.BookStatusDownloadingMatcherType;
import org.nypl.simplified.books.book_registry.BookStatusDownloadingType;
import org.nypl.simplified.books.book_registry.BookStatusEvent;
import org.nypl.simplified.books.book_registry.BookStatusHeld;
import org.nypl.simplified.books.book_registry.BookStatusHeldReady;
import org.nypl.simplified.books.book_registry.BookStatusHoldable;
import org.nypl.simplified.books.book_registry.BookStatusLoanable;
import org.nypl.simplified.books.book_registry.BookStatusLoaned;
import org.nypl.simplified.books.book_registry.BookStatusLoanedMatcherType;
import org.nypl.simplified.books.book_registry.BookStatusLoanedType;
import org.nypl.simplified.books.book_registry.BookStatusMatcherType;
import org.nypl.simplified.books.book_registry.BookStatusRequestingDownload;
import org.nypl.simplified.books.book_registry.BookStatusRequestingLoan;
import org.nypl.simplified.books.book_registry.BookStatusRequestingRevoke;
import org.nypl.simplified.books.book_registry.BookStatusRevokeFailed;
import org.nypl.simplified.books.book_registry.BookStatusRevoked;
import org.nypl.simplified.books.book_registry.BookStatusType;
import org.nypl.simplified.books.book_registry.BookWithStatus;
import org.nypl.simplified.books.controller.BooksControllerType;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.feeds.FeedEntryOPDS;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess;
import org.nypl.simplified.opds.core.OPDSAvailabilityType;
import org.nypl.simplified.opds.core.OPDSCategory;
import org.slf4j.Logger;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A book detail view.
 */

public final class CatalogBookDetailView
    implements BookStatusMatcherType<Unit, UnreachableCodeException>,
    BookStatusLoanedMatcherType<Unit, UnreachableCodeException>,
    BookStatusDownloadingMatcherType<Unit, UnreachableCodeException> {

  private static final URI GENRES_URI;
  private static final String GENRES_URI_TEXT;
  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(CatalogBookDetailView.class);
  }

  static {
    GENRES_URI = NullCheck.notNull(
        URI.create("http://librarysimplified.org/terms/genres/Simplified/"));
    GENRES_URI_TEXT =
        NullCheck.notNull(CatalogBookDetailView.GENRES_URI.toString());
  }

  private final Activity activity;
  private final ViewGroup book_download;
  private final LinearLayout book_download_buttons;
  private final TextView book_download_text;
  private final ViewGroup book_downloading;
  private final Button book_downloading_cancel;
  private final ViewGroup book_downloading_failed;
  private final LinearLayout book_downloading_failed_buttons;
  private final Button book_downloading_failed_dismiss;
  private final Button book_downloading_failed_retry;
  private final TextView book_downloading_percent_text;
  private final ProgressBar book_downloading_progress;
  private final AtomicReference<FeedEntryOPDS> entry;
  private final ScrollView scroll_view;
  private final TextView book_downloading_label;
  private final TextView book_downloading_failed_text;
  private final TextView book_debug_status;
  private final BooksControllerType books_controller;
  private final BookRegistryReadableType books_registry;
  private final ProfilesControllerType profiles_controller;
  private final AccountType account;

  /**
   * Construct a detail view.
   */

  public CatalogBookDetailView(
      final Activity in_activity,
      final LayoutInflater in_inflater,
      final AccountType in_account,
      final BookCoverProviderType in_cover_provider,
      final BookRegistryReadableType in_books_registry,
      final ProfilesControllerType in_profiles_controller,
      final BooksControllerType in_books_controller,
      final FeedEntryOPDS in_entry) {

    this.activity =
        NullCheck.notNull(in_activity, "Activity");
    this.account =
        NullCheck.notNull(in_account, "Account");
    this.profiles_controller =
        NullCheck.notNull(in_profiles_controller, "Profiles controller");
    this.books_controller =
        NullCheck.notNull(in_books_controller, "Books controller");
    this.books_registry =
        NullCheck.notNull(in_books_registry, "Books registry");
    this.entry =
        new AtomicReference<>(NullCheck.notNull(in_entry, "Entry"));

    NullCheck.notNull(in_books_registry, "Books registry");
    this.scroll_view = new ScrollView(in_activity);

    final LayoutParams p =
        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    this.scroll_view.setLayoutParams(p);
    this.scroll_view.addOnLayoutChangeListener(
        (v, left, top, right, bottom, old_left, old_top, old_right, old_bottom) -> scroll_view.setScrollY(0));

    final View layout = in_inflater.inflate(R.layout.book_dialog, this.scroll_view, false);
    this.scroll_view.addView(layout);

    final Resources resources = NullCheck.notNull(in_activity.getResources());

    /*
     * Show the book status if status debugging is enabled.
     */

    final TextView in_debug_status =
        NullCheck.notNull(layout.findViewById(R.id.book_debug_status));

    if (resources.getBoolean(R.bool.debug_catalog_cell_view_states)) {
      in_debug_status.setVisibility(View.VISIBLE);
    } else {
      in_debug_status.setVisibility(View.GONE);
    }
    this.book_debug_status = in_debug_status;

    final ViewGroup header =
        NullCheck.notNull(layout.findViewById(R.id.book_header));
    final ViewGroup header_left =
        NullCheck.notNull(header.findViewById(R.id.book_header_left));
    final TextView header_title =
        NullCheck.notNull(header.findViewById(R.id.book_header_title));
    final ImageView header_cover =
        NullCheck.notNull(header.findViewById(R.id.book_header_cover));
    final TextView header_authors =
        NullCheck.notNull(header.findViewById(R.id.book_header_authors));
    this.book_download_buttons =
        NullCheck.notNull(header.findViewById(R.id.book_dialog_download_buttons));
    this.book_downloading_cancel =
        NullCheck.notNull(header.findViewById(R.id.book_dialog_downloading_cancel));
    this.book_downloading_failed_buttons =
        NullCheck.notNull(header.findViewById(R.id.book_dialog_downloading_failed_buttons));
    this.book_downloading_failed_dismiss =
        NullCheck.notNull(header.findViewById(R.id.book_dialog_downloading_failed_dismiss));
    this.book_downloading_failed_retry =
        NullCheck.notNull(header.findViewById(R.id.book_dialog_downloading_failed_retry));

    final ViewGroup bdd =
        NullCheck.notNull(layout.findViewById(R.id.book_dialog_downloading));
    this.book_downloading = bdd;

    this.book_downloading_label =
        NullCheck.notNull(bdd.findViewById(R.id.book_dialog_downloading_label));
    this.book_downloading_percent_text =
        NullCheck.notNull(bdd.findViewById(R.id.book_dialog_downloading_percent_text));
    this.book_downloading_progress =
        NullCheck.notNull(bdd.findViewById(R.id.book_dialog_downloading_progress));

    final ViewGroup bdf =
        NullCheck.notNull(layout.findViewById(R.id.book_dialog_downloading_failed));
    this.book_downloading_failed_text =
        NullCheck.notNull(bdf.findViewById(R.id.book_dialog_downloading_failed_text));
    this.book_downloading_failed = bdf;

    final ViewGroup bd =
        NullCheck.notNull(layout.findViewById(R.id.book_dialog_download));
    this.book_download = bd;

    this.book_download_text =
        NullCheck.notNull(bd.findViewById(R.id.book_dialog_download_text));

    final ViewGroup summary =
        NullCheck.notNull(layout.findViewById(R.id.book_summary_layout));
    final TextView summary_section_title =
        NullCheck.notNull(summary.findViewById(R.id.book_summary_section_title));
    final WebView summary_text =
        NullCheck.notNull(summary.findViewById(R.id.book_summary_text));
    final TextView header_meta =
        NullCheck.notNull(summary.findViewById(R.id.book_header_meta));

    final Button read_more_button =
        NullCheck.notNull(summary.findViewById(R.id.book_summary_read_more_button));

    read_more_button.setOnClickListener(v -> {
      CatalogBookDetailView.configureSummaryWebViewHeight(summary_text);
      read_more_button.setVisibility(View.INVISIBLE);
    });


    /*
     * Assuming a roughly fixed height for cover images, assume a 4:3 aspect
     * ratio and set the width of the cover layout.
     */

    final int cover_height = header_cover.getLayoutParams().height;
    final int cover_width = (int) (((double) cover_height / 4.0) * 3.0);
    final LinearLayout.LayoutParams cp =
        new LinearLayout.LayoutParams(cover_width, LayoutParams.WRAP_CONTENT);
    header_left.setLayoutParams(cp);

    /*
     * Configure detail texts.
     */

    final OPDSAcquisitionFeedEntry eo = in_entry.getFeedEntry();
    CatalogBookDetailView.configureSummarySectionTitle(summary_section_title);

    final BookID book_id = in_entry.getBookID();
    this.onStatus(in_entry, in_books_registry.bookStatus(book_id));

    CatalogBookDetailView.configureSummaryWebView(eo, summary_text);
    header_title.setText(eo.getTitle());
    CatalogBookDetailView.configureViewTextAuthor(eo, header_authors);
    CatalogBookDetailView.configureViewTextMeta(resources, eo, header_meta);
    in_cover_provider.loadCoverInto(in_entry, header_cover, cover_width, cover_height);
  }

  private static void configureButtonsHeight(
      final Resources rr,
      final LinearLayout layout) {

    final ScreenSizeInformationType screen = Simplified.getScreenSizeInformation();
    final int dp35 = (int) rr.getDimension(R.dimen.button_standard_height);
    final int dp8 = (int) screen.screenDPToPixels(8);
    final int button_count = layout.getChildCount();
    for (int index = 0; index < button_count; ++index) {
      final View v = layout.getChildAt(index);

      Assertions.checkPrecondition(
          v instanceof CatalogBookButtonType,
          "view %s is an instance of CatalogBookButtonType",
          v);

      final android.widget.LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp35);
      lp.leftMargin = dp8;

      v.setLayoutParams(lp);
    }
  }

  private static void configureSummarySectionTitle(
      final TextView summary_section_title) {
    summary_section_title.setText("Description");
  }

  private static void configureSummaryWebView(
      final OPDSAcquisitionFeedEntry e,
      final WebView summary_text) {

    final StringBuilder text = new StringBuilder();
    text.append("<html>");
    text.append("<head>");
    text.append("<style>body {");
    text.append("padding: 0;");
    text.append("padding-right: 2em;");
    text.append("margin: 0;");
    text.append("}</style>");
    text.append("</head>");
    text.append("<body>");
    text.append(e.getSummary());
    text.append("</body>");
    text.append("</html>");

    final WebSettings summary_text_settings = summary_text.getSettings();
    summary_text_settings.setAllowContentAccess(false);
    summary_text_settings.setAllowFileAccess(false);
    summary_text_settings.setAllowFileAccessFromFileURLs(false);
    summary_text_settings.setAllowUniversalAccessFromFileURLs(false);
    summary_text_settings.setBlockNetworkLoads(true);
    summary_text_settings.setBlockNetworkImage(true);
    summary_text_settings.setDefaultTextEncodingName("UTF-8");
    summary_text_settings.setDefaultFixedFontSize(14);
    summary_text_settings.setDefaultFontSize(14);
    summary_text.loadDataWithBaseURL(null, text.toString(), "text/html", "UTF-8", null);
  }

  /**
   * Configure the given web view to match the height of the rendered content.
   */

  private static void configureSummaryWebViewHeight(
      final WebView summary_text) {
    final LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    summary_text.setLayoutParams(q);
  }

  private static void configureViewTextAuthor(
      final OPDSAcquisitionFeedEntry e,
      final TextView authors) {
    final StringBuilder buffer = new StringBuilder();
    final List<String> as = e.getAuthors();
    for (int index = 0; index < as.size(); ++index) {
      final String a = NullCheck.notNull(as.get(index));
      if (index > 0) {
        buffer.append("\n");
      }
      buffer.append(a);
    }
    authors.setText(NullCheck.notNull(buffer.toString()));
  }

  private static void configureViewTextMeta(
      final Resources rr,
      final OPDSAcquisitionFeedEntry e,
      final TextView meta) {
    final StringBuilder buffer = new StringBuilder();
    CatalogBookDetailView.createViewTextPublicationDate(rr, e, buffer);
    CatalogBookDetailView.createViewTextPublisher(rr, e, buffer);
    CatalogBookDetailView.createViewTextCategories(rr, e, buffer);
    CatalogBookDetailView.createViewTextDistributor(rr, e, buffer);
    meta.setText(NullCheck.notNull(buffer.toString()));
  }

  private static void createViewTextCategories(
      final Resources rr,
      final OPDSAcquisitionFeedEntry e,
      final StringBuilder buffer) {
    final List<OPDSCategory> cats = e.getCategories();

    boolean has_genres = false;
    for (int index = 0; index < cats.size(); ++index) {
      final OPDSCategory c = NullCheck.notNull(cats.get(index));
      if (CatalogBookDetailView.GENRES_URI_TEXT.equals(c.getScheme())) {
        has_genres = true;
      }
    }

    if (has_genres) {
      if (buffer.length() > 0) {
        buffer.append("\n");
      }

      buffer.append(NullCheck.notNull(rr.getString(R.string.catalog_categories)));
      buffer.append(": ");

      for (int index = 0; index < cats.size(); ++index) {
        final OPDSCategory c = NullCheck.notNull(cats.get(index));
        if (CatalogBookDetailView.GENRES_URI_TEXT.equals(c.getScheme())) {
          buffer.append(c.getEffectiveLabel());
          if ((index + 1) < cats.size()) {
            buffer.append(", ");
          }
        }
      }
    }
  }

  private static String createViewTextPublicationDate(
      final Resources rr,
      final OPDSAcquisitionFeedEntry e,
      final StringBuilder buffer) {
    if (buffer.length() > 0) {
      buffer.append("\n");
    }

    final OptionType<Calendar> p_opt = e.getPublished();
    if (p_opt.isSome()) {
      final Some<Calendar> some = (Some<Calendar>) p_opt;
      final Calendar p = some.get();
      final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
      buffer.append(NullCheck.notNull(rr.getString(R.string.catalog_publication_date)));
      buffer.append(": ");
      buffer.append(fmt.format(p.getTime()));
      return NullCheck.notNull(buffer.toString());
    }

    return "";
  }

  private static void createViewTextPublisher(
      final Resources rr,
      final OPDSAcquisitionFeedEntry e,
      final StringBuilder buffer) {
    final OptionType<String> pub = e.getPublisher();
    if (pub.isSome()) {
      final Some<String> some = (Some<String>) pub;

      if (buffer.length() > 0) {
        buffer.append("\n");
      }

      buffer.append(NullCheck.notNull(rr.getString(R.string.catalog_publisher)));
      buffer.append(": ");
      buffer.append(some.get());
    }
  }

  private static void createViewTextDistributor(
      final Resources rr,
      final OPDSAcquisitionFeedEntry e,
      final StringBuilder buffer) {
    if (buffer.length() > 0) {
      buffer.append("\n");
    }

    buffer.append(String.format(rr.getString(R.string.catalog_book_distribution), e.getDistribution()));
  }

  /**
   * @return The scrolling view containing the book details
   */

  public ScrollView getScrollView() {
    return this.scroll_view;
  }

  @Override
  public Unit onBookStatusDownloaded(final BookStatusDownloaded d) {
    UIThread.checkIsUIThread();

    this.book_debug_status.setText("downloaded");
    this.book_download_buttons.removeAllViews();

    final Resources rr = NullCheck.notNull(this.activity.getResources());
    final String text = CatalogBookAvailabilityStrings.getAvailabilityString(rr, d);
    this.book_download_text.setText(text);

    this.book_download_buttons.addView(
        new CatalogBookReadButton(
            this.activity,
            this.account,
            d.getID()),
        0);

    if (d.isReturnable()) {
      final CatalogBookRevokeButton revoke =
          new CatalogBookRevokeButton(
              this.activity,
              this.books_controller,
              this.account,
              d.getID(),
              CatalogBookRevokeType.REVOKE_LOAN
          );

      this.book_download_buttons.addView(revoke, 1);
    } else if (this.entry.get().getFeedEntry().getAvailability() instanceof OPDSAvailabilityOpenAccess) {
      this.book_download_buttons.addView(
          new CatalogBookDeleteButton(
              this.activity,
              this.books_controller,
              this.account,
              d.getID()),
          1);
    }

    this.book_download_buttons.setVisibility(View.VISIBLE);

    CatalogBookDetailView.configureButtonsHeight(
        this.activity.getResources(), this.book_download_buttons);

    this.book_download.setVisibility(View.VISIBLE);
    this.book_download_buttons.setVisibility(View.VISIBLE);
    this.book_downloading.setVisibility(View.INVISIBLE);
    this.book_downloading_cancel.setVisibility(View.INVISIBLE);
    this.book_downloading_failed.setVisibility(View.INVISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.INVISIBLE);
    return Unit.unit();
  }

  @Override
  public Unit onBookStatusDownloadFailed(final BookStatusDownloadFailed f) {
    UIThread.checkIsUIThread();

    ConnectivityManager cm =
        (ConnectivityManager) this.activity.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo netInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
    if (netInfo == null || !netInfo.isConnectedOrConnecting()) {
      this.onBookStatusLoaned(new BookStatusLoaned(f.getID(), None.none(), false));
      return Unit.unit();
    }

    this.book_debug_status.setText("download failed");

    this.book_download.setVisibility(View.INVISIBLE);
    this.book_download_buttons.setVisibility(View.INVISIBLE);
    this.book_downloading.setVisibility(View.INVISIBLE);
    this.book_downloading_cancel.setVisibility(View.INVISIBLE);
    this.book_downloading_failed.setVisibility(View.VISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.VISIBLE);

    final Resources rr = NullCheck.notNull(this.activity.getResources());

    final FeedEntryOPDS current_entry = this.entry.get();

    final TextView failed =
        NullCheck.notNull(this.book_downloading_failed_text);
    failed.setText(CatalogBookErrorStrings.getFailureString(rr, f));

    final Button dismiss =
        NullCheck.notNull(this.book_downloading_failed_dismiss);
    final Button retry = NullCheck.notNull(this.book_downloading_failed_retry);

    dismiss.setOnClickListener(
        view -> this.books_controller.bookBorrowFailedDismiss(this.account, f.getID()));

    /*
     * Manually construct an acquisition controller for the retry button.
     */

    final OPDSAcquisitionFeedEntry eo = current_entry.getFeedEntry();
    final OptionType<OPDSAcquisition> a_opt =
        CatalogAcquisitionButtons.getPreferredAcquisition(
            f.getID(), eo.getAcquisitions());

    /*
     * Theoretically, if the book has ever been downloaded, then the
     * acquisition list must have contained one usable acquisition relation...
     */

    if (a_opt.isNone()) {
      throw new UnreachableCodeException();
    }

    final OPDSAcquisition a = ((Some<OPDSAcquisition>) a_opt).get();
    final CatalogAcquisitionButtonController retry_ctl =
        new CatalogAcquisitionButtonController(
            this.activity,
            this.profiles_controller,
            this.books_controller,
            this.books_registry,
            current_entry.getBookID(),
            a,
            current_entry);

    retry.setEnabled(true);
    retry.setVisibility(View.VISIBLE);
    retry.setOnClickListener(retry_ctl);

    return Unit.unit();
  }

  @Override
  public Unit onBookStatusDownloading(final BookStatusDownloadingType o) {
    return o.matchBookDownloadingStatus(this);
  }

  @Override
  public Unit onBookStatusDownloadInProgress(final BookStatusDownloadInProgress d) {
    UIThread.checkIsUIThread();

    this.book_debug_status.setText("download in progress");

    this.book_download.setVisibility(View.INVISIBLE);
    this.book_download_buttons.setVisibility(View.INVISIBLE);
    this.book_downloading.setVisibility(View.VISIBLE);
    this.book_downloading_cancel.setVisibility(View.VISIBLE);
    this.book_downloading_failed.setVisibility(View.INVISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.INVISIBLE);

    this.book_downloading_label.setText(R.string.catalog_downloading);
    CatalogDownloadProgressBar.setProgressBar(
        d.getCurrentTotalBytes(),
        d.getExpectedTotalBytes(),
        NullCheck.notNull(this.book_downloading_percent_text),
        NullCheck.notNull(this.book_downloading_progress));

    final Button dc = NullCheck.notNull(this.book_downloading_cancel);
    dc.setVisibility(View.VISIBLE);
    dc.setEnabled(true);
    dc.setOnClickListener(
        view -> this.books_controller.bookDownloadCancel(this.account, d.getID()));

    return Unit.unit();
  }

  @Override
  public Unit onBookStatusHeld(final BookStatusHeld s) {
    UIThread.checkIsUIThread();

    this.book_debug_status.setText("held");

    this.book_download_buttons.removeAllViews();
    this.book_download_buttons.setVisibility(View.VISIBLE);
    this.book_download.setVisibility(View.VISIBLE);
    this.book_downloading.setVisibility(View.INVISIBLE);
    this.book_downloading_cancel.setVisibility(View.INVISIBLE);
    this.book_downloading_failed.setVisibility(View.INVISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.INVISIBLE);

    final Resources rr = NullCheck.notNull(this.activity.getResources());

    if (s.isRevocable()) {
      final CatalogBookRevokeButton revoke =
          new CatalogBookRevokeButton(
              this.activity,
              this.books_controller,
              this.account,
              s.getID(),
              CatalogBookRevokeType.REVOKE_HOLD
          );

      this.book_download_buttons.addView(revoke, 0);
    }

    CatalogBookDetailView.configureButtonsHeight(
        rr, this.book_download_buttons);

    final String text =
        CatalogBookAvailabilityStrings.getAvailabilityString(rr, s);
    this.book_download_text.setText(text);

    return Unit.unit();
  }

  @Override
  public Unit onBookStatusHeldReady(final BookStatusHeldReady s) {
    UIThread.checkIsUIThread();

    this.book_debug_status.setText("held-ready");

    this.book_download_buttons.removeAllViews();
    this.book_download_buttons.setVisibility(View.VISIBLE);
    this.book_download.setVisibility(View.VISIBLE);
    this.book_downloading.setVisibility(View.INVISIBLE);
    this.book_downloading_cancel.setVisibility(View.INVISIBLE);
    this.book_downloading_failed.setVisibility(View.INVISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.INVISIBLE);

    final Resources rr = NullCheck.notNull(this.activity.getResources());
    final String text = CatalogBookAvailabilityStrings.getAvailabilityString(rr, s);
    this.book_download_text.setText(text);

    CatalogAcquisitionButtons.addButtons(
        this.activity,
        this.account,
        this.book_download_buttons,
        this.books_controller,
        this.profiles_controller,
        this.books_registry,
        NullCheck.notNull(this.entry.get()));

    if (s.isRevocable()) {
      final CatalogBookRevokeButton revoke =
          new CatalogBookRevokeButton(
              this.activity,
              this.books_controller,
              this.account,
              s.getID(),
              CatalogBookRevokeType.REVOKE_HOLD
          );

      this.book_download_buttons.addView(revoke, 0);
    }

    CatalogBookDetailView.configureButtonsHeight(
        rr, this.book_download_buttons);
    return Unit.unit();
  }

  @Override
  public Unit onBookStatusHoldable(final BookStatusHoldable s) {
    UIThread.checkIsUIThread();

    this.book_debug_status.setText("holdable");

    this.book_download_buttons.removeAllViews();
    this.book_download_buttons.setVisibility(View.VISIBLE);
    this.book_download.setVisibility(View.VISIBLE);
    this.book_downloading.setVisibility(View.INVISIBLE);
    this.book_downloading_cancel.setVisibility(View.INVISIBLE);
    this.book_downloading_failed.setVisibility(View.INVISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.INVISIBLE);

    final Resources rr = NullCheck.notNull(this.activity.getResources());
    final String text =
        CatalogBookAvailabilityStrings.getAvailabilityString(rr, s);
    this.book_download_text.setText(text);

    CatalogAcquisitionButtons.addButtons(
        this.activity,
        this.account,
        this.book_download_buttons,
        this.books_controller,
        this.profiles_controller,
        this.books_registry,
        NullCheck.notNull(this.entry.get()));

    CatalogBookDetailView.configureButtonsHeight(rr, this.book_download_buttons);
    return Unit.unit();
  }

  @Override
  public Unit onBookStatusLoanable(final BookStatusLoanable s) {
    UIThread.checkIsUIThread();

    this.onBookStatusNone(this.entry.get());
    this.book_debug_status.setText("loanable");
    return Unit.unit();
  }

  @Override
  public Unit onBookStatusRevokeFailed(final BookStatusRevokeFailed s) {
    UIThread.checkIsUIThread();

    this.book_debug_status.setText("revoke failed");
    this.book_download.setVisibility(View.INVISIBLE);
    this.book_downloading.setVisibility(View.INVISIBLE);
    this.book_downloading_cancel.setVisibility(View.INVISIBLE);
    this.book_downloading_failed.setVisibility(View.VISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.VISIBLE);

    final TextView failed = NullCheck.notNull(this.book_downloading_failed_text);
    failed.setText(R.string.catalog_revoke_failed);

    final Button dismiss = NullCheck.notNull(this.book_downloading_failed_dismiss);
    final Button retry = NullCheck.notNull(this.book_downloading_failed_retry);

    dismiss.setOnClickListener(
        view -> books_controller.bookRevokeFailedDismiss(account, s.getID()));

    retry.setEnabled(false);
    retry.setVisibility(View.GONE);
    return Unit.unit();
  }

  @Override
  public Unit onBookStatusRevoked(final BookStatusRevoked o) {
    UIThread.checkIsUIThread();

    this.book_debug_status.setText("revoked");

    this.book_download_buttons.removeAllViews();
    this.book_download_buttons.setVisibility(View.VISIBLE);
    this.book_download.setVisibility(View.VISIBLE);
    this.book_downloading.setVisibility(View.INVISIBLE);
    this.book_downloading_cancel.setVisibility(View.INVISIBLE);
    this.book_downloading_failed.setVisibility(View.INVISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.INVISIBLE);

    final Resources rr = NullCheck.notNull(this.activity.getResources());
    final String text =
        CatalogBookAvailabilityStrings.getAvailabilityString(rr, o);
    this.book_download_text.setText(text);

    final CatalogBookRevokeButton revoke =
        new CatalogBookRevokeButton(
            this.activity,
            this.books_controller,
            this.account,
            o.getID(),
            CatalogBookRevokeType.REVOKE_LOAN
        );
    this.book_download_buttons.addView(revoke, 0);

    CatalogBookDetailView.configureButtonsHeight(
        rr, this.book_download_buttons);

    return Unit.unit();
  }

  @Override
  public Unit onBookStatusLoaned(final BookStatusLoaned o) {
    UIThread.checkIsUIThread();

    this.book_debug_status.setText("loaned");

    this.book_download_buttons.removeAllViews();
    this.book_download_buttons.setVisibility(View.VISIBLE);
    this.book_download.setVisibility(View.VISIBLE);
    this.book_downloading.setVisibility(View.INVISIBLE);
    this.book_downloading_cancel.setVisibility(View.INVISIBLE);
    this.book_downloading_failed.setVisibility(View.INVISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.INVISIBLE);

    final Resources rr = NullCheck.notNull(this.activity.getResources());
    final String text =
        CatalogBookAvailabilityStrings.getAvailabilityString(rr, o);
    this.book_download_text.setText(text);

    CatalogAcquisitionButtons.addButtons(
        this.activity,
        this.account,
        this.book_download_buttons,
        this.books_controller,
        this.profiles_controller,
        this.books_registry,
        NullCheck.notNull(this.entry.get()));

    if (o.isReturnable()) {
      final CatalogBookRevokeButton revoke =
          new CatalogBookRevokeButton(
              this.activity,
              this.books_controller,
              this.account,
              o.getID(),
              CatalogBookRevokeType.REVOKE_LOAN
          );

      this.book_download_buttons.addView(revoke, 1);
    }

    CatalogBookDetailView.configureButtonsHeight(rr, this.book_download_buttons);
    return Unit.unit();
  }

  @Override
  public Unit onBookStatusLoanedType(final BookStatusLoanedType o) {
    return o.matchBookLoanedStatus(this);
  }

  private void onBookStatusNone(final FeedEntryOPDS e) {
    UIThread.checkIsUIThread();

    this.book_debug_status.setText("none");

    this.book_download_buttons.removeAllViews();
    this.book_download_buttons.setVisibility(View.VISIBLE);
    this.book_download.setVisibility(View.VISIBLE);
    this.book_downloading.setVisibility(View.INVISIBLE);
    this.book_downloading_cancel.setVisibility(View.INVISIBLE);
    this.book_downloading_failed.setVisibility(View.INVISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.INVISIBLE);

    final Resources rr = NullCheck.notNull(this.activity.getResources());
    final OPDSAcquisitionFeedEntry eo = e.getFeedEntry();
    final OPDSAvailabilityType avail = eo.getAvailability();
    final String text =
        CatalogBookAvailabilityStrings.getOPDSAvailabilityString(rr, avail);
    this.book_download_text.setText(text);

    CatalogAcquisitionButtons.addButtons(
        this.activity,
        this.account,
        this.book_download_buttons,
        this.books_controller,
        this.profiles_controller,
        this.books_registry,
        e);

    CatalogBookDetailView.configureButtonsHeight(rr, this.book_download_buttons);
  }

  @Override
  public Unit onBookStatusRequestingDownload(final BookStatusRequestingDownload d) {
    UIThread.checkIsUIThread();

    this.book_debug_status.setText("requesting download");

    this.book_download.setVisibility(View.INVISIBLE);
    this.book_download_buttons.setVisibility(View.INVISIBLE);
    this.book_downloading.setVisibility(View.VISIBLE);
    this.book_downloading_cancel.setVisibility(View.VISIBLE);
    this.book_downloading_failed.setVisibility(View.INVISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.INVISIBLE);

    this.book_downloading_label.setText(R.string.catalog_downloading);

    CatalogDownloadProgressBar.setProgressBar(
        0,
        100,
        NullCheck.notNull(this.book_downloading_percent_text),
        NullCheck.notNull(this.book_downloading_progress));

    final Button dc = NullCheck.notNull(this.book_downloading_cancel);
    dc.setEnabled(false);
    dc.setVisibility(View.INVISIBLE);
    dc.setOnClickListener(null);
    return Unit.unit();
  }

  @Override
  public Unit onBookStatusRequestingLoan(final BookStatusRequestingLoan s) {
    UIThread.checkIsUIThread();

    this.book_debug_status.setText("requesting loan");

    this.book_download.setVisibility(View.INVISIBLE);
    this.book_download_buttons.setVisibility(View.INVISIBLE);
    this.book_downloading.setVisibility(View.VISIBLE);
    this.book_downloading_cancel.setVisibility(View.VISIBLE);
    this.book_downloading_failed.setVisibility(View.INVISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.INVISIBLE);

    this.book_downloading_label.setText(R.string.catalog_requesting_loan);

    CatalogDownloadProgressBar.setProgressBar(
        0,
        100,
        NullCheck.notNull(this.book_downloading_percent_text),
        NullCheck.notNull(this.book_downloading_progress));

    final Button dc = NullCheck.notNull(this.book_downloading_cancel);
    dc.setEnabled(false);
    dc.setVisibility(View.INVISIBLE);
    dc.setOnClickListener(null);
    return Unit.unit();
  }

  @Override
  public Unit onBookStatusRequestingRevoke(final BookStatusRequestingRevoke s) {
    UIThread.checkIsUIThread();

    this.book_debug_status.setText("requesting revoke");

    this.book_download.setVisibility(View.INVISIBLE);
    this.book_download_buttons.setVisibility(View.INVISIBLE);
    this.book_downloading.setVisibility(View.VISIBLE);
    this.book_downloading_cancel.setVisibility(View.VISIBLE);
    this.book_downloading_failed.setVisibility(View.INVISIBLE);
    this.book_downloading_failed_buttons.setVisibility(View.INVISIBLE);

    this.book_downloading_label.setText(R.string.catalog_requesting_loan);

    CatalogDownloadProgressBar.setProgressBar(
        0,
        100,
        NullCheck.notNull(this.book_downloading_percent_text),
        NullCheck.notNull(this.book_downloading_progress));

    final Button dc = NullCheck.notNull(this.book_downloading_cancel);
    dc.setEnabled(false);
    dc.setVisibility(View.INVISIBLE);
    dc.setOnClickListener(null);
    return Unit.unit();
  }

  private void onStatus(
      final FeedEntryOPDS e,
      final OptionType<BookStatusType> status_opt) {

    if (status_opt.isSome()) {
      final Some<BookStatusType> some = (Some<BookStatusType>) status_opt;
      UIThread.runOnUIThread(() -> some.get().matchBookStatus(CatalogBookDetailView.this));
    } else {
      UIThread.runOnUIThread(() -> CatalogBookDetailView.this.onBookStatusNone(e));
    }
  }

  void onBookEvent(final BookStatusEvent event) {
    NullCheck.notNull(event, "Event");

    final BookID update_id = event.book();
    final FeedEntryOPDS current_entry = this.entry.get();
    final BookID current_id = current_entry.getBookID();

    if (current_id.equals(update_id)) {
      switch (event.type()) {
        case BOOK_CHANGED: {
          final BookWithStatus book_with_status = this.books_registry.books().get(update_id);
          if (book_with_status != null) {
            this.entry.set(FeedEntryOPDS.fromOPDSAcquisitionFeedEntry(book_with_status.book().entry()));
            UIThread.runOnUIThread(() -> book_with_status.status().matchBookStatus(this));
            return;
          }
        }
        case BOOK_REMOVED: {
          UIThread.runOnUIThread(() -> this.onBookStatusNone(current_entry));
        }
      }
    }
  }
}
