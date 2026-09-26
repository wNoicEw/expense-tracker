# Changelog

All notable changes to **Money Tracker (Offline AI Expense Tracker & Financial Intelligence)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.8.0] - 2026-09-26

### Added - Personal Transactions, Friend Profiles Hub & P2P Balance Ledgers
- **Dedicated Personal Transactions Hub (`FriendsManager` & `panel-personal`)**:
  - Introduced a dedicated **Personal Transactions** navigation tab allowing users to track peer-to-peer spending, splitting, and settlement balances with friends, colleagues, and UPI counterparties.
  - Added new financial category **"Friend"** (`cat_friend`, `#a855f7`, `users` icon) with automatic seeding across new and existing profiles.
  - Selecting "Friend" on any transaction automatically groups and creates a dedicated Friend Profile using that transaction's counterparty UPI ID, account details, or extracted person name.
- **Smart Counterparty Name & UPI Identification**:
  - Automatically parses and extracts clean counterparty names from complex bank and UPI narrations (e.g. `UPI/DR/.../NAME/...`, `Paid to NAME`, `Received from NAME`, `IMPS/.../NAME`, and handle extraction).
  - Normalizes UPI addresses and formats clean titles without boilerplate keywords or banking codes.
- **Real-Time Peer-to-Peer Balance Math**:
  - Dynamically calculates the live net balance for each friend profile:
    - `Outgoing (Sent)`: Transactions where user sent money (expense or outgoing transfer).
    - `Incoming (Received)`: Transactions where friend sent money to user (income, refund, or incoming transfer).
    - `Net Balance = Outgoing - Incoming`.
  - Intuitive status badges:
    - **"You Get +₹X"**: Friend owes user (emerald green pill).
    - **"You Owe -₹Y"**: User owes friend (rose crimson pill).
    - **"Settled Up"**: Zero balance (slate pill).
- **Executive Bento KPI Summary**:
  - Summary cards displaying *Friends Tracked*, *You Get (Total to Receive)*, *You Owe (Total to Pay)*, and *Net Position* (overall peer surplus/deficit).
  - Filter chips for *All Friends*, *You Get*, *You Owe*, and *Settled*, alongside real-time search by friend name or UPI ID.
- **Friend Detail Ledger View**:
  - Full-detail view opening upon tapping any friend profile card.
  - Complete chronological transaction ledger with flow indicators, exact amount, date, time, description, payment mode, and reference UTR.
  - Individual transaction editing and deletion directly from the friend ledger with instant balance re-calculation.
- **Friend Profile Renaming & Safe Deletion**:
  - **Inline Profile Renaming (`setFriendCustomName`)**: Allows users to assign a friendly nickname or verified name to any UPI ID / friend profile, persisted locally.
  - **Safe Deletion Dialog (`deleteFriendModal`)**: Offers two distinct, clear options:
    - *Option 1 (Remove Profile Only)*: Re-categorizes friend transactions to "Miscellaneous", removing the profile from Personal Transactions while preserving 100% of accounting, statement records, and account balances.
    - *Option 2 (Delete Profile & All Transactions)*: Permanently purges all associated transactions from the ledger.
- **Zero Impact on Main Calculations**:
  - Personal transactions are completely integrated as standard transactions in the primary database, ensuring dashboard KPIs, bank balances, statements, duplicate detection, and cashflow charts remain 100% accurate and mathematically unaltered.
- **Direct APK Distribution**:
  - Pruned oldest fallback version `ExpenseTracker-v1.5.1.apk` per 5-version retention policy.
  - Archived `ExpenseTracker-v1.8.0.apk` in `apks/` and updated root `ExpenseTracker.apk`.

## [1.7.0] - 2026-09-22

### Added - Groww-Style Ledger Filter Hub, Multi-Sort Sheet & Long-Press Multi-Select Deletion
- **Executive Ledger Filter Architecture (`LedgerFilterSheet`)**:
  - Replaced the bulky 4-row stacked horizontal filter chips in the Financial Ledger with a compact single-line toolbar inspired by modern financial apps like Groww.
  - Dedicated "Filter" button with an active filter badge counter that launches an executive split-pane modal bottom sheet:
    - **Category Navigation Rail**: Left sidebar (`Type`, `Category`, `Account`, `Currency`, `Status`) with dynamic badges indicating active selections per category.
    - **Multi-Select Attribute Pane**: Right pane with high-touch checkbox items for multi-category filtering across transaction types, 12+ categories, linked accounts, supported currencies, and review flags.
    - **Header & Footer CTAs**: "Clear all" action in the header to reset filters at once, paired with a sticky bottom CTA button showing live matching transactions (`View X Transactions`).
- **Dedicated Sorting Bottom Sheet (`LedgerSortSheet`)**:
  - Added a Groww-style "Sort by ▾" pill button opening a focused bottom sheet with clear options:
    - *Newest to Oldest* (Default chronological)
    - *Oldest to Newest*
    - *Highest Amount to Lowest*
    - *Lowest to Highest Amount*
  - Instant selection dismiss with checkmark indicator and real-time ledger list re-sorting.
- **Active Filter Dismiss Pills (`LedgerFilterSortBar`)**:
  - Dynamically displays removable filter chips (`Expenses ✕`, `Food & Dining ✕`, `INR ✕`, `Needs Review ✕`) next to the sort button for immediate visibility and 1-tap dismiss.
  - Includes a quick-access "Review (N)" chip when unreviewed transactions are detected.
- **Long-Press Multi-Selection & Bulk Deletion**:
  - Long-pressing any transaction in the ledger initiates multi-selection mode with tactile haptic feedback.
  - **Contextual Action Bar (CAB)**: Replaces top header during selection with cancel (`✕`), live selected count (`X selected`), a 1-tap "Select All" / "Deselect All" button, and bulk delete action.
  - **Visual Selection Feedback**: Inline animated Checkbox on the left of each row and illuminated primary tint with active border stroke for selected rows.
  - **Safe Bulk Deletion**: Added Room DAO query `deleteTransactionsByIds` with an irreversible deletion confirmation dialog.
  - Back handler support automatically exits selection mode when the user taps Android system back.
- **APK Distribution**:
  - Pruned oldest fallback version `ExpenseTracker-v1.5.0.apk` per 5-version retention policy.
  - Archived `ExpenseTracker-v1.7.0.apk` in `apks/` and updated main `ExpenseTracker.apk`.

## [1.6.2] - 2026-09-22

### Fixed - Universal Statement Transaction Time Extraction & Precision Timestamp Engine
- **Transaction Time Detection & Precision Timestamp Parsing (Android & Web)**:
  - Fixed an issue where statements containing explicit transaction times (e.g., Google Pay statements specifying `12:04 PM`, `11:11 AM`, `10:01 AM`) defaulted all transaction times to `12:00 AM`.
  - Upgraded the statement parsing engines on both Android (`StatementParserEngine.kt`) and Web (`js/parser.js`) with robust time-extraction and datetime-matching capabilities.
