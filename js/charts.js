/**
 * High-Performance Chart.js Analytics Engine
 * Features:
 * 1. Unified Income (+ve) vs Expense (-ve) Net Cashflow Graph with Zero Baseline (Breakeven / Overspend detector)
 * 2. Cumulative Net Savings & Deficit Trajectory
 * 3. Category Expense Donut with center total
 * 4. Multi-Account Spend & Balance Trajectory
 * 5. High-Resolution PNG Exports
 */

class ChartsEngine {
  constructor() {
    this.instances = {};
    if (window.Chart) {
      Chart.defaults.font.family = "'Outfit', 'Plus Jakarta Sans', sans-serif";
      Chart.defaults.color = '#94a3b8';
    }
  }

  /**
   * Helper to format currency for chart tooltips & labels
   */
  formatCurrency(value) {
    const isNeg = Number(value) < 0;
    const absVal = Math.abs(Number(value) || 0);
    const formatted = '₹' + absVal.toLocaleString('en-IN', {
      maximumFractionDigits: 0
    });
    return isNeg ? `-${formatted}` : formatted;
  }

  /**
   * 1. Unified Net Income (+ve) & Expense (-ve) Graph with Zero Baseline
   * - Income is positive (+ve, above 0)
   * - Expense is negative (-ve, below 0)
   * - Net Curve crosses 0: above 0 = Savings/Surplus, below 0 = Overspending/Deficit
   */
  renderCashflowChart(canvasId, transactions, daysRange = 30, viewMode = 'cumulative') {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;

    if (this.instances[canvasId]) {
      this.instances[canvasId].destroy();
    }

    const validTxns = transactions.filter(t => t.duplicateStatus !== 'merged');

    // Group by date
    const dateMap = {};
    const now = new Date();
    
    for (let i = daysRange - 1; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
      const dateStr = d.toISOString().split('T')[0];
      dateMap[dateStr] = { 
        income: 0, 
        expense: 0, 
        net: 0,
        cumulative: 0,
        display: d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) 
      };
    }

    validTxns.forEach(t => {
      if (dateMap[t.date]) {
        const amt = Math.abs(parseFloat(t.amount) || 0);
        if (t.type === 'income') dateMap[t.date].income += amt;
        else if (t.type === 'expense') dateMap[t.date].expense += amt;
      }
    });

    let runningTotal = 0;
    Object.keys(dateMap).forEach(k => {
      dateMap[k].net = dateMap[k].income - dateMap[k].expense;
      runningTotal += dateMap[k].net;
      dateMap[k].cumulative = runningTotal;
    });

    const labels = Object.values(dateMap).map(v => v.display);
    const incomeData = Object.values(dateMap).map(v => v.income);
    const expenseDataNeg = Object.values(dateMap).map(v => -v.expense); // Negative for expense below 0
    const netData = Object.values(dateMap).map(v => v.net);
    const cumulativeData = Object.values(dateMap).map(v => v.cumulative);

    const chartCtx = ctx.getContext('2d');

    // Calculate max scale value
    const maxVal = Math.max(...incomeData, ...expenseDataNeg.map(v => Math.abs(v)), 1000);

    let datasets = [];

