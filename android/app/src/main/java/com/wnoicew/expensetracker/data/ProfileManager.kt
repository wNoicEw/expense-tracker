package com.wnoicew.expensetracker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class UserProfile(
    val id: String,
    val name: String,
    val initial: String,
    val gradientColors: List<Long>,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toBrush(): Brush {
        val colors = if (gradientColors.size >= 2) {
            gradientColors.map { Color(it) }
        } else {
            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
        }
        return Brush.linearGradient(colors)
    }
}

val PRESET_GRADIENTS = listOf(
    listOf(0xFF6366F1, 0xFF8B5CF6), // Indigo -> Violet
    listOf(0xFF10B981, 0xFF059669), // Emerald -> Forest
    listOf(0xFF3B82F6, 0xFF06B6D4), // Blue -> Cyan
    listOf(0xFFF59E0B, 0xFFD97706), // Amber -> Gold
    listOf(0xFFEC4899, 0xFFF43F5E), // Pink -> Rose
    listOf(0xFF8B5CF6, 0xFFD946EF), // Purple -> Fuchsia
    listOf(0xFF14B8A6, 0xFF0EA5E9), // Teal -> Sky
    listOf(0xFFF97316, 0xFFEF4444)  // Orange -> Coral
)

class ProfileManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("money_tracker_profiles_prefs", Context.MODE_PRIVATE)

    val profiles = mutableStateListOf<UserProfile>()
    val activeProfile = mutableStateOf<UserProfile?>(null)

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        profiles.clear()
        val raw = prefs.getString("profiles_json", null)
        if (!raw.isNullOrEmpty()) {
            try {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val initial = obj.optString("initial", name.firstOrNull()?.uppercase() ?: "?")
                    val gradArray = obj.optJSONArray("gradient")
                    val gradColors = mutableListOf<Long>()
                    if (gradArray != null) {
                        for (g in 0 until gradArray.length()) {
                            gradColors.add(gradArray.getLong(g))
                        }
                    } else {
                        gradColors.addAll(PRESET_GRADIENTS[i % PRESET_GRADIENTS.size])
                    }
                    val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    profiles.add(UserProfile(id, name, initial, gradColors, createdAt))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val activeId = prefs.getString("active_profile_id", null)
        activeProfile.value = profiles.find { it.id == activeId }
    }

    private fun saveProfiles() {
        val array = JSONArray()
        for (p in profiles) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("initial", p.initial)
            val gradArray = JSONArray()
            p.gradientColors.forEach { gradArray.put(it) }
            obj.put("gradient", gradArray)
            obj.put("createdAt", p.createdAt)
            array.put(obj)
        }
        prefs.edit().putString("profiles_json", array.toString()).apply()
    }

    fun createProfile(name: String): UserProfile {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Profile name cannot be empty" }
        require(profiles.none { it.name.equals(trimmed, ignoreCase = true) }) { "A profile named '$trimmed' already exists" }

        val gradientIndex = profiles.size % PRESET_GRADIENTS.size
        val newProfile = UserProfile(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            initial = trimmed.first().uppercase(),
            gradientColors = PRESET_GRADIENTS[gradientIndex]
        )
        profiles.add(newProfile)
        saveProfiles()
        setActiveProfile(newProfile.id)
        return newProfile
    }

    fun renameProfile(id: String, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "Name cannot be empty" }
        require(profiles.none { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) { "Name '$trimmed' already in use" }

        val index = profiles.indexOfFirst { it.id == id }
        if (index != -1) {
            val old = profiles[index]
            val updated = old.copy(name = trimmed, initial = trimmed.first().uppercase())
            profiles[index] = updated
            if (activeProfile.value?.id == id) {
                activeProfile.value = updated
            }
            saveProfiles()
        }
    }

    fun setActiveProfile(id: String) {
        val profile = profiles.find { it.id == id }
        if (profile != null) {
            activeProfile.value = profile
            prefs.edit().putString("active_profile_id", id).apply()
        }
    }

    fun deleteProfile(id: String) {
        // Delete database file for this profile
        context.deleteDatabase("ExpenseTrackerDB_$id")
        profiles.removeAll { it.id == id }
        saveProfiles()
        if (activeProfile.value?.id == id) {
            val next = profiles.firstOrNull()
            if (next != null) {
                setActiveProfile(next.id)
            } else {
                activeProfile.value = null
                prefs.edit().remove("active_profile_id").apply()
            }
        }
    }
}
