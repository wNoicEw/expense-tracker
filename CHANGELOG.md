# Changelog

All notable changes to **Money Tracker (Offline AI Expense Tracker & Financial Intelligence)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
