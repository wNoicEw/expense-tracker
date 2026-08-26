/**
 * ProfileManager — Multi-User Profile System
 * Profiles are stored in localStorage. Each profile gets its own isolated IndexedDB.
 * Zero cross-contamination: switching profiles re-opens a completely different database.
 */

const PROFILES_KEY = 'money_tracker_profiles';
const ACTIVE_PROFILE_KEY = 'money_tracker_active_profile';

// Curated gradient pairs for ultra-luxurious avatar rings & backgrounds
const AVATAR_COLORS = [
  'linear-gradient(135deg, #6366f1, #8b5cf6)', // Indigo -> Violet
  'linear-gradient(135deg, #10b981, #059669)', // Emerald -> Forest
  'linear-gradient(135deg, #3b82f6, #06b6d4)', // Blue -> Cyan
  'linear-gradient(135deg, #f59e0b, #d97706)', // Amber -> Gold
  'linear-gradient(135deg, #ec4899, #f43f5e)', // Pink -> Rose
  'linear-gradient(135deg, #8b5cf6, #d946ef)', // Purple -> Fuchsia
  'linear-gradient(135deg, #14b8a6, #0ea5e9)', // Teal -> Sky
  'linear-gradient(135deg, #f97316, #ef4444)'  // Orange -> Coral
];

class ProfileManager {
  constructor() {
    this._profiles = null; // cached
  }

  // --- Persistence Helpers ---

  _load() {
    try {
      const raw = localStorage.getItem(PROFILES_KEY);
      this._profiles = raw ? JSON.parse(raw) : [];
    } catch {
      this._profiles = [];
    }
    return this._profiles;
  }

  _save() {
    localStorage.setItem(PROFILES_KEY, JSON.stringify(this._profiles));
  }

  // --- Public API ---

  /**
   * Returns all saved profiles as an array.
   * @returns {{ id: string, name: string, color: string, initial: string, createdAt: string, lastUsedAt: string }[]}
   */
  getProfiles() {
    return this._load();
  }

  /**
   * Returns the currently active profile object, or null.
   */
  getActiveProfile() {
    const id = localStorage.getItem(ACTIVE_PROFILE_KEY);
    if (!id) return null;
    return this._load().find(p => p.id === id) || null;
  }

  /**
   * Returns true if there is a valid active profile set.
   */
  hasActiveProfile() {
    return this.getActiveProfile() !== null;
  }

  /**
   * Creates a new profile, sets it as active, and reloads the page.
   * @param {string} name - Display name for the profile.
   */
  createProfile(name) {
    name = (name || '').trim();
    if (!name) throw new Error('Profile name cannot be empty.');
    if (name.length > 32) throw new Error('Profile name must be 32 characters or fewer.');

    const profiles = this._load();

    // Prevent exact duplicate names (case-insensitive)
    if (profiles.some(p => p.name.toLowerCase() === name.toLowerCase())) {
      throw new Error(`A profile named "${name}" already exists.`);
    }

    const id = 'user_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6);
    const initial = name.trim()[0].toUpperCase();
    const color = AVATAR_COLORS[profiles.length % AVATAR_COLORS.length];

    const profile = {
      id,
      name,
      initial,
      color,
      createdAt: new Date().toISOString(),
      lastUsedAt: new Date().toISOString()
    };

    profiles.push(profile);
    this._profiles = profiles;
    this._save();

    // Activate and reload
    this._setActive(id);
    location.reload();
  }

  /**
   * Switches to the given profile and reloads.
   * @param {string} id
   */
  switchProfile(id) {
    const profiles = this._load();
    const profile = profiles.find(p => p.id === id);
    if (!profile) throw new Error('Profile not found.');

    // Update lastUsedAt
    profile.lastUsedAt = new Date().toISOString();
    this._profiles = profiles;
    this._save();

    this._setActive(id);
    location.reload();
  }

  /**
   * Deletes a profile and its entire IndexedDB. If it was the active profile,
   * switches to the first remaining profile (or shows the chooser if none left).
   * @param {string} id
   */
  async deleteProfile(id) {
    const profiles = this._load();
    const profile = profiles.find(p => p.id === id);
    if (!profile) return;

    // Delete the IndexedDB for this profile
    const dbName = `ExpenseTrackerDB_${id}`;
    await new Promise((resolve) => {
      const req = indexedDB.deleteDatabase(dbName);
      req.onsuccess = resolve;
      req.onerror = resolve; // resolve anyway
      req.onblocked = resolve;
    });

    // Remove from list
    this._profiles = profiles.filter(p => p.id !== id);
    this._save();

    const activeId = localStorage.getItem(ACTIVE_PROFILE_KEY);
    if (activeId === id) {
      // Switch to the first remaining profile or clear active
      if (this._profiles.length > 0) {
        this._setActive(this._profiles[0].id);
      } else {
        localStorage.removeItem(ACTIVE_PROFILE_KEY);
      }
    }

    location.reload();
  }

  /**
   * Renames an existing profile. Updates name and initial, re-saves.
   * Does NOT trigger a reload — caller updates the UI live.
   * @param {string} id
   * @param {string} newName
   */
  renameProfile(id, newName) {
    newName = (newName || '').trim();
    if (!newName) throw new Error('Name cannot be empty.');
    if (newName.length > 32) throw new Error('Name must be 32 characters or fewer.');

    const profiles = this._load();
    const profile = profiles.find(p => p.id === id);
    if (!profile) throw new Error('Profile not found.');

    // Duplicate check (exclude self)
    const duplicate = profiles.find(p => p.id !== id && p.name.toLowerCase() === newName.toLowerCase());
    if (duplicate) throw new Error(`A profile named "${newName}" already exists.`);

    profile.name = newName;
    profile.initial = newName[0].toUpperCase();
    this._profiles = profiles;
    this._save();
    return profile;
  }

  /**
   * Returns the IndexedDB database name for the given profile ID.
   */
  getDbName(profileId) {
    return `ExpenseTrackerDB_${profileId}`;
  }

  /**
   * Returns the database name for the currently active profile.
   * Falls back to a default name if no profile is active (should not happen in normal flow).
   */
  getActiveDbName() {
    const id = localStorage.getItem(ACTIVE_PROFILE_KEY);
    return id ? `ExpenseTrackerDB_${id}` : 'ExpenseTrackerDB_default';
  }

  // --- Private ---
  _setActive(id) {
    localStorage.setItem(ACTIVE_PROFILE_KEY, id);
  }
}

// Global instance — created BEFORE db.js so the DB name can be resolved
window.profileManager = new ProfileManager();
