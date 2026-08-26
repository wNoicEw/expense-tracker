# Changelog

All notable changes to **Money Tracker (Offline AI Expense Tracker & Financial Intelligence)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

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