- **Android Precision Epoch Timestamp Parsing (`StatementParserEngine.kt`)**:
  - Expanded `supportedDateFormats` and `monthFirstFormats` with 12-hour AM/PM and 24-hour timestamp patterns (`dd MMM yyyy hh:mm:ss a`, `dd MMM yyyy hh:mm a`, `dd MMM, yyyy hh:mm a`, `MMM dd, yyyy hh:mm a`, `dd/MM/yyyy hh:mm a`, `yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd HH:mm`, etc.).
  - Implemented `extractTime(text: String): String?` with regex normalization for 12-hour AM/PM (e.g. `12:04pm` -> `12:04 PM`) and 24-hour formats.
  - Overloaded `parseDate` to combine date string with extracted time string before parsing, setting the exact epoch millisecond so Android UI formatters (`hh:mm a`) display the true transaction time rather than default midnight (`12:00 AM`).
  - Integrated time extraction across all statement parsers: Google Pay, Navi, PhonePe, SBI, Credit Card statements, Generic fallback lines, and CSV/Excel tables.
- **Web Transaction Time Preservation (`js/parser.js`)**:
  - Implemented `extractTime(text)` in `js/parser.js` supporting 12-hour AM/PM and 24-hour formats.
  - Fixed transaction normalization in `parseFile` by assigning `time: row.time || ''`, preserving transaction times across all web views, ledger filters, and backup exports.
  - Integrated time parsing across `parseUPIAppPDF` (Google Pay), `parseNaviBlocks`, `parsePhonePePDF`, `parsePaytmPDF`, `parseGenericTablePDF`, `parseCreditCardPDF`, `parseCSVText`, and `parseExcelBuffer`.
- **Cash Flow Dual-Zone Curve & Gradient Fill (Android & Web)**:
  - Fixed an issue in Cumulative Flow mode where the curve line and gradient fill were rendered entirely red whenever the net period ended in a deficit (`totalNet < 0`), even for historical segments where the balance was positive above the zero baseline.
  - Upgraded Android's `DashboardScreen.kt` Canvas rendering using dual-zone `clipRect` scissoring:
    - **Positive Territory (`balance >= 0`, above zero line)**: Curve renders in vibrant Emerald Green (`#10B981`) paired with a luminous emerald gradient fill fading toward the zero baseline.
    - **Negative Territory (`balance < 0`, below zero line)**: Curve renders in Rose Red (`#F43F5E`) paired with a rich crimson gradient fill fading toward the baseline.
    - Zero baseline dashed divider is drawn crisply on top of the fills for optimal visual hierarchy.
  - Updated Web's `js/charts.js` with a dynamic dual-stop linear gradient centered around the calculated zero-baseline ratio, ensuring seamless cross-platform parity between Web and Android.
- **Comprehensive Regression & Unit Test Coverage**:
  - Added unit test cases in `LogicTests.kt` verifying exact time extraction and parsing for Google Pay (`12:04 PM`, `11:11 AM`, `10:01 AM`), Navi (`12:00 AM`, `06:24 PM`), PhonePe (`06:57 PM`), and CSV statements.
  - Added web regression tests in `test_web_regressions.js` verifying time preservation across Google Pay, Navi, PhonePe, and generic table rows.

## [1.6.1] - 2026-09-21

### Fixed - UPI Statement Intelligence: Google Pay, Navi Bank Account Detection & Clean Formatting
- **Dashboard Accounts & Cards Live Balance Snapshot (Android & Web)**:
  - Fixed an issue where the Dashboard "Accounts & Cards" snapshot displayed `₹0` for accounts and credit cards, despite the correct amounts appearing inside the "Manage" screen (`AccountsScreen`).
  - Resolved the root cause by binding the Dashboard snapshot to `accountsWithMetrics` instead of raw static account entities, calculating live balances, card spends, and dues in real time with dynamic currency formatting and color coding.
- **Navi Statement Glued Bank Account & Last-4 Resolution (Android & Web)**:
  - Fixed an issue where bank account narrations with last-4 digits glued to the payee row (such as `JOHN DOE HDFC Bank - 1234`) were failing account splitting and defaulting to the file-level account (`Navi UPI Wallet`).
  - Added support for bank names ending with last-4 digits directly on the payee line, cleanly extracting the true linked bank account (e.g. `HDFC Bank Account (•••• 1234)`) and preserving the clean merchant/person title.
  - Expanded bank name recognition patterns to support multi-word Indian banks (Kotak Mahindra Bank, Punjab National Bank, Bank of Baroda, Union Bank of India, etc.) without greedily consuming preceding payee words.
- **Boilerplate Watermark Filtering in Categorizer Titles (Android & Web)**:
  - Filtered out payment app boilerplate notes (`Note: Paid via Navi UPI`, `Paid using ...`) from transaction titles so that descriptions read cleanly as the merchant/payee (e.g. `John Doe`) rather than truncated strings like `JOHN DOE (Paid vi...`.
- **Google Pay Statement Detection & Priority Routing (Web & Android)**:
  - Fixed statement routing order so Google Pay statement signatures take priority over underlying bank names mentioned in transaction rows (such as State Bank of India, HDFC Bank, ICICI Bank), preventing Google Pay statements with SBI transactions from being falsely hijacked by SBI table parsers.
  - Refined bank detection in `extractAccountMetadata` to avoid false positives on UPI narrations in bank statements while reliably matching Google Pay signatures (`google pay app`, `gpay`).
- **Google Pay Self-Transfer & Exact Inflow/Outflow Parity (Web & Android)**:
  - Added support for `Self transfer to <Bank> <Account>` rows, classifying them as `transfer` transactions instead of expenses.
  - Excluded internal self-transfers from statement outflow totals in Google Pay, achieving 100% exact parity with official Google Pay statement summary figures (Sent / Received totals).
- **RuPay Credit Card 2-Digit Masked Account Detection (Web & Android)**:
  - Added support for 2-digit masked card identifiers (e.g. `XX99`), common on Google Pay UPI linked credit cards, ensuring RuPay cards are properly recognized with their last digits and assigned dedicated credit card accounts.
- **Incoming UPI Receipt Bank Account Resolution (Android & Web)**:
  - Enhanced Android's account hint extractor to recognize `Paid to <Bank> <Account>` lines on received transactions, correctly attributing incoming transfers to destination bank accounts.
- **Statement Period Summary Header Filtering (Web)**:
  - Filtered date-range summary headers (e.g. `01 March 2026 - 31 August 2026`) in `parseUPIAppPDF`, preventing phantom transactions from being created for period totals.

## [1.6.0] - 2026-09-19

### Added - Currency Hub, Refund Intelligence, Cross-Platform Interoperability & Dedicated Ledger Filtering
- **144Hz & High-Refresh-Rate Display Compatibility (Android)**:
  - Configured `preferredDisplayModeId` and `preferredRefreshRate` on `MainActivity.window.attributes` across `onCreate`, `onResume`, and `onWindowFocusChanged` to dynamically lock the window to the hardware display's maximum supported refresh rate (144Hz, 120Hz, 90Hz).
  - Bypasses vendor OEM display throttles (Motorola, Xiaomi, ASUS ROG, Realme) that clamp non-game Compose applications to 60Hz.
  - Declared `android:hardwareAccelerated="true"` explicitly on `<application>` and `<activity>` in `AndroidManifest.xml`.
  - Replaced expensive GPU `Modifier.blur()` RenderEffect passes with single-pass `Brush.radialGradient` ambient backlights across `HigGlassCard`, `BacklitCurrencySelector`, `DashboardScreen`, and `ProfileChooserScreen`, keeping GPU frame times well under the 6.94ms 144Hz budget.
  - Implemented zero-allocation currency formatting via `ThreadLocal<HashMap<String, NumberFormat>>` in `CurrencyEngine` and memoized date/string formatting in `TransactionRowItem`, eliminating ICU allocations and GC pauses during fast scroll frames.
  - Added `contentType = { _, txn -> txn.type }` in `TransactionsScreen.kt` for efficient Compose `LazyColumn` item slot recycling.
