package com.amurcanov.tgwsproxy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "proxy_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IS_DYNAMIC_COLOR = booleanPreferencesKey("is_dynamic_color")
        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val IS_DC_AUTO = booleanPreferencesKey("is_dc_auto")
        val DC1 = stringPreferencesKey("dc1")
        val DC2 = stringPreferencesKey("dc2")
        val DC3 = stringPreferencesKey("dc3")
        val DC4 = stringPreferencesKey("dc4")
        val PORT = stringPreferencesKey("port")
        val POOL_SIZE = intPreferencesKey("pool_size")
        val CFPROXY_ENABLED = booleanPreferencesKey("cfproxy_enabled")
        val CUSTOM_CF_DOMAIN_ENABLED = booleanPreferencesKey("custom_cf_domain_enabled")
        val CUSTOM_CF_DOMAIN = stringPreferencesKey("custom_cf_domain")
        val SECRET_KEY = stringPreferencesKey("secret_key")
        val LOG_SHOW_DEBUG = booleanPreferencesKey("log_show_debug")
        val LOG_SHOW_INFO = booleanPreferencesKey("log_show_info")
        val LOG_SHOW_ERROR = booleanPreferencesKey("log_show_error")
        val LOG_SHOW_NULL = booleanPreferencesKey("log_show_null")
        val IS_EXPERIMENTAL_MODE = booleanPreferencesKey("is_experimental_mode")
        val UPDATE_LAST_CHECK_AT = longPreferencesKey("update_last_check_at")
        val UPDATE_LATEST_VERSION = stringPreferencesKey("update_latest_version")
        val UPDATE_LAST_ERROR = stringPreferencesKey("update_last_error")
        val UPDATE_CHECK_INTERVAL_HOURS = intPreferencesKey("update_check_interval_hours")
        val UPDATE_POSTPONE_UNTIL = longPreferencesKey("update_postpone_until")
        val UPDATE_POSTPONE_VERSION = stringPreferencesKey("update_postpone_version")
        val UPDATE_DIALOG_LAST_SHOWN_VERSION = stringPreferencesKey("update_dialog_last_shown_version")
        val UPDATE_DIALOG_LAST_SHOWN_AT = longPreferencesKey("update_dialog_last_shown_at")
        val UPDATE_DIALOG_LAST_ACTION_VERSION = stringPreferencesKey("update_dialog_last_action_version")
        val UPDATE_DIALOG_LAST_ACTION = stringPreferencesKey("update_dialog_last_action")
        val UPDATE_DIALOG_LAST_ACTION_AT = longPreferencesKey("update_dialog_last_action_at")
    }

    val isReady: Flow<Boolean> = context.dataStore.data.map { true }
    val isExperimentalMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_EXPERIMENTAL_MODE] ?: false }
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }
    val isDynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_DYNAMIC_COLOR] ?: true }
    val themePalette: Flow<String> = context.dataStore.data.map { it[Keys.THEME_PALETTE] ?: "indigo" }
    val isDcAuto: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_DC_AUTO] ?: true }
    val dc1: Flow<String> = context.dataStore.data.map { it[Keys.DC1] ?: "" }
    val dc2: Flow<String> = context.dataStore.data.map { it[Keys.DC2] ?: "" }
    val dc3: Flow<String> = context.dataStore.data.map { it[Keys.DC3] ?: "" }
    val dc4: Flow<String> = context.dataStore.data.map { it[Keys.DC4] ?: "149.154.167.220" }
    val dc5: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey("dc5")] ?: "" }
    val dc203: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey("dc203")] ?: "" }
    val dc1m: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey("dc1m")] ?: "" }
    val dc2m: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey("dc2m")] ?: "" }
    val dc3m: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey("dc3m")] ?: "" }
    val dc4m: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey("dc4m")] ?: "" }
    val dc5m: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey("dc5m")] ?: "" }
    val dc203m: Flow<String> = context.dataStore.data.map { it[stringPreferencesKey("dc203m")] ?: "" }
    val port: Flow<String> = context.dataStore.data.map { it[Keys.PORT] ?: "1443" }
    val poolSize: Flow<Int> = context.dataStore.data.map { it[Keys.POOL_SIZE] ?: 4 }
    val cfproxyEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.CFPROXY_ENABLED] ?: true }
    val customCfDomainEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.CUSTOM_CF_DOMAIN_ENABLED] ?: false }
    val customCfDomain: Flow<String> = context.dataStore.data.map { it[Keys.CUSTOM_CF_DOMAIN] ?: "" }
    val secretKey: Flow<String> = context.dataStore.data.map { it[Keys.SECRET_KEY] ?: "" }

    val logShowDebug: Flow<Boolean> = context.dataStore.data.map { it[Keys.LOG_SHOW_DEBUG] ?: false }
    val logShowInfo: Flow<Boolean> = context.dataStore.data.map { it[Keys.LOG_SHOW_INFO] ?: false }
    val logShowError: Flow<Boolean> = context.dataStore.data.map { it[Keys.LOG_SHOW_ERROR] ?: true }
    val logShowNull: Flow<Boolean> = context.dataStore.data.map { it[Keys.LOG_SHOW_NULL] ?: false }
    val updateLastCheckAt: Flow<Long> = context.dataStore.data.map { it[Keys.UPDATE_LAST_CHECK_AT] ?: 0L }
    val updateLatestVersion: Flow<String> = context.dataStore.data.map { it[Keys.UPDATE_LATEST_VERSION] ?: "" }
    val updateLastError: Flow<String> = context.dataStore.data.map { it[Keys.UPDATE_LAST_ERROR] ?: "" }
    val updateCheckIntervalHours: Flow<Int> = context.dataStore.data.map { it[Keys.UPDATE_CHECK_INTERVAL_HOURS] ?: 24 }
    val updatePostponeUntil: Flow<Long> = context.dataStore.data.map { it[Keys.UPDATE_POSTPONE_UNTIL] ?: 0L }
    val updatePostponeVersion: Flow<String> = context.dataStore.data.map { it[Keys.UPDATE_POSTPONE_VERSION] ?: "" }
    val updateDialogLastShownVersion: Flow<String> = context.dataStore.data.map { it[Keys.UPDATE_DIALOG_LAST_SHOWN_VERSION] ?: "" }
    val updateDialogLastShownAt: Flow<Long> = context.dataStore.data.map { it[Keys.UPDATE_DIALOG_LAST_SHOWN_AT] ?: 0L }
    val updateDialogLastActionVersion: Flow<String> = context.dataStore.data.map { it[Keys.UPDATE_DIALOG_LAST_ACTION_VERSION] ?: "" }
    val updateDialogLastAction: Flow<String> = context.dataStore.data.map { it[Keys.UPDATE_DIALOG_LAST_ACTION] ?: "" }
    val updateDialogLastActionAt: Flow<Long> = context.dataStore.data.map { it[Keys.UPDATE_DIALOG_LAST_ACTION_AT] ?: 0L }

    suspend fun saveSecretKey(key: String) {
        context.dataStore.edit { it[Keys.SECRET_KEY] = key }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun saveDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_DYNAMIC_COLOR] = enabled }
    }

    suspend fun saveThemePalette(palette: String) {
        context.dataStore.edit { it[Keys.THEME_PALETTE] = palette }
    }

    suspend fun saveLogFilters(debug: Boolean, info: Boolean, error: Boolean, isNull: Boolean) {
        context.dataStore.edit {
            it[Keys.LOG_SHOW_DEBUG] = debug
            it[Keys.LOG_SHOW_INFO] = info
            it[Keys.LOG_SHOW_ERROR] = error
            it[Keys.LOG_SHOW_NULL] = isNull
        }
    }

    suspend fun saveUpdateState(lastCheckAt: Long, latestVersion: String, error: String) {
        context.dataStore.edit {
            it[Keys.UPDATE_LAST_CHECK_AT] = lastCheckAt
            it[Keys.UPDATE_LATEST_VERSION] = latestVersion
            it[Keys.UPDATE_LAST_ERROR] = error
        }
    }

    suspend fun saveUpdateCheckIntervalHours(hours: Int) {
        context.dataStore.edit { it[Keys.UPDATE_CHECK_INTERVAL_HOURS] = hours }
    }

    suspend fun saveUpdatePostpone(version: String, until: Long) {
        context.dataStore.edit {
            it[Keys.UPDATE_POSTPONE_VERSION] = version
            it[Keys.UPDATE_POSTPONE_UNTIL] = until
        }
    }

    suspend fun saveUpdateDialogShown(version: String, shownAt: Long) {
        context.dataStore.edit {
            it[Keys.UPDATE_DIALOG_LAST_SHOWN_VERSION] = version
            it[Keys.UPDATE_DIALOG_LAST_SHOWN_AT] = shownAt
        }
    }

    suspend fun saveUpdateDialogAction(version: String, action: String, actedAt: Long) {
        context.dataStore.edit {
            it[Keys.UPDATE_DIALOG_LAST_ACTION_VERSION] = version
            it[Keys.UPDATE_DIALOG_LAST_ACTION] = action
            it[Keys.UPDATE_DIALOG_LAST_ACTION_AT] = actedAt
        }
    }

    suspend fun saveAll(isDcAuto: Boolean, dc1: String, dc2: String, dc3: String, dc4: String, dc5: String, dc203: String,
                        dc1m: String, dc2m: String, dc3m: String, dc4m: String, dc5m: String, dc203m: String,
                        isExperimental: Boolean, port: String, poolSize: Int,
                        cfproxyEnabled: Boolean, customCfDomainEnabled: Boolean, customCfDomain: String, secretKey: String) {
        context.dataStore.edit {
            it[Keys.IS_DC_AUTO] = isDcAuto
            it[Keys.DC1] = dc1
            it[Keys.DC2] = dc2
            it[Keys.DC3] = dc3
            it[Keys.DC4] = dc4
            it[stringPreferencesKey("dc5")] = dc5
            it[stringPreferencesKey("dc203")] = dc203
            it[stringPreferencesKey("dc1m")] = dc1m
            it[stringPreferencesKey("dc2m")] = dc2m
            it[stringPreferencesKey("dc3m")] = dc3m
            it[stringPreferencesKey("dc4m")] = dc4m
            it[stringPreferencesKey("dc5m")] = dc5m
            it[stringPreferencesKey("dc203m")] = dc203m
            it[Keys.IS_EXPERIMENTAL_MODE] = isExperimental
            it[Keys.PORT] = port
            it[Keys.POOL_SIZE] = poolSize
            it[Keys.CFPROXY_ENABLED] = cfproxyEnabled
            it[Keys.CUSTOM_CF_DOMAIN_ENABLED] = customCfDomainEnabled
            it[Keys.CUSTOM_CF_DOMAIN] = customCfDomain
            it[Keys.SECRET_KEY] = secretKey
        }
    }
}
