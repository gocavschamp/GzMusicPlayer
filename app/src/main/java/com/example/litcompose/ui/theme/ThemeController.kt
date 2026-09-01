package com.example.litcompose.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** 可选的主题主色（含浅色/深色两套 primary） */
enum class AccentColor(
    val key: String,
    val label: String,
    val light: Color,
    val dark: Color,
) {
    Mint("mint", "薄荷", Color(0xFF4AAE9B), Color(0xFF82D6C3)),
    Purple("purple", "紫", Color(0xFF6750A4), Color(0xFFD0BCFF)),
    Blue("blue", "蓝", Color(0xFF1E88E5), Color(0xFF90CAF9)),
    Orange("orange", "橙", Color(0xFFEF6C00), Color(0xFFFFB74D)),
    Pink("pink", "粉", Color(0xFFD81B60), Color(0xFFF48FB1)),
}

/**
 * 全局主题状态：深色模式 + 主题主色，状态持久化到 SharedPreferences。
 * 通过 Composable 读取 state 自动触发重组换肤。
 */
object ThemeController {
    private const val PREFS = "litcompose_theme"
    private const val KEY_DARK = "dark"
    private const val KEY_ACCENT = "accent"

    var darkTheme by mutableStateOf(false)
        private set
    var accent by mutableStateOf(AccentColor.Mint)
        private set

    private var prefs: SharedPreferences? = null

    /** 在 Application/Activity 启动时调用一次 */
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        darkTheme = prefs?.getBoolean(KEY_DARK, false) ?: false
        accent =
            enumValues<AccentColor>().firstOrNull { it.key == prefs?.getString(KEY_ACCENT, AccentColor.Mint.key) }
                ?: AccentColor.Mint
    }

    fun setDark(dark: Boolean) {
        darkTheme = dark
        prefs?.edit()?.putBoolean(KEY_DARK, dark)?.apply()
    }

    fun setAccentColor(color: AccentColor) {
        accent = color
        prefs?.edit()?.putString(KEY_ACCENT, color.key)?.apply()
    }
}