- **Currency Information & Exchange Rates Hub (Android APK & Web App)**:
  - Added dedicated Currency Information & Rates screen in Android APK (under *More* tab -> `Currency & Exchange Rates`) and Web App (sidebar navigation -> `Exchange Rates`).
  - **Dynamic Default Base Currency Architecture**: Dynamically calculates and formats all rates relative to whatever currency is set as the active profile's default/primary currency (e.g. `1 EUR = 90.76 INR`, `1 INR = 0.012 USD`).
  - **Clean UI & Self-Reference Omission**: The active profile's default currency is automatically excluded from the card list (e.g., if EUR is default, 1 EUR = 1 EUR is omitted, displaying the remaining 5 currencies).
  - Shows last updated timestamp (date and time) and API sync health indicator with ambient pulse dot and active base badge.
  - Added "Force Sync from API" button with rotating sync animation to bypass daily limits and pull fresh rates on demand.
  - **Dynamic Manual Rate Overrides**: Allows user to input custom rates directly relative to their active default currency (e.g., set `1 EUR = 100 INR`) with instant real-time inverse conversion preview.
  - Dedicated "Reset to API" per-card button and global "Reset All" button to revert overrides back to the pristine cached API rates relative to the base currency.
  - Vibrant Apple HIG / FinTech ambient backlight glow cards with signature neon color halos per currency (Emerald Green for INR, Electric Blue for USD, Royal Violet for EUR, Rose Crimson for GBP, Alpine Cyan for CHF, Golden Amber for JPY).
- **Profile Manager & Chooser Usability Polish (Android & Web)**:
  - Unified currency selection into a single intuitive selector for the active profile, eliminating duplicate selectors.
  - **Single-Surface Currency Selector Redesign (Android & Web)**: Eliminated the nested "double box" visual defect in light mode by replacing multi-layer surface wrappers with a clean, unified single-surface card. Features pure solid white backgrounds (`#ffffff`), delicate 1dp borders (`#e2e8f0`), and subtle 8% signature accent tint on selection in light theme, paired with deep dark cards (`#131b2e`) and luminous 16% accent glow in dark theme. Added top-row national flags, crisp checkmark badge indicators, and tactile haptic feedback.
  - Fixed mobile soft keyboard layout compression on the profile name input field with `BoxWithConstraints` and responsive vertical scroll containers.
  - **Screen Top Spacing & WindowInsets Harmonization (Android)**:
    - Fixed excessive blank space gaps under top navigation bars on subscreens (`Cards & Accounts`, `Currency & Rates`) by setting `contentWindowInsets = WindowInsets(0.dp)` on nested `Scaffold` composables, eliminating redundant double-status-bar insets.
    - Eliminated large top gaps in `ModalBottomSheet` containers (`ProfileManagerSheet`, `AddEditTransactionSheet`, `TransactionDetailsSheet`, `AddAccountSheet`, `AddRuleSheet`, `PasswordPromptSheet`) by replacing `safeDrawingPadding()` (which erroneously applied status-bar height to sheet tops) with `navigationBarsPadding()` and compact `top = 2.dp` padding.
    - Synchronized `contentPadding` top alignments across all tabs and subscreens for a cohesive, homogeneous visual rhythm.
- **Refund Inflow Intelligence & Deduplication Isolation (Web & Android)**:
  - Added dedicated `REFUND` transaction type across Web (`'refund'`) and Android (`TransactionType.REFUND`).
  - Auto-detection of refunds and reversal credits across bank and card statement parsers (Navi, Paytm, generic PDF, spatial table, line-based, CSV).
  - Deduplication strictly separates transactions of different types: an expense and a refund of identical amount/merchant are never merged as duplicates.
  - Financial aggregators and KPI math (Web `accounts.js`, `budgets.js`, `charts.js` & Android `KpiMath`, `DayClock`, `CalendarAggregator`, `MainViewModel`) count refunds as positive inflow without misclassifying them as earned income.
  - UI styled with dedicated Alpine Cyan badge & amount coloring (`#06b6d4` / `Color(0xFF06B6D4)`) and `Refresh` icon across ledger cards, details sheets, edit dialogs, and filters.
  - Added a dedicated 4th `"Refunds"` filter segment in the Android ledger (`HigSegmentedControl`).
- **Web App & Android App Functional Parity Harmonization**:
  - Added manual **Add Custom Rule** dialog (`addRuleModal`) and header button to the Web AI Learned Rules tab, bringing 100% parity with Android's `AddRuleSheet` with instant retroactive reclassification of existing matching records.
  - Added `Refund (Reversal)` transaction type option to Web transaction edit modal dropdown (`eTxnType`), resolving the gap where editing a refund transaction could reset its type to expense.
  - Updated Web accounts view credits calculation (`totalCredits` in `accounts.js`/`app.js`) to include refund transactions as positive inflows alongside income and transfers.
  - Updated multi-format PDF export engine (`export.js`) so refund rows render with a positive `+` prefix and signature Alpine Cyan styling.
- **Bi-Directional Cross-Platform Backup Interoperability**:
  - Web `ExportEngine.restoreBackupFromFile`: Supports importing Android APK JSON backups, auto-converting epoch millisecond timestamps into ISO `YYYY-MM-DD` strings, mapping `accountId`/`accountName`, and deserializing categories and rules.
  - Android `ExportEngine.parseJsonBackup`: Supports importing Web `stores` JSON backups, auto-parsing ISO dates to epoch milliseconds, and mapping Web property names and enums.
- **Android Statement Picker Modernization & Password Resilience**:
  - Replaced legacy `GetContent()` with Android Storage Access Framework `ActivityResultContracts.OpenDocument()` in `UploadScreen.kt`.
  - Added in-memory byte caching for password-protected statements in `UploadScreen`, eliminating transient URI permission expiration on Android 14/15 while entering PDF passwords.
