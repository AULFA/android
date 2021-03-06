package org.nypl.simplified.books.profiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.io7m.jfunctional.None;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.OptionVisitorType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnreachableCodeException;

import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;
import org.nypl.simplified.assertions.Assertions;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.books.accounts.AccountBundledCredentialsType;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.accounts.AccountProviderCollectionType;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.accounts.AccountsDatabaseException;
import org.nypl.simplified.books.accounts.AccountsDatabaseFactoryType;
import org.nypl.simplified.books.accounts.AccountsDatabaseNonexistentException;
import org.nypl.simplified.books.accounts.AccountsDatabaseType;
import org.nypl.simplified.books.analytics.AnalyticsLogger;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.files.DirectoryUtilities;
import org.nypl.simplified.files.FileLocking;
import org.nypl.simplified.files.FileUtilities;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.annotation.concurrent.GuardedBy;

import static org.nypl.simplified.books.profiles.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_DISABLED;
import static org.nypl.simplified.books.profiles.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_ENABLED;

/**
 * The default implementation of the {@link ProfilesDatabaseType} interface.
 */

public final class ProfilesDatabase implements ProfilesDatabaseType {

  private static final Logger LOG = LogUtilities.getLog(ProfilesDatabase.class);

  private static final ProfileID ANONYMOUS_PROFILE_ID = ProfileID.create(0);

  private final File directory;
  private final ConcurrentSkipListMap<ProfileID, Profile> profiles;
  private final SortedMap<ProfileID, ProfileType> profiles_read;
  private final AnonymousProfileEnabled profile_anon_enabled;
  private final AccountsDatabaseFactoryType accounts_databases;
  private final Object profile_current_lock;
  private final AnalyticsLogger analytics;
  private final AccountProviderCollectionType account_providers;
  private final AccountBundledCredentialsType account_bundled_credentials;
  private @GuardedBy("profile_current_lock")
  ProfileID profile_current;

  private ProfilesDatabase(
    final AnalyticsLogger in_analytics,
    final AccountProviderCollectionType account_providers,
    final AccountBundledCredentialsType account_bundled_credentials,
    final AccountsDatabaseFactoryType accounts_databases,
    final File directory,
    final ConcurrentSkipListMap<ProfileID, Profile> profiles,
    final AnonymousProfileEnabled anonymous_enabled) {

    this.analytics =
      Objects.requireNonNull(in_analytics, "analytics");
    this.account_providers =
      Objects.requireNonNull(account_providers, "Account providers");
    this.account_bundled_credentials =
      Objects.requireNonNull(account_bundled_credentials, "Account bundled credentials");
    this.accounts_databases =
      Objects.requireNonNull(accounts_databases, "Accounts databases");
    this.directory =
      Objects.requireNonNull(directory, "directory");
    this.profiles =
      Objects.requireNonNull(profiles, "profiles");
    this.profile_anon_enabled =
      Objects.requireNonNull(anonymous_enabled, "Anonymous enabled");

    this.profiles_read = castMap(Collections.unmodifiableSortedMap(this.profiles));
    this.profile_current_lock = new Object();
    this.profile_current = null;

    for (final Profile profile : this.profiles.values()) {
      profile.setOwner(this);
    }
  }

  /**
   * Perform an unchecked (but safe) cast of the given map type. The cast is safe because
   * {@code V <: VB}.
   */

  @SuppressWarnings("unchecked")
  private static <K, VB, V extends VB> SortedMap<K, VB> castMap(final SortedMap<K, V> m) {
    return (SortedMap<K, VB>) m;
  }

  /**
   * Open a profile database from the given directory, creating a new database if one does not
   * exist. The anonymous account will not be enabled, and will be ignored even if one is present
   * in the on-disk database.
   *
   * @param account_providers           The available account providers
   * @param account_bundled_credentials The bundled account credentials
   * @param directory                   The directory
   * @return A profile database
   * @throws ProfileDatabaseException If any errors occurred whilst trying to open the database
   */

