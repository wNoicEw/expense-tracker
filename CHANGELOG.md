# Changelog

All notable changes to **Money Tracker (Offline AI Expense Tracker & Financial Intelligence)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
- No calendar month-view / day-ledger on the Transactions screen (Android is table-only).
- No "back up your data" reminder banner on Android.
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
