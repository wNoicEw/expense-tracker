/**
 * Offline IndexedDB Storage Engine
 * 100% Client-Side Local Persistence — Namespaced per user profile.
 * The database name is dynamically resolved from the active profile,
 * ensuring complete data isolation between users.
 */

// Resolve DB name from the active profile (profiles.js must load first)
const DB_NAME = window.profileManager
  ? window.profileManager.getActiveDbName()
  : 'ExpenseTrackerDB_default';

const DB_VERSION = 1;

class Database {
  constructor() {
    this.db = null;
    this.isReady = false;
  }

  async init() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);

      request.onupgradeneeded = (event) => {
        const db = event.target.result;

        // Transactions Store
        if (!db.objectStoreNames.contains('transactions')) {
          const txnStore = db.createObjectStore('transactions', { keyPath: 'id' });
          txnStore.createIndex('date', 'date', { unique: false });
          txnStore.createIndex('category', 'category', { unique: false });
          txnStore.createIndex('accountId', 'accountId', { unique: false });
          txnStore.createIndex('type', 'type', { unique: false });
          txnStore.createIndex('referenceNo', 'referenceNo', { unique: false });
          txnStore.createIndex('isDuplicate', 'isDuplicate', { unique: false });
          txnStore.createIndex('needsReview', 'needsReview', { unique: false });
        }

        // Accounts Store
        if (!db.objectStoreNames.contains('accounts')) {
          db.createObjectStore('accounts', { keyPath: 'id' });
        }

        // Categories Store
        if (!db.objectStoreNames.contains('categories')) {
          db.createObjectStore('categories', { keyPath: 'id' });
        }

        // Budgets Store
        if (!db.objectStoreNames.contains('budgets')) {
          db.createObjectStore('budgets', { keyPath: 'id' });
        }

        // Categorization Rules Store
        if (!db.objectStoreNames.contains('rules')) {
          db.createObjectStore('rules', { keyPath: 'id' });
        }

        // Statement Upload History Store
        if (!db.objectStoreNames.contains('statements')) {
          db.createObjectStore('statements', { keyPath: 'id' });
        }
      };

      request.onsuccess = async (event) => {
        this.db = event.target.result;
        this.isReady = true;
        await this.seedDefaultCategories();
        resolve(this);
      };

      request.onerror = (event) => {
        console.error('IndexedDB init error:', event.target.error);
        reject(event.target.error);
      };
    });
  }

  /**
   * Seeds default spending & income categories needed by the AI auto-classifier.
   * Accounts are intentionally NOT seeded — every profile starts with zero accounts.
   * Transactions, statements, and rules are also empty on a fresh profile.
   */
  async seedDefaultCategories() {
    const existingCategories = await this.getAll('categories');
    if (existingCategories.length === 0) {
      const defaultCategories = [
        { id: 'cat_food', name: 'Food & Dining', icon: 'utensils', color: '#f59e0b', type: 'expense', budget: 12000 },
        { id: 'cat_groceries', name: 'Groceries & Mart', icon: 'shopping-cart', color: '#10b981', type: 'expense', budget: 10000 },
        { id: 'cat_shopping', name: 'Shopping & E-Comm', icon: 'shopping-bag', color: '#ec4899', type: 'expense', budget: 15000 },
        { id: 'cat_travel', name: 'Travel & Commute', icon: 'car', color: '#06b6d4', type: 'expense', budget: 6000 },
        { id: 'cat_utilities', name: 'Bills & Utilities', icon: 'zap', color: '#8b5cf6', type: 'expense', budget: 8000 },
        { id: 'cat_subscriptions', name: 'Subscriptions & OTT', icon: 'film', color: '#ef4444', type: 'expense', budget: 2500 },
        { id: 'cat_health', name: 'Health & Pharmacy', icon: 'heart-pulse', color: '#14b8a6', type: 'expense', budget: 4000 },
        { id: 'cat_investments', name: 'Investments & SIP', icon: 'trending-up', color: '#3b82f6', type: 'expense', budget: 25000 },
        { id: 'cat_rent', name: 'Rent & Housing', icon: 'home', color: '#6366f1', type: 'expense', budget: 22000 },
        { id: 'cat_salary', name: 'Salary & Professional', icon: 'briefcase', color: '#22c55e', type: 'income', budget: 0 },
        { id: 'cat_freelance', name: 'Freelance & Side Hustle', icon: 'code', color: '#06b6d4', type: 'income', budget: 0 },
        { id: 'cat_transfers', name: 'Transfers & CC Bill', icon: 'arrow-left-right', color: '#94a3b8', type: 'transfer', budget: 0 },
        { id: 'cat_uncategorized', name: 'Uncategorized', icon: 'help-circle', color: '#94a3b8', type: 'expense', budget: 0 },
        { id: 'cat_other', name: 'Miscellaneous', icon: 'circle-dot', color: '#64748b', type: 'expense', budget: 5000 }
      ];

      for (const cat of defaultCategories) {
        await this.put('categories', cat);
      }
    }
  }

  // Generic DB Operations
  async getAll(storeName) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(storeName, 'readonly');
      const store = tx.objectStore(storeName);
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => reject(req.error);
    });
  }

  async getById(storeName, id) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(storeName, 'readonly');
      const store = tx.objectStore(storeName);
      const req = store.get(id);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async put(storeName, item) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(storeName, 'readwrite');
      const store = tx.objectStore(storeName);
      const req = store.put(item);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async putBatch(storeName, items) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(storeName, 'readwrite');
      const store = tx.objectStore(storeName);
      items.forEach(item => store.put(item));
      tx.oncomplete = () => resolve(items.length);
      tx.onerror = () => reject(tx.error);
    });
  }

  async delete(storeName, id) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(storeName, 'readwrite');
      const store = tx.objectStore(storeName);
      const req = store.delete(id);
      req.onsuccess = () => resolve(true);
      req.onerror = () => reject(req.error);
    });
  }

  async clearStore(storeName) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(storeName, 'readwrite');
      const store = tx.objectStore(storeName);
      const req = store.clear();
      req.onsuccess = () => resolve(true);
      req.onerror = () => reject(req.error);
    });
  }

  async clearAll() {
    const storeNames = Array.from(this.db.objectStoreNames);
    await new Promise((resolve, reject) => {
      const tx = this.db.transaction(storeNames, 'readwrite');
      storeNames.forEach(s => {
        tx.objectStore(s).clear();
      });
      tx.oncomplete = () => resolve(true);
      tx.onerror = (e) => reject(tx.error || e);
      tx.onabort = (e) => reject(tx.error || e);
    });
    await this.seedDefaultCategories();
  }
}

// Global instance
window.db = new Database();