  public static ProfilesDatabaseType openWithAnonymousAccountDisabled(
    final AnalyticsLogger in_analytics,
    final AccountProviderCollectionType account_providers,
    final AccountBundledCredentialsType account_bundled_credentials,
    final AccountsDatabaseFactoryType accounts_databases,
    final File directory)
    throws ProfileDatabaseException {

    Objects.requireNonNull(in_analytics, "in_analytics");
    Objects.requireNonNull(account_providers, "Account providers");
    Objects.requireNonNull(account_bundled_credentials, "Account bundled credentials");
    Objects.requireNonNull(accounts_databases, "Accounts databases");
    Objects.requireNonNull(directory, "Directory");

    LOG.debug("opening profile database: {}", directory);

    final ConcurrentSkipListMap<ProfileID, Profile> profiles = new ConcurrentSkipListMap<>();
    final ObjectMapper jom = new ObjectMapper();

    final List<Exception> errors = new ArrayList<>();
    openAllProfiles(
      account_providers,
      accounts_databases,
      account_bundled_credentials,
      directory,
      profiles,
      jom,
      errors);
    profiles.remove(ANONYMOUS_PROFILE_ID);

    if (!errors.isEmpty()) {
      for (final Exception e : errors) {
        LOG.error("error during profile database open: ", e);
      }

      throw new ProfileDatabaseOpenException(
        "One or more errors occurred whilst trying to open the profile database.",
        errors);
    }

    return new ProfilesDatabase(
      in_analytics,
      account_providers,
      account_bundled_credentials,
      accounts_databases,
      directory,
      profiles,
      ANONYMOUS_PROFILE_DISABLED);
  }

  private static void openAllProfiles(
    final AccountProviderCollectionType account_providers,
    final AccountsDatabaseFactoryType accounts_databases,
    final AccountBundledCredentialsType account_bundled_credentials,
    final File directory,
    final SortedMap<ProfileID, Profile> profiles,
    final ObjectMapper jom,
    final List<Exception> errors) {

    if (!directory.exists()) {
      directory.mkdirs();
    }

    if (!directory.isDirectory()) {
      errors.add(new IOException("Not a directory: " + directory));
    }

    final String[] profile_dirs = directory.list();
    if (profile_dirs != null) {
      for (final String profile_id_name : profile_dirs) {
        LOG.debug("opening profile: {}/{}", directory, profile_id_name);
        final Profile profile =
          openOneProfile(
            account_providers,
            accounts_databases,
            account_bundled_credentials,
            jom,
            directory,
            errors,
            profile_id_name);

        if (profile == null) {
          continue;
        }
        profiles.put(profile.id, profile);
      }
    }
  }

  /**
   * Open a profile database from the given directory, creating a new database if one does not exist.
   * The anonymous account will be enabled and will use the given account provider as the default
   * account.
   *
   * @param account_providers           The available account providers
   * @param account_bundled_credentials The bundled account credentials
   * @param account_provider            The account provider that will be used for the anonymous account
   * @param directory                   The directory
   * @return A profile database
   * @throws ProfileDatabaseException If any errors occurred whilst trying to open the database
   */

  public static ProfilesDatabaseType openWithAnonymousAccountEnabled(
    final AnalyticsLogger in_analytics,
    final AccountProviderCollectionType account_providers,
    final AccountBundledCredentialsType account_bundled_credentials,
    final AccountsDatabaseFactoryType accounts_databases,
    final AccountProvider account_provider,
    final File directory)
    throws ProfileDatabaseException {

    Objects.requireNonNull(in_analytics, "in_analytics");
    Objects.requireNonNull(account_providers, "Account providers");
    Objects.requireNonNull(account_bundled_credentials, "Account bundled credentials");
    Objects.requireNonNull(accounts_databases, "Accounts databases");
    Objects.requireNonNull(account_provider, "Account provider");
    Objects.requireNonNull(directory, "Directory");

    LOG.debug("opening profile database: {}", directory);

    final ConcurrentSkipListMap<ProfileID, Profile> profiles = new ConcurrentSkipListMap<>();
    final ObjectMapper jom = new ObjectMapper();

    final List<Exception> errors = new ArrayList<>();
    openAllProfiles(
      account_providers,
      accounts_databases,
      account_bundled_credentials,
      directory,
      profiles,
      jom,
      errors);

    if (!profiles.containsKey(ANONYMOUS_PROFILE_ID)) {
      final Profile anon =
        createProfileActual(
          account_providers,
          account_bundled_credentials,
          accounts_databases,
          account_provider,
          directory,
          "",
          ANONYMOUS_PROFILE_ID);
      profiles.put(ANONYMOUS_PROFILE_ID, anon);
    }

    if (!errors.isEmpty()) {
      throw new ProfileDatabaseOpenException(
        "One or more errors occurred whilst trying to open the profile database.", errors);
    }

    final ProfilesDatabase database =
      new ProfilesDatabase(
        in_analytics,
        account_providers,
        account_bundled_credentials,
        accounts_databases,
        directory,
        profiles,
        ANONYMOUS_PROFILE_ENABLED);

    database.setCurrentProfile(ANONYMOUS_PROFILE_ID);
    return database;
  }

