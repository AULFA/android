package org.nypl.simplified.app.reader;

import android.content.Context;

import com.bugsnag.android.Severity;
import com.io7m.jfunctional.None;
import com.io7m.jfunctional.OptionPartialVisitorType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;

import org.nypl.drm.core.AdobeAdeptContentFilterType;
import org.nypl.drm.core.AdobeAdeptContentRightsClientType;
import org.nypl.drm.core.DRMException;
import org.nypl.drm.core.DRMUnsupportedException;
import org.nypl.simplified.app.AdobeDRMServices;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.bugsnag.IfBugsnag;
import org.nypl.simplified.files.FileUtilities;
import org.readium.sdk.android.Container;
import org.readium.sdk.android.EPub3;
import org.readium.sdk.android.Package;
import org.readium.sdk.android.SdkErrorHandler;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * The default implementation of the {@link ReaderReadiumEPUBLoaderType}
 * interface.
 */

public final class ReaderReadiumEPUBLoader implements ReaderReadiumEPUBLoaderType {

  private static final Logger LOG = LogUtilities.getLog(ReaderReadiumEPUBLoader.class);

  private final ConcurrentHashMap<File, Container> containers;
  private final ExecutorService exec;
  private final Context context;

  private ReaderReadiumEPUBLoader(
      final Context in_context,
      final ExecutorService in_exec) {

    this.exec = NullCheck.notNull(in_exec);
    this.context = NullCheck.notNull(in_context);
    this.containers = new ConcurrentHashMap<>();
  }

  private static Container loadFromFile(
      final Context ctx,
      final ReaderReadiumEPUBLoadRequest request)
      throws IOException {

    /*
     * Readium will happily segfault if passed a filename that does not refer
     * to a file that exists.
     */

    if (!request.epubFile().isFile()) {
      throw new FileNotFoundException("No such file");
    }

    /*
     * If Adobe rights exist for the given book, then those rights must
     * be read so that they can be fed to the content filter plugin. If
     * no rights file exists, it may either be that the file has been lost
     * or that the book is not encrypted. If the book actually is encrypted
     * and there is no rights information, then unfortunately there is
     * nothing that can be done about this. This is not something that should
     * happen in practice and likely indicates database tampering or a bug
     * in the program.
     */

    final byte[] adobe_rights_data =
        request.adobeRightsFile().acceptPartial(
            new OptionPartialVisitorType<File, byte[], IOException>() {
              @Override
              public byte[] none(final None<File> none) {
                LOG.debug("No Adobe rights data exists");
                return new byte[0];
              }

              @Override
              public byte[] some(final Some<File> some) throws IOException {
                LOG.debug("Adobe rights data exists, loading it");
                final File adobe_rights_file = some.get();
                final byte[] data = FileUtilities.fileReadBytes(adobe_rights_file);
                LOG.debug("Loaded {} bytes of Adobe rights data", data.length);
                return data;
              }
            });

    /*
     * The majority of logged messages will be useless noise.
     */

    final SdkErrorHandler errors = (message, is_severe) -> {
      LOG.debug("{}", message);
      if (is_severe) {
        IfBugsnag.get().notify(new ReaderReadiumSdkException(message), Severity.ERROR);
      }
      return true;
    };

    /*
     * The Readium SDK will call the given filter handler when the
     * filter chain has been populated. It is at this point that it
     * is necessary to register the Adobe content filter plugin, if
     * one is to be used. The plugin will call the given rights client
     * every time it needs to load rights data (which will only be once,
     * given the way that the application creates a new instance of
     * Readium each time a book is opened).
     */

    final AdobeAdeptContentRightsClientType rights_client = path -> {
      LOG.debug("returning {} bytes of rights data for path {}", adobe_rights_data.length, path);
      return adobe_rights_data;
    };

    final Runnable filter_handler = () -> {
      LOG.debug("Registering content filter");

      try {
        final AdobeAdeptContentFilterType adobe =
            AdobeDRMServices.newAdobeContentFilter(
                ctx, AdobeDRMServices.getPackageOverride(ctx.getResources()));
        adobe.registerFilter(rights_client);
        LOG.debug("Content filter registered");
      } catch (final DRMUnsupportedException e) {
        LOG.error("DRM is not supported: ", e);
      } catch (final DRMException e) {
        LOG.error("DRM could not be initialized: ", e);
      }
    };

    EPub3.setSdkErrorHandler(errors);
    EPub3.setContentFiltersRegistrationHandler(filter_handler);
    final Container c = EPub3.openBook(request.epubFile().toString());
    EPub3.setSdkErrorHandler(null);

    /*
     * Only the default package is considered important. If the package has no
     * spine items, then the package is considered to be unusable.
     */

    final Package p = c.getDefaultPackage();
    if (p.getSpineItems().isEmpty()) {
      throw new IOException("Loaded package had no spine items");
    }
    return c;
  }

  /**
   * Construct a new EPUB loader.
   *
   * @param in_context The application context
   * @param in_exec    An executor service
   * @return A new EPUB loader
   */

  public static ReaderReadiumEPUBLoaderType newLoader(
      final Context in_context,
      final ExecutorService in_exec) {
    return new ReaderReadiumEPUBLoader(in_context, in_exec);
  }

  @Override
  public void loadEPUB(
      final ReaderReadiumEPUBLoadRequest request,
      final ReaderReadiumEPUBLoadListenerType l) {

    NullCheck.notNull(request);
    NullCheck.notNull(l);

    /*
     * This loader caches references to loaded containers. It's not actually
     * expected that there will be more than one container for the lifetime of
     * the process.
     */

    final ConcurrentHashMap<File, Container> cs = this.containers;
    this.exec.submit(() -> {
      try {
        final Container c;
        if (cs.containsKey(request.epubFile())) {
          c = NullCheck.notNull(cs.get(request.epubFile()));
        } else {
          c = ReaderReadiumEPUBLoader.loadFromFile(this.context, request);
          cs.put(request.epubFile(), c);
        }

        l.onEPUBLoadSucceeded(c);
      } catch (final Throwable x0) {
        try {
          l.onEPUBLoadFailed(x0);
        } catch (final Throwable x1) {
          LOG.error("{}", x1.getMessage(), x1);
        }
      }
    });
  }

  private static abstract class ReaderReadiumRuntimeException extends Exception {
    ReaderReadiumRuntimeException(final String in_message) {
      super(in_message);
    }
  }

  private static final class ReaderReadiumSdkException extends ReaderReadiumRuntimeException {
    ReaderReadiumSdkException(final String in_message) {
      super(in_message);
    }
  }

  private static final class ReaderReadiumContentFilterException extends ReaderReadiumRuntimeException {
    ReaderReadiumContentFilterException(final String in_message) {
      super(in_message);
    }
  }
}
