package com.gtlx.statusbardrift.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.gtlx.statusbardrift.config.DriftConfig

/**
 * 设置界面 —— 状态栏漂流瓶
 *
 * 风格：Soft UI + Glassmorphism（毛玻璃+柔色）
 * 主题色：emerald → teal
 */
class MainActivity : Activity() {

    private var driftPx = 3
    private var intervalSec = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        driftPx = DriftConfig.driftPx
        intervalSec = (DriftConfig.intervalMs / 1000).toInt()

        // 根布局：纵向滚动
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#f0fdf4")) // 浅绿背景
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        // === 顶部卡片 ===
        container.addView(buildHeaderCard())

        // === 偏移幅度卡片 ===
        container.addView(buildSectionTitle("📏 偏移幅度"))
        container.addView(buildSliderCard(
            minVal = 1,
            maxVal = 20,
            current = driftPx,
            unit = "px",
            description = "状态栏每次移动的距离。幅度越大，参与轮换的像素越多，防烧屏覆盖范围越广，但也越容易被肉眼察觉。推荐 2-4px：够用且几乎不可见。"
        ) { value ->
            driftPx = value
            saveConfig()
        })

        // === 切换周期卡片 ===
        container.addView(buildSectionTitle("⏱️ 切换周期"))
        container.addView(buildSliderCard(
            minVal = 5,
            maxVal = 120,
            current = intervalSec,
            unit = "秒",
            description = "多久翻转一次方向。周期越短，像素轮换越频繁，防烧屏效果越好，但 SystemUI 唤醒次数越多（耗电略增）。日常推荐 30-60 秒：效果与耗电的平衡点。"
        ) { value ->
            intervalSec = value
            saveConfig()
        })

        // === 操作按钮 ===
        container.addView(buildSectionTitle("⚡ 操作"))
        container.addView(buildButtonRow())

        // === 说明文字 ===
        container.addView(buildInfoCard(
            "💡 使用说明",
            "• 修改后立即生效，不需要重启\n" +
            "• 偏移幅度：推荐 2-4px，越大防烧范围越广但越显眼\n" +
            "• 切换周期：推荐 30-60 秒，越短效果越好但略耗电\n" +
            "• 息屏时自动暂停，亮屏恢复，不增加睡眠耗电\n" +
            "• 已有烧屏痕迹无法消除，本模块用于防止继续恶化"
        ))

        root.addView(container)
        setContentView(root)
    }

    // ===== 顶部卡片 =====
    private fun buildHeaderCard(): android.view.View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            background = glassCardBg()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(24)
            }
        }
        card.addView(TextView(this).apply {
            text = "🍃 状态栏漂流瓶"
            textSize = 24f
            setTextColor(Color.parseColor("#065f46"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "v1.0.0 · 让状态栏像素轮流休息"
            textSize = 13f
            setTextColor(Color.parseColor("#059669"))
            setPadding(0, dp(6), 0, 0)
        })
        return card
    }

    // ===== 区块标题 =====
    private fun buildSectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.parseColor("#064e3b"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(4), dp(20), dp(4), dp(10))
        }
    }

    // ===== 滑块卡片 =====
    private fun buildSliderCard(
        minVal: Int,
        maxVal: Int,
        current: Int,
        unit: String,
        description: String,
        onChanged: (Int) -> Unit
    ): android.view.View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            background = glassCardBg()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
        }

        // 数值显示行
        val valueRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val valueText = TextView(this).apply {
            text = "$current $unit"
            textSize = 22f
            setTextColor(Color.parseColor("#059669"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        val minText = TextView(this).apply {
            text = "$minVal"
            textSize = 12f
            setTextColor(Color.parseColor("#9ca3af"))
        }
        val maxText = TextView(this).apply {
            text = "$maxVal"
            textSize = 12f
            setTextColor(Color.parseColor("#9ca3af"))
        }
        valueRow.addView(valueText)
        valueRow.addView(minText)
        valueRow.addView(TextView(this).apply {
            text = "  ~  "
            textSize = 12f
            setTextColor(Color.parseColor("#d1d5db"))
        })
        valueRow.addView(maxText)

        // SeekBar
        val seekBar = SeekBar(this).apply {
            max = maxVal - minVal
            progress = current - minVal
            setPadding(0, dp(8), 0, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + minVal
                    valueText.text = "$value $unit"
                    if (fromUser) onChanged(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // 描述
        val descText = TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(Color.parseColor("#6b7280"))
            setPadding(0, dp(4), 0, 0)
        }

        card.addView(valueRow)
        card.addView(seekBar)
        card.addView(descText)
        return card
    }

    // ===== 按钮行 =====
    private fun buildButtonRow(): android.view.View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val restartBtn = Button(this).apply {
            text = "重启 SystemUI"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = gradientButtonBg()
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply {
                marginEnd = dp(8)
            }
            setOnClickListener { restartSystemUI() }
        }

        row.addView(restartBtn)
        return row
    }

    // ===== 信息卡片 =====
    private fun buildInfoCard(title: String, content: String): android.view.View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = infoCardBg()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(24)
            }
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(Color.parseColor("#047857"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })
        card.addView(TextView(this).apply {
            text = content
            textSize = 12f
            setTextColor(Color.parseColor("#065f46"))
            lineHeight = dp(18)
        })
        return card
    }

    // ===== 背景样式 =====
    private fun glassCardBg(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#ffffff"))
            setStroke(dp(1), Color.parseColor("#d1fae5"))
            // 阴影效果用 elevation 更自然，但纯代码用 stroke + 半透明模拟
        }
    }

    private fun infoCardBg(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(Color.parseColor("#ecfdf5"))
        }
    }

    private fun gradientButtonBg(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.parseColor("#10b981"),
                Color.parseColor("#0d9488")
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
        }
    }

    // ===== 工具方法 =====
    private fun dp(value: Int): Int {
        return (resources.displayMetrics.density * value).toInt()
    }

    private fun saveConfig() {
        DriftConfig.saveFromUi(this, driftPx, intervalSec)
    }

    private fun restartSystemUI() {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "killall com.android.systemui"))
            Toast.makeText(this, "SystemUI 正在重启...", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "需要 root 权限才能重启 SystemUI", Toast.LENGTH_SHORT).show()
        }
    }
}
