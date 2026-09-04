package com.gtlx.statusbardrift.config

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle

/**
 * 配置 ContentProvider —— 跨进程共享配置（无 root 标准方案）
 *
 * SystemUI/Launcher 等目标进程通过 ContentResolver 读写配置。
 * 热更新：目标进程注册 ContentObserver 监听 URI 变化。
 *
 * 支持的操作：
 * - query(uri, null, null, null, null) → 返回所有配置
 * - call("get_config", null, null) → Bundle 形式返回配置
 * - call("set_config", null, bundle) → 更新配置
 */
class ConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.gtlx.statusbardrift.configprovider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/config")

        const val METHOD_GET_CONFIG = "get_config"
        const val METHOD_SET_CONFIG = "set_config"

        const val KEY_DRIFT_PX = "drift_px"
        const val KEY_INTERVAL_SEC = "interval_sec"
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val context = context ?: return MatrixCursor(arrayOf())
        val config = DriftConfig

        val cursor = MatrixCursor(arrayOf(KEY_DRIFT_PX, KEY_INTERVAL_SEC))
        cursor.addRow(arrayOf(config.driftPx, config.intervalMs / 1000))
        cursor.setNotificationUri(context.contentResolver, uri)
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null

        return when (method) {
            METHOD_GET_CONFIG -> {
                Bundle().apply {
                    putInt(KEY_DRIFT_PX, DriftConfig.driftPx)
                    putInt(KEY_INTERVAL_SEC, (DriftConfig.intervalMs / 1000).toInt())
                }
            }
            METHOD_SET_CONFIG -> {
                if (extras != null) {
                    val driftPx = extras.getInt(KEY_DRIFT_PX, DriftConfig.driftPx)
                    val intervalSec = extras.getInt(KEY_INTERVAL_SEC, (DriftConfig.intervalMs / 1000).toInt())
                    DriftConfig.saveFromProvider(context, driftPx, intervalSec)
                    // 通知 ContentObserver 配置变了
                    context.contentResolver.notifyChange(CONTENT_URI, null)
                }
                Bundle().apply { putBoolean("success", true) }
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.$AUTHORITY.config"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