- **Security Audit, Bug Hunt & Data Integrity Hardening (Web & Android)**:
  - Cross-Currency Deduplication Isolation: Bucketed amount keys and comparison logic in `js/duplicateDetector.js` and `DuplicateDetectorEngine.kt` now strictly require identical currency codes, preventing false-positive duplicates across different currencies with matching nominal values.
  - Android Dashboard Reactive Currency Invalidation: Wired `currencyStateVersion` into `MainViewModel.kt`'s `totalNetWorth`, `totals30D`, and `categoryBreakdown` flows, ensuring instant real-time KPI updates upon manual rate overrides or API syncs.
  - Web Database Connection Teardown: Added `close()` to `Database` in `js/db.js` so profile deletion via `indexedDB.deleteDatabase()` executes cleanly without blocked connection locks.
  - CSV & Excel Export Accuracy: Added `Currency` and `Account` columns to CSV exports and dynamically bound primary currency codes to Excel header labels in `js/export.js`.
  - Cleartext Traffic Blocked: Configured `android:usesCleartextTraffic="false"` in `AndroidManifest.xml` to enforce strict HTTPS transport security.
  - Web Calendar Inflow Accuracy: Corrected refund arithmetic in `js/calendar.js` monthly inflows and day-ledger badge styling.
  - Profile-Scoped Storage Sandboxing (Web): Scoped `CurrencyEngine` manual rates and overrides to the active user profile ID (`money_tracker_currency_overrides_<profileId>`) with seamless legacy fallback.
  - Mobile Sidebar Scrim Backdrop (Web): Added animated `.sidebar-backdrop` with backdrop blur and tap-to-dismiss handling on mobile viewports.
  - Daylight Ambient Glow Tuning (Web): Strengthened border opacity and colored drop shadows on currency cards in light theme for crisp daytime visibility.
  - Multi-Currency Ledger Filtering (Web & Android): Added currency filter controls allowing transactions to be isolated by currency code (`INR`, `USD`, `EUR`, `GBP`, `CHF`, `JPY`).
  - Android NeedsReview Parity: Added transaction type reclassification segmented control (`Expense`, `Income`, `Refund`, `Transfer`) and custom regex/string pattern editing for persistent rule learning.
  - Adaptive Filter Chips (Android): Replaced squeezed segmented control with smooth horizontally scrollable `LazyRow` filter chips for type and currency filtering on all viewport widths.
  - Tactile Haptic Feedback (Android): Added subtle haptic responses (`LocalHapticFeedback`) across manual rate overrides, rule learning, delete confirmations, and filter switches.
  - Proguard / R8 Keep Rules (Android): Added `proguard-rules.pro` protecting Room entities, DAOs, PDFBox Android, Apache Commons CSV, and serialization models.
  - Temp & Junk Files Cleanup: Cleared stale intermediate build caches, transforms, stopped all orphaned Gradle daemons, and pruned old APK archives.

### Fixed & Enhanced - Comprehensive Security, Accessibility & UX Hardening
- **Stored XSS Defense & Strict ID Validation (Web)**:
  - Enforced strict alphanumeric regex validation (`^[a-zA-Z0-9_\-.:@]{1,128}$`) on imported entity IDs during backup restoration.
  - Replaced inline string-interpolated IDs with safe `data-id` dataset attributes in transaction action buttons, rule cards, review resolutions, duplicate pairs, and account lists.
  - Escaped category names dynamically in dropdown selections.
- **Soft Keyboard Insets & Bottom Sheet Scrolling (Android)**:
  - Applied `.verticalScroll(rememberScrollState())` and `.imePadding()` across `AddTransactionBottomSheet`, `TransactionDetailBottomSheet`, `AddAccountBottomSheet`, and `ProfileManagerSheet`, preventing button occlusion and ensuring all form inputs scroll smoothly into view when the soft keyboard is active.
- **Multi-Currency Formatting Consistency (Android)**:
  - Integrated `CurrencyEngine` formatting into `DuplicateResolverScreen`, `NeedsReviewScreen`, and `UploadScreen` preview cards, honoring the user profile's active primary currency with proper locale-aware decimal formatting.
  - Added dedicated Alpine Cyan coloring (`Color(0xFF06B6D4)`) for refund transaction amounts in `NeedsReviewScreen`.
- **Atomic Account Deletion & Transaction Reassignment (Android)**:
  - Added `@Query` in `TransactionDao` to automatically reassign transactions to `"Cash / Unassigned"` and empty `accountId` within an atomic Room `db.withTransaction { ... }` block when an account is deleted.
- **Accessible Modal Focus Trapping (Web)**:
  - Implemented `trapModalFocus(modal)` and `releaseModalFocus(modal)` to constrain `Tab` and `Shift+Tab` cycling within open dialogs according to WCAG 2.1 AA accessibility guidelines.
- **IndexedDB Deletion Blocked Fix (Web)**:
  - Safely closed active IndexedDB connections prior to calling `indexedDB.deleteDatabase()` during profile removal.
- **Modern AutoMirrored Icons (Android)**:
  - Replaced deprecated Compose icons with `Icons.AutoMirrored` equivalents (`HelpOutline`, `ArrowBack`, `TrendingUp`, `ShowChart`).
- **Centralized Lucide Icon Hydration (Web)**:
  - Consolidated safe icon hydration helper `hydrateIcons(root)` on `App` and `window.hydrateIcons` with existence guards and root-scoping support.
- **Dynamic Touch Dial Precision (Web)**:
  - Upgraded interactive timepicker touch math to dynamically compute plate center coordinates from `getBoundingClientRect()`, ensuring pixel-accurate clock face dragging on small mobile screens.
- **Deduplication Floating Point Precision Fix (Android)**:
  - Replaced `(amount * 100).toLong()` with `kotlin.math.round(abs(amount) * 100).toLong()` in Android duplicate engine, eliminating truncation mismatches on cents/paise amounts like `19.99`.
- **Search Debouncing & UI Performance Optimization (Android & Web)**:
  - Added 200ms debounce on transaction search queries in Compose `TransactionsScreen` to eliminate main-thread stutter on large datasets.
  - Event listener leaks eliminated in Web interactive timepicker by binding document move/up listeners dynamically during active dragging only.

## [1.5.1] - 2026-09-19

### Changed - Currency Portfolio Adjustment
- **Replaced Chinese Yuan (`CNY`) with British Pound (`GBP`) and Swiss Franc (`CHF`)**:
  - Removed `CNY` (`¥` / `元`) across Web and Android platforms.
  - Added British Pound (`GBP`, `£`) with Rose Crimson ambient backlight halo (`#E11D48`).
  - Added Swiss Franc (`CHF`, `₣`) with Alpine Cyan ambient backlight halo (`#06B6D4`).
  - Updated offline baseline rates: `1 USD = 0.78 GBP`, `1 USD = 0.89 CHF`.
  - Updated Backlit Currency Selector, Transaction Creation/Edit modal selects, unit tests, and cross-currency conversion engines.

## [1.5.0] - 2026-09-19

### Added - Multi-Currency Architecture & Live Exchange Rates
- **Global Currency Engine (Web & Android)**:
  - Full support for 5 major global currencies: Indian Rupee (`INR`, `₹`), US Dollar (`USD`, `$`), Euro (`EUR`, `€`), Japanese Yen (`JPY`, `¥`), and Chinese Yuan (`CNY`, `¥`).
  - **Live Daily Exchange Rates**: Integrated with Fawaz Ahmed's Currency API (`jsdelivr` CDN with fallback to Cloudflare Pages) using USD base cross-rate math `(amount / rateFromUsd) * rateToUsd`.
  - **Strict Once-a-Day Sync Policy**: Rates are fetched only once per calendar day upon the first app opening. If the app is not opened on a given day, no background fetch is triggered. Offline baseline snapshot is maintained for complete offline resilience.
  - User-Agent header `Mozilla/5.0 MoneyTrackerApp/1.5.0` to ensure reliable CDN response delivery.
