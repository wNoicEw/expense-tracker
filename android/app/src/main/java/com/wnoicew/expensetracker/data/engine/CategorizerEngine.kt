package com.wnoicew.expensetracker.data.engine

import com.wnoicew.expensetracker.data.model.RuleEntity
import com.wnoicew.expensetracker.data.model.TransactionType

data class CategorizationResult(
    val category: String,
    val cleanTitle: String,
    val type: TransactionType,
    val confidence: String, // "high", "learned", "low"
    val needsReview: Boolean,
    val matchedKeyword: String = "",
    val identifier: String = ""
)

object CategorizerEngine {

    data class MerchantRule(
        val category: String,
        val keywords: List<String>,
        val defaultType: TransactionType = TransactionType.EXPENSE
    )

    private val merchantRules = listOf(
        // Food & Dining
        MerchantRule(
            category = "Food & Dining",
            keywords = listOf(
                "swiggy", "zomato", "starbucks", "mcdonald", "kfc", "burger king", "domino", "pizza hut",
                "biryani", "chaayos", "chai point", "cafe coffee day", "subway", "haldiram",
                "barbeque nation", "eatfit", "bakehouse", "restaurant", "dining", "dhaba", "bakery",
                "juice", "shawarma", "tiffin", "canteen", "sweets", "food court"
            )
        ),
        // Groceries & Daily Needs
        MerchantRule(
            category = "Groceries & Mart",
            keywords = listOf(
                "blinkit", "zepto", "instamart", "bigbasket", "dmart", "d-mart", "spencer",
                "reliance retail", "smart point", "natures basket", "more retail", "grofers", "bbdaily",
                "country delight", "dairy", "supermarket", "grocery", "kirana", "vegetable", "fruits",
                "provision", "freshtohome", "licious"
            )
        ),
        // Shopping & E-Commerce
        MerchantRule(
            category = "Shopping & E-Comm",
            keywords = listOf(
                "amazon", "flipkart", "myntra", "ajio", "nykaa", "zara", "h&m", "tata cliq",
                "meesho", "croma", "reliance digital", "decathlon", "nike", "adidas", "uniqlo",
                "apple store", "ikea", "lifestyle", "westside", "shoppers stop", "lenskart", "boat",
                "snitch", "urbanic", "clovia", "purplle", "clothing", "electronics", "footwear"
            )
        ),
        // Travel & Transport
        MerchantRule(
            category = "Travel & Commute",
            keywords = listOf(
                "uber", "ola", "rapido", "irctc", "makemytrip", "cleartrip", "yatra", "goibibo",
                "indigo", "air india", "vistara", "akasa", "spicejet", "petrol", "fuel", "hpcl",
                "bpcl", "iocl", "shell", "indian oil", "hindustan petroleum", "bharat petroleum",
                "metro", "fastag", "toll", "parking", "auto rickshaw", "railway", "train", "bus",
                "redbus", "chalo"
            )
        ),
        // Bills & Utilities
        MerchantRule(
            category = "Bills & Utilities",
            keywords = listOf(
                "bescom", "tata power", "adani elec", "mseb", "uppcl", "cesc", "tneb", "bwssb",
                "water board", "mahanagar gas", "igl", "adani gas", "airtel", "jio", "vodafone",
                "vi prepaid", "vi postpaid", "act fibernet", "hathway", "tata play", "tatasky",
                "dth", "electricity", "broadband", "cylinder", "indane", "hp gas", "bharat gas",
                "bbps", "billpay", "utility"
            )
        ),
        // Subscriptions & OTT
        MerchantRule(
            category = "Subscriptions & OTT",
            keywords = listOf(
                "netflix", "spotify", "amazon prime", "hotstar", "disney", "apple music",
                "youtube premium", "sonyliv", "zee5", "chatgpt", "openai", "midjourney", "claude",
                "adobe", "google one", "google storage", "icloud", "cursor", "github", "notion",
                "canva", "playstation", "xbox", "steam", "audible", "kindle"
            )
        ),
        // Health & Wellness
        MerchantRule(
            category = "Health & Pharmacy",
            keywords = listOf(
                "apollo pharmacy", "netmeds", "tata 1mg", "1mg", "pharmeasy", "medplus",
                "cult.fit", "cultfit", "gold gym", "anytime fitness", "hospital", "clinic",
                "dental", "diagnostic", "pathology", "dr lal pathlabs", "metropolis", "fortis",
                "max healthcare", "manipal", "medical", "pharmacy", "chemist", "doctor", "physio", "opticals"
            )
        ),
        // Investments & Wealth
        MerchantRule(
            category = "Investments & SIP",
            keywords = listOf(
                "zerodha", "groww", "upstox", "coin", "kuvera", "angel one", "indmoney",
                "smallcase", "et money", "mutual fund", "sip", "nsdl", "cdsl", "nps", "ppf",
                "sgb", "motilal oswal", "icici direct", "hdfc securities", "uti amc", "sbi mutual",
                "nippon", "mirae asset", "parag parikh", "stocks", "equity", "gold"
            )
        ),
        // Rent & Housing
        MerchantRule(
            category = "Rent & Housing",
            keywords = listOf(
                "rent", "maintenance", "housing society", "nobroker", "magicbricks", "housing.com",
                "mygate", "urban company", "housekeeping", "apartment", "landlord", "flat maintenance"
            )
        ),
        // Salary & Professional Income
        MerchantRule(
            category = "Salary & Professional",
            keywords = listOf(
                "salary", "payroll", "ach credit", "ach cr/", "tech corp", "infotech", "tcs",
                "infosys", "wipro", "accenture", "google india", "microsoft india", "amazon dev",
                "corporate stipend", "wages", "compensation"
            ),
            defaultType = TransactionType.INCOME
        ),
        // Transfers & Credit Card Bill Payments
        MerchantRule(
            category = "Transfers & CC Bill",
            keywords = listOf(
                "credit card payment", "cc payment", "card bill payment", "card payment",
                "cc bill", "card bill", "card settlement", "card outstanding", "minimum due",
                "total amount due", "cred app", "cred payment", "cred card", "billdesk cc", "billdesk card", "billdesk credit",
                "razorpay card", "payu card", "autopay cc", "autopay card", "nach card",
                "ecs card", "si credit card", "hdfc card payment", "sbi card payment",
                "icici card payment", "axis card payment", "kotak card payment", "amex payment",
                "self transfer", "fund transfer", "own account transfer", "internal transfer",
                "atm cash", "atm withdrawal", "cash deposit", "cash withdrawal"
            ),
            defaultType = TransactionType.TRANSFER
        ),
        // Freelance & Business
        MerchantRule(
            category = "Freelance & Side Hustle",
            keywords = listOf(
                "freelance", "consulting", "upwork", "fiverr", "stripe", "paypal",
                "client payment", "saas payout", "royalty", "ad revenue", "google adsense"
            ),
            defaultType = TransactionType.INCOME
        )
    )