  private static @Nullable
  Profile openOneProfile(
    final AccountProviderCollectionType account_providers,
    final AccountsDatabaseFactoryType accounts_databases,
    final AccountBundledCredentialsType account_bundled_credentials,
    final ObjectMapper jom,
    final File directory,
    final List<Exception> errors,
    final String profile_id_name) {

    final int id;
    try {
      id = Integer.parseInt(profile_id_name);
    } catch (final NumberFormatException e) {
      errors.add(new IOException("Could not parse directory name as profile ID", e));
      return null;
    }

    final File profile_dir = new File(directory, profile_id_name);
    final File profile_file = new File(profile_dir, "profile.json");
    if (!profile_file.isFile()) {
      LOG.error("[{}]: {} is not a file", id, profile_file);
      return null;
    }

    final ProfileDescription desc;
    try {
      desc = ProfileDescriptionJSON.deserializeFromFile(jom, profile_file);
    } catch (final IOException e) {
      errors.add(new IOException("Could not parse profile: " + profile_file, e));
      return null;
    }

    final ProfileID profile_id = ProfileID.create(id);
    final File profile_accounts_dir = new File(profile_dir, "accounts");

    try {
      final AccountsDatabaseType accounts =
        accounts_databases.openDatabase(account_providers, profile_accounts_dir);

      createAutomaticAccounts(profile_id, account_providers, account_bundled_credentials, accounts);

      if (accounts.accounts().isEmpty()) {
        LOG.debug("profile is empty, creating a default account");
        accounts.createAccount(account_providers.providerDefault());
      }

      Assertions.checkPrecondition(
        !accounts.accounts().isEmpty(),
        "Accounts database must not be empty");

      final AccountType account = accounts.accounts().get(accounts.accounts().firstKey());
      return new Profile(null, profile_id, profile_dir, desc, accounts, account);
    } catch (final AccountsDatabaseException e) {
      errors.add(e);
      return null;
    }
  }

  @Override
  public AnonymousProfileEnabled anonymousProfileEnabled() {
    return this.profile_anon_enabled;
  }

  @Override
  public ProfileType anonymousProfile() throws ProfileAnonymousDisabledException {
    switch (this.profile_anon_enabled) {
      case ANONYMOUS_PROFILE_ENABLED:
        return this.profiles.get(ANONYMOUS_PROFILE_ID);
      case ANONYMOUS_PROFILE_DISABLED:
        throw new ProfileAnonymousDisabledException("The anonymous profile is not enabled");
    }
    throw new UnreachableCodeException();
  }

  @Override
  public File directory() {
    return this.directory;
  }

  @Override
  public SortedMap<ProfileID, ProfileType> profiles() {
    return this.profiles_read;
  }

