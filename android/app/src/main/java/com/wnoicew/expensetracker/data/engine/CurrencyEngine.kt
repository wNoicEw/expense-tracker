package com.wnoicew.expensetracker.data.engine

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val name: String,
    val flag: String,
    val locale: Locale,
    val defaultDecimals: Int = 2
)

object CurrencyEngine {

    const val DEFAULT_CURRENCY = "INR"

    val SUPPORTED_CURRENCIES = listOf(
        CurrencyInfo("INR", "₹", "Indian Rupee", "🇮🇳", Locale("en", "IN"), 2),
        CurrencyInfo("USD", "$", "US Dollar", "🇺🇸", Locale.US, 2),
        CurrencyInfo("EUR", "€", "Euro", "🇪🇺", Locale.GERMANY, 2),
        CurrencyInfo("GBP", "£", "British Pound", "🇬🇧", Locale.UK, 2),
        CurrencyInfo("CHF", "₣", "Swiss Franc", "🇨🇭", Locale("de", "CH"), 2),
        CurrencyInfo("JPY", "¥", "Japanese Yen", "🇯🇵", Locale.JAPAN, 0)
    )

    private val CURRENCY_MAP = SUPPORTED_CURRENCIES.associateBy { it.code.uppercase() }

    // Baseline offline exchange rates relative to 1 USD
    val BASELINE_USD_RATES: Map<String, Double> = mapOf(
        "USD" to 1.0,
        "INR" to 83.50,
        "EUR" to 0.92,
        "GBP" to 0.78,
        "CHF" to 0.89,
        "JPY" to 155.00
    )

    private val currentRates = ConcurrentHashMap<String, Double>(BASELINE_USD_RATES)
    private val apiRates = ConcurrentHashMap<String, Double>(BASELINE_USD_RATES)
    private val manualOverrides = ConcurrentHashMap.newKeySet<String>()

    private const val PREFS_NAME = "money_tracker_currency_prefs"
    private const val KEY_LAST_FETCH_DATE = "last_currency_fetch_date"
    private const val KEY_LAST_FETCH_TIMESTAMP = "last_currency_fetch_timestamp"
    private const val KEY_RATES_JSON = "rates_usd_json"
    private const val KEY_API_RATES_JSON = "api_rates_usd_json"
    private const val KEY_MANUAL_OVERRIDES = "manual_overrides_set"

    private const val PRIMARY_API_URL = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json"
    private const val FALLBACK_API_URL = "https://latest.currency-api.pages.dev/v1/currencies/usd.json"

    fun getCurrencyInfo(code: String): CurrencyInfo {
        return CURRENCY_MAP[code.uppercase()] ?: CURRENCY_MAP[DEFAULT_CURRENCY]!!
    }

    fun getSymbol(code: String): String {
        return getCurrencyInfo(code).symbol
    }

    fun getSupportedCurrencies(): List<CurrencyInfo> = SUPPORTED_CURRENCIES

    fun getRatesSnapshot(): Map<String, Double> = HashMap(currentRates)

    fun getApiRate(code: String): Double {
        val upper = code.uppercase()
        return apiRates[upper] ?: BASELINE_USD_RATES[upper] ?: 1.0
    }

    fun isManualOverride(code: String): Boolean {
        return manualOverrides.contains(code.uppercase())
    }

    fun hasAnyManualOverride(): Boolean = manualOverrides.isNotEmpty()

