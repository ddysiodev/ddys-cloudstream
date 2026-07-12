package io.ddys.cloudstream

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class DdysSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun apiBase(): String = normalizeBaseUrl(prefs.getString(KEY_API_BASE, DEFAULT_API_BASE) ?: DEFAULT_API_BASE)
    fun siteBase(): String = normalizeBaseUrl(prefs.getString(KEY_SITE_BASE, DEFAULT_SITE_BASE) ?: DEFAULT_SITE_BASE)
    fun apiKey(): String = prefs.getString(KEY_API_KEY, "")?.trim().orEmpty()
    fun pageSize(): Int = prefs.getInt(KEY_PAGE_SIZE, DEFAULT_PAGE_SIZE).coerceIn(1, 80)
    fun homeLimit(): Int = prefs.getInt(KEY_HOME_LIMIT, DEFAULT_HOME_LIMIT).coerceIn(1, 80)
    fun directOnly(): Boolean = prefs.getBoolean(KEY_DIRECT_ONLY, false)
    fun includeExternal(): Boolean = prefs.getBoolean(KEY_INCLUDE_EXTERNAL, true)

    fun save(
        apiBase: String,
        siteBase: String,
        apiKey: String,
        pageSize: Int,
        homeLimit: Int,
        directOnly: Boolean,
        includeExternal: Boolean,
    ) {
        prefs.edit()
            .putString(KEY_API_BASE, normalizeBaseUrl(apiBase.ifBlank { DEFAULT_API_BASE }))
            .putString(KEY_SITE_BASE, normalizeBaseUrl(siteBase.ifBlank { DEFAULT_SITE_BASE }))
            .putString(KEY_API_KEY, apiKey.trim())
            .putInt(KEY_PAGE_SIZE, pageSize.coerceIn(1, 80))
            .putInt(KEY_HOME_LIMIT, homeLimit.coerceIn(1, 80))
            .putBoolean(KEY_DIRECT_ONLY, directOnly)
            .putBoolean(KEY_INCLUDE_EXTERNAL, includeExternal)
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_API_BASE = "https://ddys.io/api/v1"
        const val DEFAULT_SITE_BASE = "https://ddys.io"
        const val DEFAULT_PAGE_SIZE = 24
        const val DEFAULT_HOME_LIMIT = 24

        private const val PREFS_NAME = "ddys_cloudstream_settings"
        private const val KEY_API_BASE = "api_base"
        private const val KEY_SITE_BASE = "site_base"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_PAGE_SIZE = "page_size"
        private const val KEY_HOME_LIMIT = "home_limit"
        private const val KEY_DIRECT_ONLY = "direct_only"
        private const val KEY_INCLUDE_EXTERNAL = "include_external"

        fun normalizeBaseUrl(value: String): String {
            val text = value.trim().trimEnd('/')
            return text.ifBlank { DEFAULT_SITE_BASE }
        }
    }
}

object DdysSettingsDialog {
    fun show(activity: Activity, settings: DdysSettings) {
        val padding = activity.dp(20)
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }

        val apiBase = textInput(activity, "API Base", settings.apiBase())
        val siteBase = textInput(activity, "Site Base", settings.siteBase())
        val apiKey = textInput(activity, "API Key", settings.apiKey()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val pageSize = numberInput(activity, "每页数量", settings.pageSize())
        val homeLimit = numberInput(activity, "首页数量", settings.homeLimit())
        val directOnly = CheckBox(activity).apply {
            text = "只返回直链资源"
            isChecked = settings.directOnly()
        }
        val includeExternal = CheckBox(activity).apply {
            text = "包含外部/网盘/磁力资源"
            isChecked = settings.includeExternal()
        }

        listOf(apiBase, siteBase, apiKey, pageSize, homeLimit).forEach { layout.addView(it) }
        layout.addView(directOnly)
        layout.addView(includeExternal)

        val scroll = ScrollView(activity).apply { addView(layout) }

        AlertDialog.Builder(activity)
            .setTitle("DDYS 设置")
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                settings.save(
                    apiBase = apiBase.editText.text.toString(),
                    siteBase = siteBase.editText.text.toString(),
                    apiKey = apiKey.editText.text.toString(),
                    pageSize = pageSize.editText.text.toString().toIntOrNull() ?: DdysSettings.DEFAULT_PAGE_SIZE,
                    homeLimit = homeLimit.editText.text.toString().toIntOrNull() ?: DdysSettings.DEFAULT_HOME_LIMIT,
                    directOnly = directOnly.isChecked,
                    includeExternal = includeExternal.isChecked,
                )
                Toast.makeText(activity, "DDYS 设置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("恢复默认") { _, _ ->
                settings.reset()
                Toast.makeText(activity, "DDYS 设置已恢复默认", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun textInput(context: Context, label: String, value: String): LabeledEditText {
        return LabeledEditText(context, label, value, InputType.TYPE_CLASS_TEXT)
    }

    private fun numberInput(context: Context, label: String, value: Int): LabeledEditText {
        return LabeledEditText(context, label, value.toString(), InputType.TYPE_CLASS_NUMBER)
    }

    private class LabeledEditText(
        context: Context,
        label: String,
        value: String,
        inputTypeValue: Int,
    ) : LinearLayout(context) {
        val editText: EditText

        init {
            orientation = VERTICAL
            val bottom = context.dp(12)
            setPadding(0, 0, 0, bottom)

            addView(TextView(context).apply {
                text = label
                textSize = 13f
            })
            editText = EditText(context).apply {
                setText(value)
                setSingleLine(true)
                inputType = inputTypeValue
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }
            addView(editText)
        }
    }

    private fun Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
