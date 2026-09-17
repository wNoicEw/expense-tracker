# Changelog

All notable changes to **Money Tracker (Offline AI Expense Tracker & Financial Intelligence)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
