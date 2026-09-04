package com.gtlx.statusbardrift.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.util.Log
import android.widget.TextView
import com.gtlx.statusbardrift.config.DriftConfig

/**
 * 设置界面 —— 桌面入口
 *
 * 可调参数：
 * - 偏移幅度（1-10px）
 * - 切换周期（5-120 秒）
 * - 一键重启 SystemUI
 */
class MainActivity : Activity() {

    private var driftPx = 3
    private var intervalSec = 30

    private lateinit var driftValue: TextView
    private lateinit var intervalValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadConfig()
        // 启动时确保配置文件也写到 /data/local/tmp/，SystemUI 能读到
        syncConfigToTmp()


        val sv = ScrollView(this)
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        // 标题
        ll.addView(TextView(this).apply {
            text = "状态栏漂流瓶"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
        })
        ll.addView(TextView(this).apply {
            text = "\n周期性水平偏移状态栏，防止 OLED 烧屏\n"
            textSize = 13f
            setTextColor(0xFFB0B0B0.toInt())
            gravity = Gravity.CENTER_HORIZONTAL
        })

        // 偏移幅度
        ll.addView(TextView(this).apply {
            text = "偏移幅度"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, dp(4))
        })
        driftValue = TextView(this).apply {
            text = "$driftPx px"
            textSize = 14f
            setTextColor(0xFF4DD0E1.toInt())
        }
        ll.addView(driftValue)
        ll.addView(SeekBar(this).apply {
            max = 10
            progress = driftPx - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    driftPx = p + 1
                    driftValue.text = "$driftPx px"
                    if (fromUser) saveConfig()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })

        // 切换周期
        ll.addView(TextView(this).apply {
            text = "切换周期"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, dp(4))
        })
        intervalValue = TextView(this).apply {
            text = "$intervalSec 秒"
            textSize = 14f
            setTextColor(0xFF4DD0E1.toInt())
        }
        ll.addView(intervalValue)
        ll.addView(SeekBar(this).apply {
            max = 115 // 5-120秒
            progress = intervalSec - 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    intervalSec = p + 5
                    intervalValue.text = "$intervalSec 秒"
                    if (fromUser) saveConfig()
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })

        // 重启 SystemUI 按钮
        val restartBtn = Button(this).apply {
            text = "重启 SystemUI 立即生效"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF00897B.toInt())
            setOnClickListener {
                try {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", "killall com.android.systemui"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        ll.addView(restartBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        ).apply { setMargins(0, dp(24), 0, 0) })

        // 说明
        ll.addView(TextView(this).apply {
            text = """
                
                【说明】
                • 偏移幅度：状态栏左右漂移的最大距离
                  1px = 几乎不可见，10px = 比较明显
                  推荐 2-4px，防烧屏够用且不显眼

                • 切换周期：每隔多久变一次位置
                  周期越短防烧屏效果越好，但更耗电
                  推荐 20-60 秒

                修改后配置自动热更新，大多数情况无需重启
                （幅度变化立即生效，周期变化下次切换时生效）

                版本：1.0.0
            """.trimIndent()
            textSize = 12f
            setTextColor(0xFF909090.toInt())
            setLineSpacing(0f, 1.3f)
            setPadding(0, dp(16), 0, 0)
        })

        sv.addView(ll)
        sv.setBackgroundColor(0xFF121212.toInt())
        setContentView(sv)
    }

    private fun loadConfig() {
        // 从文件读（跟 SystemUI 读同一份）
        DriftConfig.load(this)
        driftPx = DriftConfig.driftPx
        intervalSec = (DriftConfig.intervalMs / 1000).toInt()
    }

    private fun saveConfig() {
        DriftConfig.saveFromUi(this, driftPx, intervalSec)
    }

    private fun syncConfigToTmp() {
        // 用 root 权限把配置文件复制到 /data/local/tmp/，SystemUI 能读到
        Thread {
            try {
                val dir = getExternalFilesDir(null) ?: filesDir
                val src = java.io.File(dir, "status_bar_drift.conf")
                if (!src.exists()) saveConfig()
                if (src.exists()) {
                    val proc = Runtime.getRuntime().exec(arrayOf(
                        "su", "-c",
                        "cp ${src.absolutePath} /data/local/tmp/status_bar_drift.conf && chmod 644 /data/local/tmp/status_bar_drift.conf"
                    ))
                    val exitCode = proc.waitFor()
                    Log.d("StatusBarDrift", "syncConfigToTmp exitCode=$exitCode")
                }
            } catch (t: Throwable) {
                Log.e("StatusBarDrift", "syncConfigToTmp failed", t)
            }
        }.start()
    }

    private fun dp(px: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, px.toFloat(),
        resources.displayMetrics
    ).toInt()
}