- **Ambient Backlit Currency Selector**:
  - Custom UI component inspired by Apple Human Interface Guidelines and `ui-ux-pro-max` styling principles.
  - Features radiant per-currency ambient glow halos (Emerald Green for INR, Electric Blue for USD, Royal Violet for EUR, Golden Amber for JPY, Coral Red for CNY), flag badges, currency symbols, and currency codes.
  - Smooth micro-animations with animated elevation and border outlines on selection.
- **Profile-Level Primary Currency**:
  - Default currency is selected with the backlit selector during account/profile setup.
  - Can be switched anytime from Settings / Profile Manager sheet.
  - All dashboards, cashflow graphs, KPI metrics, category breakdowns, budgets, and monthly calendar views automatically convert and aggregate in the profile's active primary currency.
- **Multi-Currency Manual Entry & Live Conversion Preview**:
  - Record transactions in any supported currency with the integrated Backlit Currency Selector.
  - Real-time conversion preview badge (`≈ ...`) showing the primary currency equivalent as you type.
  - Transaction ledger cards display the original transaction currency alongside the converted primary currency value.
- **Multi-Currency Accounts & Cards**:
  - Accounts can be created in their native currency with custom symbols.
- **Data Persistence & Database Migration**:
  - Android Room database bumped to Version 4 with seamless SQLite migration `MIGRATION_3_4` adding `currency` columns with `DEFAULT 'INR'` to `transactions` and `accounts`.
  - Export & Backup engines updated across Web and Android: JSON and CSV backups preserve currency tags per row without schema breakage.
- **Tests**:
  - Added unit test suite for `CurrencyEngine` conversions, cross-rates, once-a-day logic, and multi-currency KPI aggregation.
  - Web regression test suite updated (28/28 tests passing).

## [1.4.2] - 2026-09-18

Found by running the real parser against an actual SBI account statement and a Navi UPI history (kept local, never committed).

### Fixed
- **Navi UPI import silently dropped rows (web + Android)**: every "Bill payment of …" row (credit-card bills, electricity) was skipped because only "Paid to / Received from" rows were recognised. In the sample, 5 of 104 transactions were lost, including a large card bill. All 104 now import.
- **Credit-card bill payments double-counted**: a bill paid *of* a credit card was recorded as spending on top of the card's own purchases. They are now transfers (`Transfers & CC Bill`) on both platforms, and no longer create a phantom RuPay card account.
- **Navi rows on the wrong account / polluted names (web)**: the paying account column was glued into the payee ("Paid to X HDFC Bank RuPay"), the user's own name leaked in from the page header, and everything landed on one wrongly-typed "State Bank of India Account (0000)". The parser now reads the details / account / amount columns by position, so each row goes to the account that paid it ("State Bank of India - 2105" and "Credit Card - XX99"). SBI-paid rows land on the same account as an imported SBI statement, which lets cross-statement duplicate detection work. Long payee names that wrap onto the time row are joined correctly.
- **SBI statement typed as a UPI wallet with the wrong account number (web + Android)**: narrations containing "gpay"/"paytm" made a bank statement a wallet, and the first 11-digit number (the CIF number) was used instead of the Account Number. Bank statements are never wallets now, and the value labelled Account Number is used. Navi is identified before SBI so its "Account" column can't claim the whole file.
- **Repeated table-header rows leaking into narrations (web)**: a per-page header row (`Date Narration … Withdrawal Deposit Balance`) is now recognised and skipped in the generic, SBI and credit-card table parsers instead of being appended to the previous transaction.
- **Two accounts at one bank merged into one (web)**: a second account or card at the same bank silently reused the first because of a bank-name fallback. Accounts with different real last-4 digits now stay separate; the fallback only applies when a last-4 is missing or a placeholder.
- **PhonePe / Google Pay / Paytm direction (web)**: "Bill payment of HDFC **Credit** Card" was read as income because the first CREDIT/DEBIT word (or any line containing "credit") decided the direction. The type column now wins, and "Paid to / Bill payment / Recharge" rows are outgoing.
- **Google Pay dates split across rows** ("01 Jan," / "2025") are re-joined.
- **Two-digit masked card suffixes** such as `XX99` are now captured as the card's last digits.
- **Android restore picker** no longer hides backups saved as `text/plain` or `application/octet-stream` (uses `OpenDocument`; content is still validated on restore).
- **Android US-order dates**: `01/15/2025` is now read as 15 January when day-first is impossible. Ambiguous dates such as `05/06/2025` stay day-first (Indian convention) and unreadable ones are still flagged rather than guessed.

### Added
- **Per-row account detection for UPI histories (web + Android)**: one statement that mixes several bank accounts, a UPI-linked RuPay credit card and the app wallet now splits into separate accounts, each keyed by its last four digits, instead of landing on one account. Covers Navi (verified on a real statement), and PhonePe, Google Pay and Paytm (built from public descriptions of their layouts, **not verified on real PDFs**). A row with no readable account line falls back to the file's default account and is never dropped. On Android the hints flow from the parser to the importer in memory only, with no database change.

### Changed
- **APK archive retention 10 → 5** (build script, README, project rules): each debug APK is ~26 MB, so ten fallback versions plus the root APK dominated the repository size. `ExpenseTracker.apk` is no longer kept in git history; only the current archive lives in the tree.
- **Categorizer**: removed hyper-local merchant/institution title entries and added generic fee terms (`tuition`, `school fee`, `college fee`, `exam fee`, `challan`) to Bills & Utilities on web and Android. Manual corrections keep teaching the app your own merchants.
- **Repository hygiene**: history no longer contains local tooling files, local SDK paths or personal identifiers; commits use the GitHub noreply address.

### Verified against the real files
- SBI statement: 118 of 118 rows match the PDF's own debit/credit/balance columns, with both totals identical to the paisa and an unbroken running balance.
- Navi statement: 104 transaction IDs in the PDF, 104 records with unique IDs.

### Tests
- Web regression suite 13 → 27 (Navi block layout, per-app account splitting for PhonePe / Google Pay / Paytm with mixed banks, cards and wallet, same-bank account separation, header-row guard, masked card suffix). Android unit tests 31 → 42.

### Known gaps
- PhonePe, Google Pay and Paytm account detection has only been exercised on synthetic layouts; how those apps label credit-card, wallet and UPI Lite payments is unconfirmed. Unknown wording simply falls back to the default account.
- Android reads flattened PDF text lines, so its Navi/UPI account parsing is tolerant of several glue orders but has not been tried against real PDFBox output on a device. A Navi row split across a page break is still dropped on Android. The repeated-header guard is web-only.
- Android gives a UPI-linked card without a four-digit suffix (`XX99`) last-4 "0000"; the card is matched by bank name.

## [1.4.1] - 2026-09-18