    fun getLastFetchTimestamp(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_FETCH_TIMESTAMP, null) ?: "Offline Baseline Rates"
    }

    /**
     * Initializes rates and overrides from saved SharedPreferences on app startup.
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load manual overrides
        val savedOverrides = prefs.getStringSet(KEY_MANUAL_OVERRIDES, null)
        manualOverrides.clear()
        if (savedOverrides != null) {
            manualOverrides.addAll(savedOverrides.map { it.uppercase() })
        }

        // Load API rates cache
        val savedApiJson = prefs.getString(KEY_API_RATES_JSON, null)
        if (!savedApiJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(savedApiJson)
                for (cur in SUPPORTED_CURRENCIES) {
                    val codeLower = cur.code.lowercase()
                    if (obj.has(codeLower)) {
                        val rate = obj.optDouble(codeLower, -1.0)
                        if (rate > 0.0) {
                            apiRates[cur.code] = rate
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Load active effective rates
        val savedJson = prefs.getString(KEY_RATES_JSON, null)
        if (!savedJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(savedJson)
                for (cur in SUPPORTED_CURRENCIES) {
                    val codeLower = cur.code.lowercase()
                    if (obj.has(codeLower)) {
                        val rate = obj.optDouble(codeLower, -1.0)
                        if (rate > 0.0) {
                            currentRates[cur.code] = rate
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun shouldFetchRatesOnDate(lastFetchDate: String?, currentDate: String): Boolean {
        return lastFetchDate == null || lastFetchDate != currentDate
    }

    /**
     * Checks if exchange rates have been fetched today. If not, pulls once and updates cache.
     * Must be called from IO / background thread.
     */
    fun checkAndFetchDailyRates(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastFetchDate = prefs.getString(KEY_LAST_FETCH_DATE, null)

        // Strict rule: Only pull once a day on first open
        if (!shouldFetchRatesOnDate(lastFetchDate, todayStr)) {
            return false
        }

        return forceFetchRates(context)
    }

    /**
     * Force pulls the latest exchange rates from CDN API on demand, bypassing the daily cache check.
     * Updates pure API cache, preserves manual overrides, and refreshes timestamps.
     * Must be called from background / IO thread.
     */
    fun forceFetchRates(context: Context): Boolean {
        val fetchedData = fetchFromUrl(PRIMARY_API_URL) ?: fetchFromUrl(FALLBACK_API_URL)
        if (fetchedData != null) {
            val parsedRates = parseRatesJson(fetchedData)
            if (parsedRates.isNotEmpty()) {
                apiRates.putAll(parsedRates)

                // Update active rates for currencies that do NOT have manual overrides
                for ((code, rate) in parsedRates) {
                    if (!manualOverrides.contains(code.uppercase())) {
                        currentRates[code] = rate
                    }
                }

                val now = Date()
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
                val timestampStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(now)

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val saveActiveObj = JSONObject()
                for ((code, rate) in currentRates) {
                    saveActiveObj.put(code.lowercase(), rate)
                }

                val saveApiObj = JSONObject()
                for ((code, rate) in apiRates) {
                    saveApiObj.put(code.lowercase(), rate)
                }

                prefs.edit()
                    .putString(KEY_RATES_JSON, saveActiveObj.toString())
                    .putString(KEY_API_RATES_JSON, saveApiObj.toString())
                    .putString(KEY_LAST_FETCH_DATE, todayStr)
                    .putString(KEY_LAST_FETCH_TIMESTAMP, timestampStr)
                    .apply()

                return true
            }
        }
        return false
    }

    /**
     * Get dynamic effective rate of target currency relative to base currency (1 base = X target).
     */
    fun getRateAgainstBase(targetCode: String, baseCode: String): Double {
        val tUpper = targetCode.uppercase()
        val bUpper = baseCode.uppercase()
        if (tUpper == bUpper) return 1.0
        val rateTarget = currentRates[tUpper] ?: BASELINE_USD_RATES[tUpper] ?: 1.0
        val rateBase = currentRates[bUpper] ?: BASELINE_USD_RATES[bUpper] ?: 1.0
        if (rateBase <= 0.0) return 1.0
        return rateTarget / rateBase
    }

    /**
     * Get pure API baseline rate of target currency relative to base currency (1 base = X target).
     */
    fun getApiRateAgainstBase(targetCode: String, baseCode: String): Double {
        val tUpper = targetCode.uppercase()
        val bUpper = baseCode.uppercase()
        if (tUpper == bUpper) return 1.0
        val apiTarget = apiRates[tUpper] ?: BASELINE_USD_RATES[tUpper] ?: 1.0
        val apiBase = apiRates[bUpper] ?: BASELINE_USD_RATES[bUpper] ?: 1.0
        if (apiBase <= 0.0) return 1.0
        return apiTarget / apiBase
    }

    /**
     * Manually overrides the conversion rate for targetCode relative to baseCode (1 base = rateFromBase * target).
     */
    fun updateManualRateAgainstBase(context: Context, targetCode: String, baseCode: String, rateFromBase: Double) {
        if (rateFromBase <= 0.0) return
        val tUpper = targetCode.uppercase()
        val bUpper = baseCode.uppercase()
        val rateBase = currentRates[bUpper] ?: BASELINE_USD_RATES[bUpper] ?: 1.0
        currentRates[tUpper] = rateFromBase * rateBase
        manualOverrides.add(tUpper)

        persistCurrentState(context)
    }

    /**
     * Manually overrides the conversion rate for a given currency code (relative to 1 USD).
     */
    fun updateManualRate(context: Context, code: String, rateToUsd: Double) {
        updateManualRateAgainstBase(context, code, "USD", rateToUsd)
    }

    /**
     * Resets a specific currency's conversion rate to the latest API-fetched rate relative to baseCode.
     */
    fun resetRateToApiAgainstBase(context: Context, targetCode: String, baseCode: String) {
        val tUpper = targetCode.uppercase()
        val bUpper = baseCode.uppercase()
        manualOverrides.remove(tUpper)
        val apiTarget = apiRates[tUpper] ?: BASELINE_USD_RATES[tUpper] ?: 1.0
        val apiBase = apiRates[bUpper] ?: BASELINE_USD_RATES[bUpper] ?: 1.0
        val rateBase = currentRates[bUpper] ?: BASELINE_USD_RATES[bUpper] ?: 1.0
        currentRates[tUpper] = (apiTarget / apiBase) * rateBase

        persistCurrentState(context)
    }

    /**
     * Resets a specific currency's conversion rate to the latest API-fetched rate (or baseline).
     */
    fun resetRateToApi(context: Context, code: String) {
        resetRateToApiAgainstBase(context, code, "USD")
    }

    /**
     * Resets all currencies to their latest API-fetched rates, clearing all manual overrides.
     */
    fun resetAllRatesToApi(context: Context) {
        manualOverrides.clear()
        for (cur in SUPPORTED_CURRENCIES) {
            val apiRate = apiRates[cur.code] ?: BASELINE_USD_RATES[cur.code] ?: 1.0
            currentRates[cur.code] = apiRate
        }

        persistCurrentState(context)
    }

    private fun persistCurrentState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saveActiveObj = JSONObject()
        for ((code, rate) in currentRates) {
            saveActiveObj.put(code.lowercase(), rate)
        }

        prefs.edit()
            .putString(KEY_RATES_JSON, saveActiveObj.toString())
            .putStringSet(KEY_MANUAL_OVERRIDES, HashSet(manualOverrides))
            .apply()
    }

    /**
     * Parses the USD-based exchange rate JSON payload from Fawaz Ahmed's API.
     */
    fun parseRatesJson(jsonString: String): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        try {
            val root = JSONObject(jsonString)
            val usdNode = root.optJSONObject("usd")
            if (usdNode != null) {
                for (cur in SUPPORTED_CURRENCIES) {
                    val codeLower = cur.code.lowercase()
                    if (usdNode.has(codeLower)) {
                        val rate = usdNode.optDouble(codeLower, -1.0)
                        if (rate > 0.0) {
                            result[cur.code] = rate
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun fetchFromUrl(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 MoneyTrackerApp/1.5.1")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                sb.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Converts an amount from one currency to another using USD cross-rates:
     * amountInTarget = (amount / rateFromUsd) * rateToUsd
     */
    fun convert(amount: Double, fromCurrency: String, toCurrency: String): Double {
        val fromUpper = fromCurrency.uppercase()
        val toUpper = toCurrency.uppercase()
        if (fromUpper == toUpper) return amount

        val rateFrom = currentRates[fromUpper] ?: BASELINE_USD_RATES[fromUpper] ?: 1.0
        val rateTo = currentRates[toUpper] ?: BASELINE_USD_RATES[toUpper] ?: 1.0

        if (rateFrom <= 0.0) return amount
        val amountInUsd = amount / rateFrom
        return amountInUsd * rateTo
    }

    private val threadLocalFormatters = ThreadLocal.withInitial {
        HashMap<String, NumberFormat>()
    }

    /**
     * Formats an amount in the specified currency with symbol and decimal formatting.
     * Uses ThreadLocal cached NumberFormat to avoid ICU allocations on high-refresh-rate scroll passes.
     */
    fun format(amount: Double, currency: String, maxFractionDigits: Int? = null): String {
        val info = getCurrencyInfo(currency)
        val frac = maxFractionDigits ?: info.defaultDecimals
        val key = "${info.code}_$frac"
        val map = threadLocalFormatters.get() ?: HashMap()
        val nf = map.getOrPut(key) {
            NumberFormat.getCurrencyInstance(info.locale).apply {
                maximumFractionDigits = frac
                minimumFractionDigits = if (frac == 0) 0 else frac
            }
        }
        return nf.format(amount)
    }

    /**
     * Returns a NumberFormat configured for the given currency code.
     */
    fun getFormat(currency: String, maxFractionDigits: Int = 0): NumberFormat {
        val info = getCurrencyInfo(currency)
        val key = "custom_${info.code}_$maxFractionDigits"
        val map = threadLocalFormatters.get() ?: HashMap()
        val cached = map.getOrPut(key) {
            NumberFormat.getCurrencyInstance(info.locale).apply {
                maximumFractionDigits = maxFractionDigits
                minimumFractionDigits = 0
            }
        }
        return cached.clone() as NumberFormat
    }

    /**
     * Returns a compact representation (e.g. ₹12.5k, $1.2M, ¥50k)
     */
    fun formatCompact(amount: Double, currency: String): String {
        val symbol = getSymbol(currency)
        val abs = Math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            abs >= 10_000_000 -> "$sign$symbol" + String.format(Locale.ROOT, "%.1fCr", abs / 10_000_000.0)
            abs >= 100_000 && currency.uppercase() == "INR" -> "$sign$symbol" + String.format(Locale.ROOT, "%.1fL", abs / 100_000.0)
            abs >= 1_000_000 -> "$sign$symbol" + String.format(Locale.ROOT, "%.1fM", abs / 1_000_000.0)
            abs >= 1_000 -> "$sign$symbol" + String.format(Locale.ROOT, "%.1fk", abs / 1_000.0)
            else -> "$sign$symbol" + String.format(Locale.ROOT, "%.0f", abs)
        }
    }

    // Direct helper for testing without Android context
    fun setRateForTesting(currency: String, rateToUsd: Double) {
        currentRates[currency.uppercase()] = rateToUsd
    }

    fun setRateAgainstBaseForTesting(currency: String, baseCurrency: String, rateFromBase: Double) {
        val tUpper = currency.uppercase()
        val bUpper = baseCurrency.uppercase()
        val rateBase = currentRates[bUpper] ?: BASELINE_USD_RATES[bUpper] ?: 1.0
        currentRates[tUpper] = rateFromBase * rateBase
        manualOverrides.add(tUpper)
    }

    fun setApiRateForTesting(currency: String, rateToUsd: Double) {
        apiRates[currency.uppercase()] = rateToUsd
    }

    fun setManualOverrideForTesting(currency: String, isOverridden: Boolean) {
        val upper = currency.uppercase()
        if (isOverridden) manualOverrides.add(upper) else manualOverrides.remove(upper)
    }

    fun resetRatesForTesting() {
        currentRates.clear()
        currentRates.putAll(BASELINE_USD_RATES)
        apiRates.clear()
        apiRates.putAll(BASELINE_USD_RATES)
        manualOverrides.clear()
    }
}
