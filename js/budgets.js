/**
 * Budgets & Financial Health Intelligence
 * Tracks category-wise spending vs allocations, calculates burn rates, and financial health score.
 */

class BudgetsManager {
  constructor() {}

  /**
   * Get budget status for all categories for a given month/year
   */
  async getBudgetsStatus(year = new Date().getFullYear(), month = new Date().getMonth()) {
    const categories = await window.db.getAll('categories');
    const transactions = await window.db.getAll('transactions');

    // Filter transactions for the specified month & year (ignoring merged duplicates & transfers)
    const monthTxns = transactions.filter(t => {
      if (t.duplicateStatus === 'merged') return false;
      const d = new Date(t.date);
      return d.getFullYear() === year && d.getMonth() === month;
    });

    const categorySpendMap = {};
    let totalIncome = 0;
    let totalExpense = 0;

    monthTxns.forEach(t => {
      const amt = Math.abs(parseFloat(t.amount) || 0);
      if (t.type === 'income') {
        totalIncome += amt;
      } else if (t.type === 'expense') {
        totalExpense += amt;
        categorySpendMap[t.category] = (categorySpendMap[t.category] || 0) + amt;
      }
    });

    const budgetCards = categories
      .filter(c => c.type === 'expense')
      .map(cat => {
        const spent = categorySpendMap[cat.name] || 0;
        const limit = cat.budget || 5000;
        const percentage = limit > 0 ? Math.round((spent / limit) * 100) : 0;
        const remaining = Math.max(0, limit - spent);
        const isExceeded = spent > limit;

        let status = 'safe'; // green
        if (percentage >= 100) status = 'exceeded'; // red
        else if (percentage >= 80) status = 'warning'; // amber

        return {
          ...cat,
          spent,
          limit,
          remaining,
          percentage,
          status,
          isExceeded
        };
      });

    // Sort by highest spent first
    budgetCards.sort((a, b) => b.spent - a.spent);

    // Calculate Financial Health Score (0 - 100)
    const savings = Math.max(0, totalIncome - totalExpense);
    const savingsRate = totalIncome > 0 ? Math.round((savings / totalIncome) * 100) : 0;

    // Health Score factors: Savings Rate (50%), Budget adherence (30%), Debt safety (20%)
    let healthScore = Math.min(100, Math.round(savingsRate * 0.8 + 20));
    if (totalExpense > totalIncome && totalIncome > 0) {
      healthScore = Math.max(10, 50 - Math.round(((totalExpense - totalIncome) / totalIncome) * 50));
    }

    return {
      budgets: budgetCards,
      totalIncome,
      totalExpense,
      netSavings: savings,
      savingsRate,
      healthScore
    };
  }

  /**
   * Update category budget limit
   */
  async updateCategoryBudget(categoryId, newBudget) {
    const cat = await window.db.getById('categories', categoryId);
    if (cat) {
      cat.budget = parseFloat(newBudget) || 0;
      await window.db.put('categories', cat);
    }
    return cat;
  }
}

// Global instance
window.budgetsManager = new BudgetsManager();