    if (viewMode === 'cumulative') {
      // Cumulative Net Position over time (Surplus vs Deficit)
      const gradSurplus = chartCtx.createLinearGradient(0, 0, 0, 300);
      gradSurplus.addColorStop(0, 'rgba(16, 185, 129, 0.4)');
      gradSurplus.addColorStop(1, 'rgba(16, 185, 129, 0.05)');

      datasets = [
        {
          type: 'line',
          label: 'Cumulative Flow (₹)',
          data: cumulativeData,
          borderColor: (c) => {
            const val = c.raw;
            return val >= 0 ? '#10b981' : '#f43f5e';
          },
          segment: {
            borderColor: (ctx) => (ctx.p1.parsed.y >= 0 ? '#10b981' : '#f43f5e'),
            backgroundColor: (ctx) => (ctx.p1.parsed.y >= 0 ? 'rgba(16, 185, 129, 0.15)' : 'rgba(244, 63, 94, 0.15)')
          },
          backgroundColor: gradSurplus,
          fill: { target: 'origin' },
          borderWidth: 3,
          tension: 0.35,
          pointRadius: 4,
          pointHoverRadius: 7,
          pointBackgroundColor: (c) => c.raw >= 0 ? '#10b981' : '#f43f5e'
        }
      ];
    } else {
      // UNIFIED VIEW: Income Line (+ve above 0) and Expense Line (-ve below 0) with zero baseline
      const incomeGrad = chartCtx.createLinearGradient(0, 0, 0, 300);
      incomeGrad.addColorStop(0, 'rgba(16, 185, 129, 0.35)');
      incomeGrad.addColorStop(1, 'rgba(16, 185, 129, 0.0)');

      const expenseGrad = chartCtx.createLinearGradient(0, 0, 0, 300);
      expenseGrad.addColorStop(0, 'rgba(244, 63, 94, 0.35)');
      expenseGrad.addColorStop(1, 'rgba(244, 63, 94, 0.0)');

      datasets = [
        {
          type: 'line',
          label: 'Income (+ve)',
          data: incomeData,
          borderColor: '#10b981',
          backgroundColor: incomeGrad,
          fill: { target: 'origin' },
          borderWidth: 2.5,
          tension: 0.38,
          pointBackgroundColor: '#10b981',
          pointRadius: 3.5,
          pointHoverRadius: 6
        },
        {
          type: 'line',
          label: 'Expense (-ve)',
          data: expenseDataNeg,
          borderColor: '#f43f5e',
          backgroundColor: expenseGrad,
          fill: { target: 'origin' },
          borderWidth: 2.5,
          tension: 0.38,
          pointBackgroundColor: '#f43f5e',
          pointRadius: 3.5,
          pointHoverRadius: 6
        }
      ];
    }

    const isLight = document.documentElement.getAttribute('data-theme') === 'light';