  @Override
  public ProfileType createProfile(
    final AccountProvider account_provider,
    final String raw_name)
    throws ProfileDatabaseException {

    Objects.requireNonNull(account_provider, "Provider");
    final String display_name = normalizeDisplayName(raw_name);

    if (display_name.isEmpty()) {
      throw new ProfileCreateInvalidException("Display name cannot be empty");
    }

    final OptionType<ProfileType> existing = findProfileWithDisplayName(display_name);
    if (existing.isSome()) {
      throw new ProfileCreateDuplicateException("Display name is already used by an existing profile");
    }

    final ProfileID next;
    if (!this.profiles.isEmpty()) {
      next = ProfileID.create(this.profiles.lastKey().id() + 1);
    } else {
      next = ProfileID.create(1);
    }

    Assertions.checkInvariant(
      !this.profiles.containsKey(next),
      "Profile ID %s cannot have been used", next);

    final Profile profile =
      createProfileActual(
        this.account_providers,
        this.account_bundled_credentials,
        this.accounts_databases,
        account_provider,
        this.directory,
        display_name,
        next);

    this.profiles.put(profile.id(), profile);
    profile.setOwner(this);

    logProfileCreated(analytics, profile);
    return profile;
  }

  private static void logProfileCreated(AnalyticsLogger analytics, Profile profile) {
    final ProfilePreferences prefs = profile.preferences();
    final StringBuilder eventBuilder = new StringBuilder(128);
    eventBuilder.append("profile_created,");
    eventBuilder.append(profile.id().id());
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(profile.displayName()));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmpty(prefs.gender())));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmptyDate(prefs.dateOfBirth())));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmpty(prefs.role())));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmpty(prefs.school())));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmpty(prefs.grade())));
    analytics.logToAnalytics(eventBuilder.toString());
  }

  private static void logProfileModified(AnalyticsLogger analytics, Profile profile) {
    final ProfilePreferences prefs = profile.preferences();
    final StringBuilder eventBuilder = new StringBuilder(128);
    eventBuilder.append("profile_modified,");
    eventBuilder.append(profile.id().id());
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(profile.displayName()));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmpty(prefs.gender())));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmptyDate(prefs.dateOfBirth())));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmpty(prefs.role())));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmpty(prefs.school())));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmpty(prefs.grade())));
    analytics.logToAnalytics(eventBuilder.toString());
  }

  private static void logProfileDeleted(AnalyticsLogger analytics, Profile profile) {
    final ProfilePreferences prefs = profile.preferences();
    final StringBuilder eventBuilder = new StringBuilder(128);
    eventBuilder.append("profile_deleted,");
    eventBuilder.append(profile.id().id());
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(profile.displayName()));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmpty(prefs.gender())));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmptyDate(prefs.dateOfBirth())));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmpty(prefs.role())));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmpty(prefs.school())));
    eventBuilder.append(',');
    eventBuilder.append(scrubCommas(orEmpty(prefs.grade())));
    analytics.logToAnalytics(eventBuilder.toString());
  }

  private static String orEmptyDate(OptionType<LocalDate> opt)
  {
    return opt.accept(new OptionVisitorType<LocalDate, String>() {
      @Override
      public String none(None<LocalDate> n) {
        return "";
      }

      @Override
      public String some(Some<LocalDate> s) {
        return ISODateTimeFormat.basicDate().print(s.get());
      }
    });
  }

  private static String orEmpty(OptionType<String> opt)
  {
    return opt.accept(new OptionVisitorType<String, String>() {
      @Override
      public String none(None<String> n) {
        return "";
      }

      @Override
      public String some(Some<String> s) {
        return s.get();
      }
    });
  }

  private static String scrubCommas(
    final String text)
  {
    return text.replace(",", "");
  }

  /**
   * Do the actual work of creating the profile.
   *
   * @param account_providers           The available account providers
   * @param account_bundled_credentials The bundled credentials
   * @param accounts_databases          A factory for account databases
   * @param account_provider            The account provider that will be used for the default account
   * @param directory                   The profile directory
   * @param raw_name                    The display name for the account
   * @param id                          The account ID
   */

  private static Profile createProfileActual(
    final AccountProviderCollectionType account_providers,
    final AccountBundledCredentialsType account_bundled_credentials,
    final AccountsDatabaseFactoryType accounts_databases,
    final AccountProvider account_provider,
    final File directory,
    final String raw_name,
    final ProfileID id)
    throws ProfileDatabaseException {

    try {
      final String display_name = normalizeDisplayName(raw_name);

      final File profile_dir =
        new File(directory, Integer.toString(id.id()));
      final File profile_accounts_dir =
        new File(profile_dir, "accounts");

      final ProfilePreferences prefs =
        ProfilePreferences.builder()
          .build();

      final ProfileDescription desc =
        ProfileDescription.builder(display_name, prefs)
          .build();

      try {
        final AccountsDatabaseType accounts =
          accounts_databases.openDatabase(account_providers, profile_accounts_dir);

        createAutomaticAccounts(id, account_providers, account_bundled_credentials, accounts);

        final AccountType account;
        final SortedMap<URI, AccountType> by_provider = accounts.accountsByProvider();
        if (by_provider.containsKey(account_provider.id())) {
          account = by_provider.get(account_provider.id());
        } else {
          LOG.debug("[{}]: creating default account: {}", id.id(), account_provider.id());
          account = accounts.createAccount(account_provider);
        }

        final OptionType<AccountAuthenticationCredentials> credentials_opt =
          account_bundled_credentials.bundledCredentialsFor(account_provider.id());
        account.setCredentials(credentials_opt);

        final Profile profile =
          new Profile(null, id, profile_dir, desc, accounts, account);

        writeDescription(profile_dir, desc);
        return profile;
      } catch (final AccountsDatabaseException e) {
        throw new ProfileDatabaseAccountsException("Could not initialize accounts database", e);
      }
    } catch (final IOException e) {
      throw new ProfileDatabaseIOException("Could not write profile data", e);
    }
  }

  /**
   * Create an account for all of the providers that are marked as "add automatically".
   */

  private static void createAutomaticAccounts(
    final ProfileID profile,
    final AccountProviderCollectionType account_providers,
    final AccountBundledCredentialsType account_bundled_credentials,
    final AccountsDatabaseType accounts) throws AccountsDatabaseException {

    LOG.debug("[{}]: creating automatic accounts", profile.id());

    for (final AccountProvider auto_provider : account_providers.providers().values()) {
      if (auto_provider.addAutomatically()) {
        final URI auto_provider_id = auto_provider.id();
        LOG.debug(
          "[{}]: account provider {} should be added automatically",
          profile.id(),
          auto_provider_id);

        AccountType auto_account = accounts.accountsByProvider().get(auto_provider_id);
        if (auto_account != null) {
          LOG.debug("[{}]: automatic account {} already exists", profile.id(), auto_provider_id);
        } else {
          LOG.debug("[{}]: adding automatic account {}", profile.id(), auto_provider_id);
          auto_account = accounts.createAccount(auto_provider);
        }

        final OptionType<AccountAuthenticationCredentials> credentials_opt =
          account_bundled_credentials.bundledCredentialsFor(auto_provider.id());
        if (credentials_opt.isSome()) {
          LOG.debug("[{}]: credentials for automatic account {} were provided",
            profile.id(), auto_provider_id);
        } else {
          LOG.debug("[{}]: credentials for automatic account {} were not provided",
            profile.id(), auto_provider_id);
        }

        auto_account.setCredentials(credentials_opt);
      }
    }
  }

  @Override
  public OptionType<ProfileType> findProfileWithDisplayName(
    final String raw_name) {

    final String display_name = normalizeDisplayName(raw_name);
    for (final Profile profile : this.profiles.values()) {
      if (profile.displayName().equals(display_name)) {
        return Option.some(profile);
      }
    }
    return Option.none();
  }

  @Override
  public void setProfileCurrent(
    final ProfileID profile)
    throws ProfileNonexistentException, ProfileAnonymousEnabledException {

    Objects.requireNonNull(profile, "Profile");

    switch (this.profile_anon_enabled) {
      case ANONYMOUS_PROFILE_ENABLED: {
        throw new ProfileAnonymousEnabledException(
          "The anonymous profile is enabled; cannot set the current profile");
      }
      case ANONYMOUS_PROFILE_DISABLED: {
        if (!profiles.containsKey(profile)) {
          throw new ProfileNonexistentException("Profile does not exist");
        }

        setCurrentProfile(profile);
        break;
      }
    }
  }

  private void setCurrentProfile(final ProfileID profile) {
    LOG.debug("setCurrentProfile: {}", profile);
    synchronized (this.profile_current_lock) {
      this.profile_current = Objects.requireNonNull(profile, "Profile");
    }
  }

  @Override
  public OptionType<ProfileType> currentProfile() {
    synchronized (this.profile_current_lock) {
      switch (this.profile_anon_enabled) {
        case ANONYMOUS_PROFILE_ENABLED: {
          try {
            return Option.some(this.anonymousProfile());
          } catch (final ProfileAnonymousDisabledException e) {
            throw new UnreachableCodeException(e);
          }
        }
        case ANONYMOUS_PROFILE_DISABLED: {
          return Option.of(this.profile_current).map(profiles::get);
        }
      }

      throw new UnreachableCodeException();
    }
  }

  @Override
  public ProfileType currentProfileUnsafe() throws ProfileNoneCurrentException {
    return currentProfileGet();
  }

  private Profile currentProfileGet() throws ProfileNoneCurrentException {
    synchronized (this.profile_current_lock) {
      final ProfileID id = this.profile_current;
      if (id != null) {
        return this.profiles.get(Objects.requireNonNull(id, "ID"));
      }
      throw new ProfileNoneCurrentException("No profile is current");
    }
  }

  private static String normalizeDisplayName(String name) {
    return Objects.requireNonNull(name, "Display name").trim();
  }

  private static final class Profile implements ProfileType {

    private final Object description_lock;
    private @GuardedBy("description_lock")
    ProfileDescription description;

    private final Object account_current_lock;
    private @GuardedBy("account_current_lock")
    AccountType account_current;

    private ProfilesDatabase owner;
    private final AccountsDatabaseType accounts;
    private final ProfileID id;
    private final File directory;

    private Profile(
      final @Nullable ProfilesDatabase in_owner,
      final ProfileID in_id,
      final File in_directory,
      final ProfileDescription in_description,
      final AccountsDatabaseType in_accounts,
      final AccountType in_account_current) {

      this.id =
        Objects.requireNonNull(in_id, "id");
      this.directory =
        Objects.requireNonNull(in_directory, "directory");
      this.description =
        Objects.requireNonNull(in_description, "description");
      this.accounts =
        Objects.requireNonNull(in_accounts, "accounts");
      this.account_current =
        Objects.requireNonNull(in_account_current, "account_current");

      this.account_current_lock = new Object();
      this.description_lock = new Object();
      this.owner = in_owner;
    }

    private void setOwner(final ProfilesDatabase owner) {
      this.owner = Objects.requireNonNull(owner, "Owner");
    }

    @Override
    public ProfileID id() {
      return this.id;
    }

    @Override
    public boolean isAnonymous() {
      return this.id.equals(ANONYMOUS_PROFILE_ID);
    }

    @Override
    public File directory() {
      return this.directory;
    }

    @Override
    public String displayName() {
      synchronized (this.description_lock) {
        return this.description.displayName();
      }
    }

    @Override
    public boolean isCurrent() {
      synchronized (this.owner.profile_current_lock) {
        return this.id.equals(this.owner.profile_current);
      }
    }

    @Override
    public AccountType accountCurrent() {
      synchronized (this.account_current_lock) {
        return this.account_current;
      }
    }

    @Override
    public SortedMap<AccountID, AccountType> accounts() {
      return this.accounts.accounts();
    }

    @Override
    public ProfilePreferences preferences() {
      synchronized (this.description_lock) {
        return this.description.preferences();
      }
    }

    @Override
    public SortedMap<URI, AccountType> accountsByProvider() {
      return this.accounts.accountsByProvider();
    }

    @Override
    public AccountType account(final AccountID account_id)
      throws AccountsDatabaseNonexistentException {

      final AccountType account =
        this.accounts().get(Objects.requireNonNull(account_id, "Account ID"));

      if (account == null) {
        throw new AccountsDatabaseNonexistentException("Nonexistent account: " + account_id.id());
      }

      return account;
    }

    @Override
    public void delete() throws ProfileDatabaseException, IOException {
      LOG.debug("[{}]: delete", this.id.id());

      if (this.isAnonymous()) {
        throw new ProfileDatabaseDeleteAnonymousException("Cannot delete the anonymous profile");
      }

      this.owner.deleteProfile(this);
    }

    @Override
    public void setDisplayName(final String rawName)
      throws ProfileDatabaseException, IOException {

      final String newNameNormal = normalizeDisplayName(rawName);
      synchronized (this.description_lock) {
        final OptionType<ProfileType> existing =
          this.owner.findProfileWithDisplayName(newNameNormal);

        /*
         * If a profile exists with the given name, and it's not this profile... Abort!
         */

        if (existing.isSome()) {
          if (!existing.equals(Option.of(this))) {
            throw new ProfileCreateDuplicateException(
              "A profile already exists with the name '" + newNameNormal + "'");
          }
        }

        ProfileDescription new_desc =
          this.description.toBuilder()
            .setDisplayName(newNameNormal)
            .build();

        writeDescription(this.directory, new_desc);
        this.description = new_desc;
      }

      logProfileModified(this.owner.analytics, this);
    }

    @Override
    public AccountsDatabaseType accountsDatabase() {
      return this.accounts;
    }

    @Override
    public void preferencesUpdate(final ProfilePreferences preferences)
      throws IOException {

      Objects.requireNonNull(preferences, "Preferences");

      final ProfileDescription new_desc;
      synchronized (this.description_lock) {
        new_desc =
          this.description.toBuilder()
            .setPreferences(preferences)
            .build();

        writeDescription(this.directory, new_desc);
        this.description = new_desc;
      }

      logProfileModified(this.owner.analytics, this);
    }

    @Override
    public AccountType createAccount(final AccountProvider account_provider)
      throws AccountsDatabaseException {

      Objects.requireNonNull(account_provider, "Account provider");
      return this.accounts.createAccount(account_provider);
    }

    @Override
    public AccountID deleteAccountByProvider(final AccountProvider account_provider)
      throws AccountsDatabaseException {

      Objects.requireNonNull(account_provider, "Account provider");
      final AccountID deleted = this.accounts.deleteAccountByProvider(account_provider);

      synchronized (this.account_current_lock) {
        if (this.account_current.id().equals(deleted)) {
          this.account_current = Objects.requireNonNull(this.accounts().get(accounts().firstKey()));
        }
        return deleted;
      }
    }

    @Override
    public AccountType selectAccount(final AccountProvider account_provider)
      throws AccountsDatabaseNonexistentException {

      Objects.requireNonNull(account_provider, "Account provider");
      final AccountType account = this.accounts.accountsByProvider().get(account_provider.id());
      if (account != null) {
        setAccountCurrent(account.id());
        return account;
      }

      throw new AccountsDatabaseNonexistentException(
        "No account with provider: " + account_provider.id());
    }

    @Override
    public int compareTo(final ProfileReadableType other) {
      return this.displayName().compareTo(Objects.requireNonNull(other, "Other").displayName());
    }

    void setAccountCurrent(final AccountID id)
      throws AccountsDatabaseNonexistentException {

      Objects.requireNonNull(id, "ID");
      synchronized (this.account_current_lock) {
        final AccountType account = this.accounts.accounts().get(id);
        if (account != null) {
          this.account_current = account;
        } else {
          throw new AccountsDatabaseNonexistentException("No such account: " + id.id());
        }
      }
    }
  }

  private void deleteProfile(final Profile profile)
    throws IOException {

    synchronized (this.profile_current_lock) {
      this.profiles.remove(profile.id);
      if (this.profile_current == profile.id) {
        this.profile_current = null;
      }

      DirectoryUtilities.directoryDelete(profile.directory);
    }

    logProfileDeleted(this.analytics, profile);
  }

  private static void writeDescription(
    final File directory,
    final ProfileDescription new_desc)
    throws IOException {

    final File profile_lock =
      new File(directory, "lock");
    final File profile_file =
      new File(directory, "profile.json");
    final File profile_file_tmp =
      new File(directory, "profile.json.tmp");

    FileLocking.withFileThreadLocked(
      profile_lock,
      1000L,
      ignored -> {

        /*
         * Ignore the return value here; the write call will immediately fail if this
         * call fails anyway.
         */

        directory.mkdirs();

        FileUtilities.fileWriteUTF8Atomically(
          profile_file,
          profile_file_tmp,
          ProfileDescriptionJSON.serializeToString(new ObjectMapper(), new_desc));
        return Unit.unit();
      });
  }
}