    private val brandPrettyMap = mapOf(
        "swiggy" to "Swiggy Food",
        "instamart" to "Swiggy Instamart",
        "zomato" to "Zomato Dining",
        "blinkit" to "Blinkit Groceries",
        "zepto" to "Zepto Quick Mart",
        "bigbasket" to "BigBasket",
        "amazon" to "Amazon India",
        "flipkart" to "Flipkart",
        "myntra" to "Myntra Fashion",
        "uber" to "Uber Rides",
        "ola" to "Ola Cabs",
        "rapido" to "Rapido Bike Taxi",
        "irctc" to "IRCTC Indian Railways",
        "netflix" to "Netflix OTT",
        "spotify" to "Spotify Music",
        "airtel" to "Airtel Broadband / Bill",
        "jio" to "Reliance Jio Recharge",
        "bescom" to "BESCOM Electricity",
        "zerodha" to "Zerodha Coin / Kite",
        "groww" to "Groww Investment",
        "starbucks" to "Starbucks Coffee",
        "apollo" to "Apollo Pharmacy",
        "cred" to "CRED Card Bill Payment"
    )

    fun categorize(
        rawText: String,
        amount: Double = 0.0,
        customRules: List<RuleEntity> = emptyList()
    ): CategorizationResult {
        val text = rawText.lowercase()
        val identifier = extractIdentifier(rawText)

        // 1. Check custom learned rules FIRST
        for (rule in customRules) {
            val pattern = rule.pattern.lowercase().trim()
            if (pattern.isNotEmpty() && (text.contains(pattern) || (identifier.isNotEmpty() && identifier.contains(pattern)))) {
                return CategorizationResult(
                    category = rule.category,
                    cleanTitle = cleanNarration(rawText),
                    type = rule.type,
                    confidence = "learned",
                    needsReview = false,
                    matchedKeyword = pattern,
                    identifier = identifier
                )
            }
        }

        // 2. Check built-in merchant rules
        for (rule in merchantRules) {
            for (kw in rule.keywords) {
                if (text.contains(kw)) {
                    val pretty = extractMerchantName(rawText, kw)
                    return CategorizationResult(
                        category = rule.category,
                        cleanTitle = pretty,
                        type = rule.defaultType,
                        confidence = "high",
                        needsReview = false,
                        matchedKeyword = kw,
                        identifier = identifier
                    )
                }
            }
        }

        // 3. Fallback salary check
        if (text.contains("salary") || text.contains("payroll") || text.contains("ach credit") || text.contains("ach cr/")) {
            return CategorizationResult(
                category = "Salary & Professional",
                cleanTitle = cleanNarration(rawText),
                type = TransactionType.INCOME,
                confidence = "high",
                needsReview = false,
                identifier = identifier
            )
        }

        // 4. Undetected -> Uncategorized (Needs Review)
        return CategorizationResult(
            category = "Uncategorized",
            cleanTitle = cleanNarration(rawText).ifBlank { "Unclassified Entry" },
            type = if (amount < 0) TransactionType.INCOME else TransactionType.EXPENSE,
            confidence = "low",
            needsReview = true,
            identifier = identifier
        )
    }