### Fixed — data correctness
- **Wrong amounts (web parser)**: the amount was taken from the first digit run on a line, so `15 Jan 2024 … Rs. 1,250.00` could import as ₹15. Amounts now come from `extractAmount`, which strips the date/time first and ignores bare integers; the raw-table path uses it too.
- **Dropped income (web)**: an unsigned single `Amount` column was forced to *expense*, and unanchored `cr`/`dr` matching treated "Description"/"Address" as credit/debit columns. Direction is now left undetermined for the categorizer, and headers are anchored.
- **Date shifts**: `new Date('YYYY-MM-DD')` / `toISOString()` moved dates back a day in UTC+ zones (IST). New `js/dateutil.js` keeps everything in the local calendar across dashboard, charts, budgets, export and duplicate detection. Unreadable dates are no longer silently "today": the row is kept and flagged for review (web and Android; Android heuristic parsers skip such rows).
- **Over-eager de-duplication**: two identical rides in one statement no longer collapse; cross-statement fuzzy matches go to manual review instead of being dropped on import; a debit and a credit are never merged; transfer legs are never auto-deleted; merges are one atomic IndexedDB write.
- **Learned rules**: newest rule wins, and learning from a manual add/edit no longer rewrites every matching transaction.
- **Accounts**: editing keeps `createdAt`/auto-detected flags; a credit limit of 0 is accepted.

### Added — backup & safety
- **Web Full Backup (JSON)** card in Reports: download and restore (atomic, schema-checked, confirmation before replacing data). Requests persistent storage.
- **Android**: backups written through the system save dialog (timestamp recorded only after a successful write), version-checked restore, CSV/JSON export fixes (quoting, formula-injection guard, no `1.25E7`), Room schema export, explicit destructive-migration scope, delete-transaction confirmation, state that survives rotation, KPI range selector, profile delete now closes and removes its database.
- CSV export escapes formula starters on web too.

### Security
- Escaped the duplicate-pair reason text (stored XSS); a failed IndexedDB open now shows a recovery screen instead of a blank app.

### Accessibility & design
- **Web**: skip link, labelled landmarks, announced menu state and page title, labels for all form controls, accessible names for icon buttons and charts, closed modals/drawer removed from tab order, 44px touch targets on touch devices, contrast tokens replace hard-coded greys, empty-state call to action, debounced search.
- **Android (HIG)**: calendar cells and rows have roles, descriptions and selected state; 48dp targets; light-theme semantic colours darkened to ≥4.5:1; calendar parity (compact amounts, ledger edit/delete, month rollover, midnight refresh).

### Tests
- New `tests/test_web_regressions.js` (13 checks, run with `TZ=Asia/Kolkata`); Android unit tests now 31 (calendar aggregation, backup reminder policy, strict dates).

### Known gaps
- Repeated PDF header rows can still leak into narration text; US-order `MM/DD` dates on Android are flagged/skipped rather than guessed; Android restore uses `GetContent("application/json")`, which some pickers filter.

## [1.4.0] - 2026-09-18

### Added
- **Android Calendar Month-View & Day-Ledger (`CalendarMonthView.kt`, `TransactionsScreen.kt`)**:
  - Brought full feature parity to Android for the financial calendar month-view and interactive day-ledger, styled with Apple Human Interface Guidelines (HIG) and `HigGlassCard`.
  - **Sliding Pill View Switcher**: Toggles between "List View" and "Calendar Month" via `HigSegmentedControl`.
  - **Monthly Financial Header**: Displays Total Inflow, Total Outflow, and Net Cashflow summary cards with high-contrast semantic typography (`IncomeGreen`, `ExpenseRose`). Includes quick `<` / `Today` / `>` month navigation and interactive Month/Year picker dialog.
  - **7-Column Month Grid**: Accessible day cells (≥44dp touch targets) displaying day numbers, today ring, selection highlight, and transaction badges/indicators (income, expense, count).
  - **Interactive Day-Ledger**: Shows all transactions for any tapped date in an Apple HIG grouped card (`HigInsetGroup`) with day totals and a 1-tap "Add for this Date" shortcut that pre-populates the transaction sheet with the selected date.
- **Android Backup-Reminder Banner (`DashboardScreen.kt`, `MainViewModel.kt`)**:
  - Implemented the Apple HIG backup reminder banner on the Android dashboard, matching the web application's rules.
  - Automatically alerts the user if financial data has not been backed up in the last 30 days (or never) while transactions exist.
  - Features "Remind Me Later" (snoozes the banner for 7 days via persistent preferences) and "Export Backup" (navigates directly to the Reports & Exports hub).
  - Automatically records backup completion upon exporting or restoring JSON backups.

## [1.3.3] - 2026-09-18

### Fixed
- **Security (High)**: stored XSS in profile rename — a profile name was threaded through an inline `onclick="...'${name}'..."` string; HTML-escaping doesn't protect that context since the browser decodes entities before the JS parser sees them. A crafted profile name (e.g. `x'-alert(1)-'`) executed on click. `startEditProfile`/`cancelEditProfile` no longer take a name parameter at all — they look the profile up by id instead (`js/app.js`).
- **Reliability (High, Android)**: the PDF password-retry path parsed statements synchronously on the UI thread, reintroducing the exact ANR the main upload path was already fixed for. Now runs on `Dispatchers.IO`, with the URI captured up front and Cancel/dismiss disabled while unlocking to close a use-after-cancel crash window (`UploadScreen.kt`).
- **Build correctness (Medium)**: `build.gradle.kts`'s APK-copy/retention task was registered for both the debug and release variants despite the project only ever shipping debug — if both were ever built together they'd race to overwrite the same output files. Now scoped to the debug variant only; the retention pruning also no longer fails silently if a file can't be deleted.
- **Accessibility (Medium)**: the 5 legacy modals (Add/Edit Transaction, Account, PDF Password, Profile Manager) had no `role="dialog"`, focus management, or Escape-to-close, unlike the calendar/timepicker popovers. All 5 now match that pattern — dialog semantics, focus-in on open, focus-restore on close, and a shared Escape handler. Fixed a regression this introduced where Escape would close a calendar/timepicker popover *and* the modal underneath it in one keystroke — the popover now consumes that Escape press first (`js/app.js`, `js/calendar.js`, `js/timepicker.js`, `index.html`).
- **Responsive design (Medium)**: confirmed real horizontal overflow on mobile (375px viewport, 537px content). Root cause was a CSS Grid item defaulting to `min-width: auto`, so a Chart.js canvas forced the whole dashboard grid wider than the screen; fixed with `min-width: 0` on the grid columns plus `flex-wrap` on two button rows that had the same gap (`css/components.css`, `css/main.css`).
- **Polish (Low)**: replaced two remaining decorative gradient-text instances (sidebar brand title, both themes) with the app's solid text color; added `prefers-reduced-motion` support app-wide.
- **Android/Web parity**: two correctness fixes shipped web-only in v1.1.4 had not been ported to Android — cash/wallet accounts with a zero starting balance and no income showed a positive balance instead of negative, and cross-statement duplicate auto-merge accepted a substring UTR match instead of requiring an exact one. Both are now fixed identically on Android (`MainViewModel.kt`, `DuplicateDetectorEngine.kt`, `AccountsScreen.kt`).

### Known gaps (Android vs. Web, not yet addressed)
- Android's native date/time pickers lack the web calendar/timepicker's "Clear" action and custom accent styling (functionally equivalent otherwise).
- 371+ hardcoded hex colors across the web CSS vs. 14 design tokens — flagged as a separate, dedicated pass rather than a blind mechanical migration.

## [1.3.2] - 2026-09-18

