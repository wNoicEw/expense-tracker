# 💎 Money Tracker — Offline AI Expense Tracker & Financial Intelligence

[![Version](https://img.shields.io/badge/version-1.0.0-10b981.svg?style=flat-square)](https://github.com/wNoicEw/expense-tracker/releases/tag/v1.0.0)
[![Privacy](https://img.shields.io/badge/privacy-100%25%20On--Device-6366f1.svg?style=flat-square)](#-privacy--security-guarantee)
[![License](https://img.shields.io/badge/license-MIT-blue.svg?style=flat-square)](LICENSE)
[![Zero Build](https://img.shields.io/badge/dependencies-Zero%20Build%20Required-06b6d4.svg?style=flat-square)](#-getting-started)

An executive-grade, **local-first financial intelligence dashboard** and expense tracker designed for individuals, freelancers, and multi-user households. Operates **100% client-side in the browser** with zero cloud dependencies, complete data isolation per profile, and automated offline statement parsing.

---

## ✨ Key Features

### 👤 1. Isolated Multi-User Profile Architecture
- **Complete Memory Separation:** Every profile is sandboxed in its own dedicated IndexedDB instance (`ExpenseTrackerDB_<profileId>`).
- **Clean Slate Onboarding:** New accounts start entirely blank — no sample transactions, leaked cards, or shared history.
- **Ambient Profile Chooser:** Full-screen user picker featuring responsive glassmorphic cards, luminous ambient glow orbs, and entrance micro-animations.
- **Instant Profile Manager:** Switch profiles in a click, rename profiles inline without page reloads, and delete profiles with safe inline confirmation.
- **Dual-Stop Gradient Avatars:** High-contrast, luxury gradient avatars tailored for each user profile.

### 📊 2. Executive Financial Intelligence & Bento Dashboard
- **Live Reconciled Portfolio:** Real-time net worth calculation from active cards and accounts.
- **Cash Flow Analytics:** Interactive 7-day, 30-day, and 90-day cash flow views with Cumulative vs. Unified flow modes.
- **KPI Metrics:** Track 30-day Total Inflow, Total Outflow, and Net Savings Rate percentage.
- **Dynamic Category Breakdown:** Visual category distribution chart with instant percentage and volume metrics.

### 📄 3. Intelligent Offline Bank Statement Parsing
- **Local PDF & CSV Extraction:** Ingest bank statements directly within the browser without uploading files to external servers.
- **Regex & Pattern Engine:** Automatically extracts dates, amounts, descriptions, reference numbers, and transaction types.
- **Rule Learning & Auto-Categorization:** Learns from manual reclassifications and automatically categorizes future similar transactions.

### 💳 4. Cards & Accounts Management Drawer
- **Theme Gradients:** Choose customizable luxury credit/debit card gradients (Royal Navy, Emerald Green, Crimson Burgundy, Stealth Onyx, Amber Gold).
- **Balance & Limit Tracking:** Monitor credit card utilization, available limits, and account liquidity.
- **Quick Account Modal:** Add, edit, or reconcile accounts on the fly.

### 🔍 5. Reconciliation & Hygiene Workflows
- **Duplicate Resolver:** Intelligent transaction fingerprinting to identify, review, and merge accidental duplicate statement entries.
- **Needs Review Queue:** Flag ambiguous transactions for manual inspection.
- **Financial Report Export:** Export ledger data to CSV and formatted print/PDF reports.

### 🌓 6. Ultra-Refined Design System
- **OLED Dark & High-Contrast Light Mode:** Tailored design tokens with seamless 1-click theme switching.
- **Frosted Glassmorphism:** Layered card surfaces, subtle borders (`rgba(255,255,255,0.08)`), and fluid hover physics.
- **Zero Disruptive Popups:** Inline interactive feedback and accessible keyboard navigation throughout.

---

## 🛠️ Architecture & Tech Stack

Money Tracker is intentionally built with pure web technologies to ensure lifetime longevity, zero obsolescence, and maximum performance:

| Component | Technology | Purpose |
| :--- | :--- | :--- |
| **Core Structure** | Semantic HTML5 | Clean accessibility and layout hierarchy |
| **Styling & Theme** | Vanilla CSS (CSS Variables) | Frosted glassmorphism, OLED dark mode, responsive grids |
| **Client Database** | IndexedDB API | High-capacity structured storage with per-profile isolation |
| **Profile Routing** | LocalStorage + Dynamic Namespacing | Profile registry, state switching, and DB gating |
| **Visualization** | Chart.js | Interactive cash flow and category breakdown charts |
| **Iconography** | Lucide Icons | Crisp vector UI icons |
| **PDF Processing** | PDF.js | 100% on-device client-side PDF document parsing |

---

## 🚀 Getting Started

Money Tracker requires **no installation, no Node.js runtime, and no build steps**.

### 1. Clone the repository
```bash
git clone https://github.com/wNoicEw/expense-tracker.git
cd expense-tracker
```

### 2. Launch the Application
Simply double-click **`index.html`** or serve it locally with any lightweight server:

```powershell
# Using Python
python -m http.server 8000

# Using VS Code / Live Server or simply open in any browser:
# Double click index.html
```

---

## 🔒 Privacy & Security Guarantee

- **Zero Cloud Storage:** Your financial transactions, account numbers, and bank statements **never leave your device**.
- **No Telemetry / Analytics:** Zero third-party tracking scripts, cookies, or remote logging.
- **Completely Offline Capable:** Works seamlessly without an active internet connection.

---

## 📜 Version History & Changelog

All notable changes and architectural updates are recorded in [**CHANGELOG.md**](CHANGELOG.md).

- **Current Release:** [`v1.0.0`](https://github.com/wNoicEw/expense-tracker/releases/tag/v1.0.0) — *Multi-User Isolated Memory Architecture & Executive Financial Intelligence Dashboard.*

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