    fun extractIdentifier(rawNarration: String): String {
        if (rawNarration.isBlank()) return ""
        val upiMatch = Regex("""([a-zA-Z0-9.\-_]+@[a-zA-Z0-9]+)""").find(rawNarration)
        if (upiMatch != null) {
            return upiMatch.groupValues[1].lowercase()
        }
        val posMatch = Regex("""POS/([^/]+)""", RegexOption.IGNORE_CASE).find(rawNarration)
        if (posMatch != null && posMatch.groupValues[1].trim().length > 3) {
            return posMatch.groupValues[1].trim().lowercase()
        }
        val clean = cleanNarration(rawNarration).lowercase()
        return clean.split(" ").take(3).joinToString(" ")
    }

    fun extractMerchantName(rawNarration: String, matchedKeyword: String): String {
        val lower = matchedKeyword.lowercase()
        for ((key, pretty) in brandPrettyMap) {
            if (lower.contains(key)) return pretty
        }
        return cleanNarration(rawNarration).ifBlank { matchedKeyword.replaceFirstChar { it.uppercase() } }
    }

    fun cleanNarration(narration: String): String {
        if (narration.isBlank()) return "Transaction"
        var clean = narration
            .replace(Regex("""^(UPI/|POS/|ACH CR/|ACH DR/|IMPS/|NEFT/|RTGS/|BBPS/|TRANSFER-UPI/DR/|TRANSFER-UPI/CR/|TO TRANSFER-UPI/DR/)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b\d{6,}\b"""), "")
            .replace(Regex("""(CARD \d{4}|XX\d{4}|X{4,}\d{4})""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""@[a-zA-Z0-9_-]+"""), "")
            .replace(Regex("""/(MUMBAI|BANGALORE|BLR|DELHI|HYDERABAD|CHENNAI|PUNE|KOLKATA|NOIDA|GURGAON)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[/_\-]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (clean.length < 3) return narration.take(30)
        return clean
    }
}