### Fixed
- **Accessibility (P0)**: the new calendar and timepicker popovers were completely keyboard-inoperable (no `tabindex`/`role`/`aria-*`, replacing the native `<input type="date">`/`type="time">` this feature removed, which *was* keyboard-operable). Both popovers now expose `role="dialog"` with initial focus on open and focus-restore on close; calendar day cells use a roving-tabindex grid with arrow-key/Home/End navigation and Enter/Space to select; the timepicker dial ticks are keyboard-steppable the same way, including when the current time doesn't land on an exact displayed tick (`js/calendar.js`, `js/timepicker.js`).
- **Design consistency**: the timepicker's hardcoded emerald green (`#10b981`/`#059669`) didn't match the calendar's blue `--color-primary` sitting right next to it in the same row, in both themes — now uses the app's actual accent color (`css/components.css`).
- **UX**: added a "Clear" action to the calendar popover footer (previously no way to unset a date without reopening and picking a new one), and made the year label clickable to type a year directly instead of stepping one year per click.
- **Security**: `t.id` was interpolated unescaped into an inline `onclick` string in the calendar's day-ledger popup; switched to `data-txn-id` attributes with delegated listeners (`js/calendar.js`).
- **Reliability**: removed a duplicate click handler on the date field that fired `openDatePicker` twice per click (masked today by an unrelated debounce, but fragile); the wrapper's own `onclick` is now the only binding (`js/app.js`).
- **Test coverage**: `tests/test_web_edit_transaction.js` previously reimplemented fake logic and never exercised the real `calendar.js`/`timepicker.js`, and used a UTC-bug-prone date fallback that didn't match the app's actual `toLocaleDateString('en-CA')` logic. Both files are now Node-testable (guarded browser-only instantiation) and the suite asserts leap-year/days-in-month/date-format math and 12h↔24h time conversion directly.
- **Caching**: `<script>` tags had no cache-busting version query (unlike the CSS `<link>` tags, which already used `?v=`), so a browser could silently keep serving stale JS after an update ships. All script tags now carry a version query matching the stylesheet convention.

## [1.3.1] - 2026-09-18

### Fixed
- **Segmented Control Light Mode Text & Icon Contrast (`css/components.css`)**:
  - Fixed an issue where the active pill button in `.segmented-control` (e.g. `[ Table | Calendar ]` toggle on the Transactions view) had dark grey text and icon on a blue background in Light Mode.
  - Corrected selector specificity and explicitly enforced high-contrast pure white (`#ffffff`) text and SVG icon stroke for `.segmented-control button.btn-primary *` and `.segmented-control button.active *`.
  - Inactive segmented buttons retain clean, readable slate (`#475569`) styling without overriding the active state.

## [1.3.0] - 2026-09-18

### Added
- **Materialize-Style Themed Time Picker Suite (`js/timepicker.js`, `css/components.css`)**:
  - Replaced browser-native time pickers with an interactive analog clock-dial timepicker inspired by Materialize CSS and classic analog watch design.
  - **Real Analog Clock Arms**: Features dedicated Hour and Minute clock hands with counterbalance tails, center metallic pivot hub, and high-visibility neon emerald active styling with subtle glow. Both hands remain simultaneously visible just like a real mechanical clock face.
  - **Smooth View Transitions**: Selecting an hour automatically smoothly advances the dial to the minutes view. Users can also tap the large digital readout (`HH : MM`) in the header to jump back and forth.
  - **AM / PM Segment Switcher**: Supports seamless 12-hour selection with instant AM/PM toggle while outputting standard 24-hour `HH:mm` format for 100% backward database and test compatibility.
  - **Quick Action Buttons**: Includes 1-tap "Now" (current time), "Cancel", and "OK" actions with keyboard accessibility (Escape to close, Enter to submit).
  - **Dual Theme Support**: Custom styled for OLED Dark Mode (`#090d16` with radiant `#10b981` accents) and Luxury Light Theme (`#ffffff` frosted card with forest emerald `#059669` accents).
  - **Removed Old/Native Time Pickers**: Completely eliminated `<input type="time">` indicators and native browser pickers from Add and Edit Transaction modals.

## [1.2.1] - 2026-09-18

### Changed & Cleaned Up
- **Removed Old/Native Browser Date Picker (`index.html`, `components.css`, `js/calendar.js`)**:
  - Fully removed the native browser date picker indicator and its browser-specific popups from `#mTxnDate` (Add Transaction modal) and `#eTxnDate` (Edit Transaction modal).
  - Consolidated the date input into a clean, unified interactive component with a single calendar icon that opens exclusively the custom Money Tracker calendar popover.
  - Suppressed `-webkit-calendar-picker-indicator` across the application so no native OS pickers conflict or appear.

## [1.2.0] - 2026-09-18

### Added
- **Financial Calendar & Themed DatePicker (`js/calendar.js`, `css/components.css`)**:
  - Adapted core calendar logic from `trananhtuat/js-calendar` (leap-year calculations, 3x4 month overlay grid, year stepper, day formatting) into an offline, zero-dependency financial calendar engine.
  - **Themed DatePicker Popover**: Replaced plain browser date pickers with an anchored glassmorphic calendar popover for `#mTxnDate` (Add Transaction) and `#eTxnDate` (Edit Transaction). Supports instant date selection, quick presets (`Today`, `Yesterday`), animated month overlay switcher, and year navigation.
  - **Transactions Calendar View**: Added `Table | Calendar` segmented view toggle on the Transactions tab. Displays monthly financial summary pills (Total Inflow, Total Outflow, Net Cashflow) and daily expense/income badges on calendar day cells.
  - **Interactive Day Ledger**: Clicking any calendar date expands an itemized day ledger displaying transactions, totals, quick edit/delete actions, and a 1-tap "+ Add for this Date" shortcut.
  - **Full Dark & Light Theme Synchronization**: Calendar styling automatically binds to the application's global design tokens (`[data-theme="light"]` and OLED dark mode) with high-contrast day numbers, crisp borders, and subtle glassmorphic glow.

## [1.1.4] - 2026-09-18

### Fixed
- **Correctness**: cash/wallet accounts with a zero starting balance and no income were showing a positive "Available Balance" equal to total spend instead of negative (`js/accounts.js`).
- **Data integrity**: deleting an account reassigned its orphaned transactions to an arbitrary other account instead of a dedicated "Unassigned" bucket (`js/accounts.js`).
- **Data safety**: cross-statement duplicate matching no longer auto-merges (permanently deletes a record) on a substring/overlapping UTR match — only an exact reference-number match auto-merges; overlapping matches now go to manual review at 90% confidence (`js/duplicateDetector.js`).
- **Categorization**: fixed a rule-order bug where broader keywords (e.g. "amazon") matched before more specific ones (e.g. "amazon prime"), misclassifying subscriptions as shopping. Matching now prefers the longest/most specific keyword across all rules (`js/categorizer.js`, `CategorizerEngine.kt`).
- **Security**: pinned the previously `@latest` Lucide icons CDN script to a fixed version and added Subresource Integrity (SRI) hashes to all CDN-loaded scripts (`index.html`).
- **Android privacy**: disabled `allowBackup` so the local transactions database is no longer swept into Android's default cloud auto-backup.
- **Android build hygiene**: removed the unused `release` build variant, which was signed with the debug key and unminified — the project only ever ships `assembleDebug` per its release workflow.
- **Web reminder**: added a dismissible dashboard banner nudging users to export a backup if they haven't in 30+ days, since data lives only in browser storage.