    this.instances[canvasId] = new Chart(ctx, {
      type: 'line',
      data: {
        labels: labels,
        datasets: datasets
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
          mode: 'index',
          intersect: false
        },
        plugins: {
          legend: {
            display: false
          },
          tooltip: {
            backgroundColor: isLight ? 'rgba(255, 255, 255, 0.98)' : 'rgba(15, 23, 42, 0.96)',
            titleColor: isLight ? '#0f172a' : '#f8fafc',
            bodyColor: isLight ? '#334155' : '#cbd5e1',
            borderColor: isLight ? 'rgba(0, 0, 0, 0.12)' : 'rgba(255, 255, 255, 0.12)',
            borderWidth: 1,
            padding: 12,
            cornerRadius: 10,
            callbacks: {
              label: (item) => {
                const label = item.dataset.label || '';
                const val = item.raw;
                const formatted = this.formatCurrency(val);
                if (label.includes('Expense')) {
                  return ` Expense: -${this.formatCurrency(Math.abs(val))}`;
                }
                if (label.includes('Income')) {
                  return ` Income: +${this.formatCurrency(val)}`;
                }
                if (label.includes('Net Balance')) {
                  const status = val >= 0 ? '🟢 Surplus (+)' : '🔴 Deficit (−)';
                  return ` Net Flow: ${formatted} [${status}]`;
                }
                return ` ${label}: ${formatted}`;
              }
            }
          }
        },
        scales: {
          x: {
            grid: { color: isLight ? 'rgba(0, 0, 0, 0.04)' : 'rgba(255, 255, 255, 0.03)' },
            ticks: { color: isLight ? '#64748b' : '#64748b', font: { size: 11 } }
          },
          y: {
            grid: {
              color: (context) => {
                // Highlight the Zero Baseline prominently!
                if (context.tick && context.tick.value === 0) {
                  return isLight ? 'rgba(15, 23, 42, 0.65)' : 'rgba(255, 255, 255, 0.45)'; // High-contrast zero line
                }
                return isLight ? 'rgba(0, 0, 0, 0.05)' : 'rgba(255, 255, 255, 0.04)';
              },
              lineWidth: (context) => {
                if (context.tick && context.tick.value === 0) return 2;
                return 1;
              }
            },
            ticks: {
              color: (context) => {
                if (context.tick && context.tick.value === 0) return isLight ? '#0f172a' : '#f8fafc';
                if (context.tick && context.tick.value > 0) return isLight ? '#059669' : '#10b981';
                return isLight ? '#e11d48' : '#f43f5e';
              },
              font: { size: 11, weight: (context) => context.tick && context.tick.value === 0 ? '700' : '500' },
              callback: (val) => {
                if (val === 0) return '₹ 0 (Breakeven)';
                return (val > 0 ? '+' : '') + this.formatCurrency(val);
              }
            }
          }
        }
      }
    });

    // Update Dashboard Net Flow Status Badge
    const netBadge = document.getElementById('cashflowNetBadge');
    if (netBadge) {
      const currentNet = runningTotal;
      if (currentNet >= 0) {
        netBadge.className = 'badge-tag income';
        netBadge.innerHTML = `🟢 Balance: +₹ ${currentNet.toLocaleString('en-IN')}`;
      } else {
        netBadge.className = 'badge-tag expense';
        netBadge.innerHTML = `🔴 Balance: -₹ ${Math.abs(currentNet).toLocaleString('en-IN')}`;
      }
    }
  }

  /**
   * 2. Category Expense Donut Chart
   */
  renderCategoryDonutChart(canvasId, transactions) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;

    if (this.instances[canvasId]) {
      this.instances[canvasId].destroy();
    }

    const validTxns = transactions.filter(t => t.type === 'expense' && t.duplicateStatus !== 'merged');
    const categoryTotals = {};

    validTxns.forEach(t => {
      const cat = t.category || 'Miscellaneous';
      categoryTotals[cat] = (categoryTotals[cat] || 0) + (parseFloat(t.amount) || 0);
    });

    const sortedCats = Object.entries(categoryTotals).sort((a, b) => b[1] - a[1]);
    const labels = sortedCats.map(c => c[0]);
    const data = sortedCats.map(c => c[1]);
    const totalSpend = data.reduce((a, b) => a + b, 0);

    const colors = [
      '#f59e0b', '#10b981', '#ec4899', '#06b6d4', 
      '#8b5cf6', '#ef4444', '#14b8a6', '#3b82f6', 
      '#6366f1', '#84cc16', '#64748b'
    ];

    const isLight = document.documentElement.getAttribute('data-theme') === 'light';
    const isEmpty = labels.length === 0;

    this.instances[canvasId] = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: isEmpty ? ['No Expenses'] : labels,
        datasets: [{
          data: isEmpty ? [1] : data,
          backgroundColor: isEmpty ? [(isLight ? 'rgba(0, 0, 0, 0.08)' : 'rgba(255, 255, 255, 0.06)')] : colors.slice(0, labels.length),
          borderColor: isLight ? '#ffffff' : '#0f172a',
          borderWidth: isEmpty ? 0 : 3,
          hoverOffset: isEmpty ? 0 : 8
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '72%',
        plugins: {
          legend: { display: false },
          tooltip: {
            enabled: !isEmpty,
            backgroundColor: isLight ? 'rgba(255, 255, 255, 0.98)' : 'rgba(15, 23, 42, 0.95)',
            titleColor: isLight ? '#0f172a' : '#f8fafc',
            bodyColor: isLight ? '#334155' : '#cbd5e1',
            borderColor: isLight ? 'rgba(0, 0, 0, 0.12)' : 'rgba(255, 255, 255, 0.1)',
            borderWidth: 1,
            padding: 12,
            cornerRadius: 10,
            callbacks: {
              label: (item) => {
                const percent = totalSpend > 0 ? ((item.raw / totalSpend) * 100).toFixed(1) : 0;
                return ` ${this.formatCurrency(item.raw)} (${percent}%)`;
              }
            }
          }
        }
      }
    });

    return { sortedCats, totalSpend, colors };
  }

  /**
   * 3. Account Spend & Balance Comparison Bar Chart
   */
  renderAccountSpendChart(canvasId, accountsWithMetrics) {
    const ctx = document.getElementById(canvasId);
    if (!ctx) return;

    if (this.instances[canvasId]) {
      this.instances[canvasId].destroy();
    }

    const isLight = document.documentElement.getAttribute('data-theme') === 'light';

    const labels = accountsWithMetrics.map(a => a.name);
    const spendData = accountsWithMetrics.map(a => a.totalExpense);
    const balanceData = accountsWithMetrics.map(a => Math.max(0, a.computedBalance));

    this.instances[canvasId] = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [
          {
            label: 'Total Spend',
            data: spendData,
            backgroundColor: isLight ? 'rgba(225, 29, 72, 0.85)' : 'rgba(244, 63, 94, 0.75)',
            borderColor: isLight ? '#e11d48' : '#f43f5e',
            borderWidth: 1.5,
            borderRadius: 6
          },
          {
            label: 'Available Balance / Limit',
            data: balanceData,
            backgroundColor: isLight ? 'rgba(37, 99, 235, 0.85)' : 'rgba(59, 130, 246, 0.75)',
            borderColor: isLight ? '#2563eb' : '#3b82f6',
            borderWidth: 1.5,
            borderRadius: 6
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'top',
            align: 'end',
            labels: {
              color: isLight ? '#475569' : '#94a3b8',
              font: { family: 'Outfit', size: 12 },
              usePointStyle: true
            }
          },
          tooltip: {
            backgroundColor: isLight ? 'rgba(255, 255, 255, 0.98)' : 'rgba(15, 23, 42, 0.95)',
            titleColor: isLight ? '#0f172a' : '#f8fafc',
            bodyColor: isLight ? '#334155' : '#cbd5e1',
            borderColor: isLight ? 'rgba(0, 0, 0, 0.12)' : 'rgba(255, 255, 255, 0.1)',
            borderWidth: 1,
            padding: 12,
            callbacks: {
              label: (item) => `${item.dataset.label}: ${this.formatCurrency(item.raw)}`
            }
          }
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: { color: isLight ? '#475569' : '#94a3b8', font: { size: 11 } }
          },
          y: {
            grid: { color: isLight ? 'rgba(0, 0, 0, 0.05)' : 'rgba(255, 255, 255, 0.04)' },
            ticks: {
              color: isLight ? '#64748b' : '#64748b',
              callback: (val) => this.formatCurrency(val)
            }
          }
        }
      }
    });
  }

  /**
   * 4. Export Chart Canvas as crisp PNG image
   */
  exportChartAsPNG(canvasId, filename = 'financial-chart.png') {
    const canvas = document.getElementById(canvasId);
    if (!canvas) return;

    const isLight = document.documentElement.getAttribute('data-theme') === 'light';

    const offscreen = document.createElement('canvas');
    offscreen.width = canvas.width * 2;
    offscreen.height = canvas.height * 2;
    const ctx = offscreen.getContext('2d');

    // Draw background
    ctx.fillStyle = isLight ? '#ffffff' : '#0f172a';
    ctx.fillRect(0, 0, offscreen.width, offscreen.height);

    // Draw chart
    ctx.drawImage(canvas, 0, 0, offscreen.width, offscreen.height);

    const link = document.createElement('a');
    link.download = filename;
    link.href = offscreen.toDataURL('image/png', 1.0);
    link.click();
  }
}

// Global instance
window.chartsEngine = new ChartsEngine();
