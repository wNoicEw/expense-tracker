# 💎 Money Tracker — Offline AI Expense Tracker & Financial Intelligence

[![Version](https://img.shields.io/badge/version-1.6.1-10b981.svg?style=flat-square)](https://github.com/wNoicew/expense-tracker/releases/tag/v1.6.1)
[![Android](https://img.shields.io/badge/android-v1.6.1%20(SDK%2035)-3DDC84.svg?style=flat-square&logo=android&logoColor=white)](#-native-android-application-v161)
[![Privacy](https://img.shields.io/badge/privacy-100%25%20On--Device-6366f1.svg?style=flat-square)](#-privacy--security-guarantee)
[![License](https://img.shields.io/badge/license-MIT-blue.svg?style=flat-square)](LICENSE)

An executive-grade, **local-first financial intelligence suite** and offline expense tracker available as both a **zero-dependency Web Application** and a **Native Android Application** (Jetpack Compose, Apple HIG design language, Android 15 SDK 35). Operates **100% client-side on-device** with zero cloud dependencies, complete database isolation per user profile, cross-statement duplicate reconciliation, smart rule auto-categorization, category budgeting, and multi-currency intelligence.

---

## 📱 Native Android Application (v1.6.1)

The native Android app brings 100% feature parity with the browser application into a modern mobile experience:

- **UPI Statement Engine (Google Pay & Navi) & Exact Account Attribution**: Automatic offline parsing for multi-page Google Pay and Navi statements, resolving glued bank accounts (e.g. `HDFC Bank - 1234`), internal self-transfers, 2-digit masked RuPay credit cards (`XX99`), incoming transfer receipts (`Paid to <Bank>`), and clean title categorization without boilerplate watermarks.
- **Refund Auto-Detection & Dedicated Ledger Filtering**: Auto-detects refunds and reversal credits across bank and card statements (Navi, Paytm, generic CSV/PDF), categorizing them as dedicated `refund` transactions (inflow) with 1-tap segment filtering in the Android ledger.
- **Form Keyboard Insets & Accessible Navigation**: Full soft-keyboard inset handling (`imePadding`) and scrollability across all modal bottom sheets, plus WCAG 2.1 AA accessible focus trapping across Web dialogs.
- **Bi-Directional Web & Android Backup Interoperability**: Seamlessly restore Web application JSON backups directly on Android and vice-versa, with automated ISO date ↔ epoch millisecond normalization and schema cross-compatibility.
- **144Hz High-Refresh-Rate Fluidity**: Hardware window locking to maximum display refresh rate (`144Hz`, `120Hz`, `90Hz`), single-pass radial gradient backlights (zero GPU blur overhead), and ThreadLocal zero-allocation number formatting for silky-smooth, jitter-free scrolling.
- **Currency Information & Exchange Rates Hub (Android & Web)**: Dedicated hub accessible via the *More* tab on Android and sidebar on Web. Displays live exchange rates for 6 global currencies (`INR`, `USD`, `EUR`, `GBP`, `CHF`, `JPY`) relative to USD and dynamically calculated against the active profile's primary currency, last updated timestamp, force API sync button with rotating animation, manual rate overrides with live preview, and one-tap API reset.
- **Ambient Backlit Currency Design**: Apple HIG + `ui-ux-pro-max` inspired currency cards featuring glowing ambient neon backlight halos tailored per currency (Emerald Green for INR, Electric Blue for USD, Royal Violet for EUR, Rose Crimson for GBP, Alpine Cyan for CHF, Golden Amber for JPY).
- **Apple Human Interface Guidelines (HIG) Design**: Translucent glass surfaces (`HigGlassCard`), iOS-style spring sliding segmented controls (`HigSegmentedControl`), Inset Group containers (`HigInsetGroup`), and Apple Wallet card carousels.
- **Financial Calendar Month-View & Interactive Day-Ledger**: Full calendar grid with monthly inflow/outflow cards, touch-optimized day cells, income/expense indicator pills, and an interactive day-ledger with a 1-tap "Add for this Date" shortcut.
- **On-Device Backup Reminder Banner**: Proactive Apple HIG backup banner alerting the user when local data hasn't been backed up in the last 30 days.
- **Universal Statement CSV & Document Picker**: Ingest bank & UPI statements with Android SAF `OpenDocument` supporting all PDF and CSV formats, with in-memory byte caching for password-protected statements.
- **Cross-Statement Duplicate Resolver**: 99% UTR reference matching and ±24h date-proximity scoring with 1-tap "Merge & Enrich".
- **Dynamic Rule Engine**: Automatic UPI handle stripping, 12 built-in financial categories, and retroactive ledger reclassification.
- **Category Budgets & Health Scoring**: Visual progress allowances and real-time Financial Health Score (0–100).
- **Direct APK Distribution**:
  - **Main Root (Always Latest)**: [**`ExpenseTracker.apk`**](ExpenseTracker.apk)
  - **Version Archive**: [**`apks/`**](apks/) (Emergency fallback versions: `v1.4.2`, `v1.5.0`, `v1.5.1`, `v1.6.0`, `v1.6.1`)

---

## ✨ Key Features

### 👤 1. Isolated Multi-User Profile Architecture
- **Complete Memory Separation:** Every profile is sandboxed in its own dedicated database instance (IndexedDB in Web, isolated Room SQLite `ExpenseTrackerDB_<profileId>` on Android).
- **Clean Slate Onboarding:** New accounts start entirely blank — no sample transactions, leaked cards, or shared history.
- **Ambient Profile Chooser:** Full-screen user picker featuring responsive glassmorphic cards, luminous ambient glow orbs, and entrance micro-animations.
- **Instant Profile Manager:** Switch profiles in a click, rename profiles inline without page reloads, and delete profiles with safe inline confirmation.
- **Dual-Stop Gradient Avatars:** High-contrast, luxury gradient avatars tailored for each user profile.

### 📊 2. Executive Financial Intelligence & Bento Dashboard
- **Live Reconciled Portfolio:** Real-time net worth calculation from active cards and accounts.
- **Cash Flow Analytics:** Interactive 7-day, 30-day, and 90-day cash flow views with Cumulative vs. Unified flow modes.
- **KPI Metrics:** Track 30-day Total Inflow, Total Outflow, Net Savings Rate percentage, and Financial Health score.
- **Dynamic Category Breakdown:** Visual category distribution chart with instant percentage and volume metrics.

### 📄 3. Intelligent Offline Bank Statement Parsing
- **Local PDF & CSV Extraction:** Ingest bank statements directly without uploading files to external servers.
- **Regex & Pattern Engine:** Automatically extracts dates, amounts, descriptions, reference numbers, and transaction types.
- **Rule Learning & Auto-Categorization:** Learns from manual reclassifications and automatically categorizes future similar transactions.

### 💳 4. Cards & Accounts Management
- **Theme Gradients:** Choose customizable luxury credit/debit card gradients (Royal Navy, Emerald Green, Crimson Burgundy, Stealth Onyx, Amber Gold).
- **Balance & Limit Tracking:** Monitor credit card utilization, available limits, and account liquidity.
- **Quick Account Modal:** Add, edit, or reconcile accounts on the fly.

### 🔍 5. Reconciliation & Hygiene Workflows
- **Duplicate Resolver:** Intelligent transaction fingerprinting to identify, review, and merge accidental duplicate statement entries.
- **Needs Review Queue:** Flag ambiguous transactions for manual inspection.
- **Financial Report Export:** Export ledger data to CSV and encrypted JSON profile backups.

---

## 🛠️ Architecture & Tech Stack

| Platform | Technology | Features |
| :--- | :--- | :--- |
| **Web App** | HTML5, Vanilla CSS Variables, IndexedDB, Chart.js, Lucide Icons, PDF.js | Zero build step, 100% offline, privacy first |
| **Android App** | Kotlin 2.1, Jetpack Compose (Material 3), Room Database v2, Coroutines Flow | Apple HIG design tokens, Android 15 (SDK 35), Edge-to-Edge |

---

## 🚀 Getting Started

### Web Application
Simply double-click **`index.html`** or serve it with any lightweight server:
```powershell
python -m http.server 8000
```

### Android Application
Open the **`android/`** folder in Android Studio, or build from the command line:
```powershell
cd android
.\gradlew.bat assembleDebug
```
The compiled APK will automatically be placed at the root [`ExpenseTracker.apk`](ExpenseTracker.apk) and archived into [`apks/`](apks/).

---

## 🔒 Privacy & Security Guarantee

- **Zero Cloud Storage:** Your financial transactions, account numbers, and bank statements **never leave your device**.
- **No Telemetry / Analytics:** Zero third-party tracking scripts, cookies, or remote logging.
- **Completely Offline Capable:** Works seamlessly without an active internet connection.

---

## 📜 Version History & Changelog

All notable changes and architectural updates are recorded in [**CHANGELOG.md**](CHANGELOG.md).

- **Current Release:** [`v1.4.2`](https://github.com/wNoicEw/expense-tracker/releases/tag/v1.4.2) — *Real-statement fixes (Navi bill payments no longer dropped, SBI account detection, credit-card bill payments as transfers), plus the data-correctness, backup/restore and accessibility work from 1.4.1.*

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