## [1.1.3] - 2026-09-18

### Fixed & Improved
- **Statement Feature Cards Layout Redesign (`index.html`, `components.css`)**:
  - Resolved CSS class name collision where upload feature cards previously shared `.profile-card` with the user profile switcher, causing them to inherit constrained widths (`max-width: 148px`), tall vertical padding, and excessive empty space.
  - Implemented dedicated `.statement-features-grid` and `.statement-feature-card` components with a balanced 4-column horizontal layout, compact padding, and distinct accent-color icon containers.
- **Git Exclusions**:
  - Permanently untracked and excluded `AGENTS.md` in `.gitignore` to protect internal repository guidelines.

## [1.1.2] - 2026-09-18

### Fixed
- **Privacy**: Removed hardcoded personal names/contacts from the shared merchant categorizer (`js/categorizer.js`, `CategorizerEngine.kt`) — they were shipping inside the committed source and the built APK. Add them back per-profile via Learned Rules if needed.
- **Security**: PDF report export (`js/export.js`) now HTML-escapes transaction/account fields before rendering, closing an XSS gap that every other render path already guarded against.
- **Android performance**: Transactions list now uses a keyed lazy `itemsIndexed` instead of composing the entire filtered list eagerly inside one `LazyColumn` item — fixes janky scrolling on long transaction histories.
- **Android reliability**: Statement parsing on upload now runs on a background dispatcher instead of the UI thread, preventing ANRs on larger PDF statements.
- **Web dashboard**: "Total Inflow/Outflow (30D)" KPIs were actually computing the current calendar month (near-zero on day 1 of a month); `getBudgetsStatus` now supports a rolling day-range and the dashboard uses it.
- **Web performance**: Duplicate scanner now only writes transactions whose duplicate-related fields actually changed, instead of rewriting the entire transaction store on every scan (init, every upload, and every Duplicates tab open).

## [1.1.1] - 2026-09-18

### Added & Improved
- **Payment Mode Dropdowns & Account Selection**:
  - Replaced freeform text inputs with comprehensive `<select>` dropdowns in both Add and Edit transaction modals across Web and Android (`ExposedDropdownMenuBox`).
  - Included options: `UPI`, `Debit Card`, `Credit Card`, `Bank Transfer`, `NEFT`, `IMPS / RTGS`, `Net Banking`, `Cash`, `Cheque`, `Digital Wallet`, and `Other`.
  - Added robust default fallback accounts (`Primary Bank Account`, `Cash / Wallet`, `Credit Card`) ensuring dropdowns are never empty.
- **DateTime Capsule Card Redesign**:
  - Eliminated native WebKit browser white spin-button artifacts on Windows Chromium browsers (`::-webkit-inner-spin-button`).
  - Added 1-tap quick preset pills (`Now`, `Today`, `Yesterday`) and unified glassmorphic card styling.
- **Automatic Versioning & Distribution Rules**:
  - Established formal development workflow rules in `AGENTS.md` and `.agents/rules/development_workflow.md` adhering to semantic versioning (patch increment for minor fixes, minor increment for features, automated APK compilation to root and archive).

## [1.1.0] - 2026-08-27

### Added
- **Exact 1:1 Feature Parity with Web Application on Native Android**:
  - **Dynamic Dark / Light Mode Theme Toggle**: Instant switching between OLED Dark Mode and Crisp Light Mode with persistent preference storage and live system bar adaptation.
  - **Universal Offline Statement CSV Importer (`UploadScreen.kt`)**: Ingest bank and UPI CSV statements (HDFC, SBI, ICICI, Axis, GPay, PhonePe, Paytm, CRED) with on-device parsing, batch preview, and statement history tracking.
  - **Dedicated Needs Review Screen (`NeedsReviewScreen.kt`)**: Review unclassified expenses with live badge counter and 1-tap "Teach AI & Classify" to memorize merchant/UPI IDs retroactively.
  - **Cross-Statement Duplicate Resolver (`DuplicateResolverScreen.kt`)**: Smart O(1) amount bucketing, exact UTR matching (99%), and date-proximity token scoring with 1-tap "Merge & Enrich" and "Keep Separate" actions.
  - **Smart Categorization & AI Memory (`LearnedRulesScreen.kt`)**: 12 built-in financial categories, auto-stripping of routing codes, and memorized merchant keyword rule manager.
  - **Cards & Accounts (`AccountsScreen.kt`)**: Connected bank accounts, luxury Apple Wallet card carousel with custom theme gradients, and credit limit utilization meters.
  - **Reports & Multi-Format Export Center (`ReportsScreen.kt`)**: Current reconciled snapshot metrics (Inflow, Outflow, Net Retained Savings), raw CSV exports, and full JSON profile backup & restore.
- **Apple Human Interface Guidelines (HIG) Redesign**:
  - 5-tab Cupertino navigation bar (`Dashboard`, `Ledger`, `Upload`, `Review`, `More`) with live badge counters.
  - Translucent glass cards (`HigGlassCard`), Inset Group list containers (`HigInsetGroup`), and spring-animated sliding segmented controls (`HigSegmentedControl`).
- **Brand Consistency**:
  - Vectorized official Money Tracker brand logo (`ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_app_logo.xml`) integrated into app icon and in-app profile header.
- **Automated APK Version Distribution System**:
  - Root `ExpenseTracker.apk` always mirrors the latest build.
  - Dedicated `apks/ExpenseTracker-v1.1.0.apk` archive alongside historical releases.

## [1.0.0] - 2026-08-27


### Added
- **Multi-User Profile System (`profiles.js`)**:
  - Full-screen onboarding & profile chooser overlay with ambient background lighting and micro-animations.
  - Isolated IndexedDB namespaces per profile (`ExpenseTrackerDB_<profileId>`) ensuring 100% data separation and clean-slate initialization.
  - In-app Profile Manager modal with dynamic profile switching, inline profile renaming, and inline deletion confirmation.
  - Sidebar profile pill displaying the active user's name, gradient avatar, and quick profile management access.
  - Curated dual-stop gradient avatar palettes for a luxury financial dashboard feel.
- **Financial Intelligence & Core Dashboard**:
  - Executive Bento-box dashboard with Net Worth / Balance calculation, Inflow/Outflow tracking, and Net Savings Rate KPI.
  - Cash flow chart with cumulative and unified flow modes (7D, 30D, 90D intervals).
  - Categorization engine (`categorizer.js`) with rule learning and category breakdown visualizer.
  - Bank statement parser (`parser.js`) with offline PDF/CSV import and transaction extraction.
  - Cards & accounts management drawer with customizable gradient card themes and balance tracking.
  - Duplicate transaction resolver and needs-review workflow.
  - Financial report export functionality.
- **Design System & Aesthetics**:
  - Comprehensive dark/light luxury theme with frosted glassmorphism, OLED backgrounds, and CSS tokens.
  - Lucide vector iconography throughout all navigation, modals, and actions.
  - Fully accessible inline interactions without disruptive browser dialog popups.
