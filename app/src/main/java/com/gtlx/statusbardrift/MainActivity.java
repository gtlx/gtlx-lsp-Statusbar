package com.gtlx.statusbardrift;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Button;
import android.graphics.Color;
import android.view.Gravity;
import android.util.TypedValue;

/**
 * 状态栏漂流瓶 —— 设置界面
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "status_bar_drift";
    private static final String KEY_DRIFT_PX = "drift_px";
    private static final String KEY_INTERVAL_SEC = "interval_sec";

    private static final int DEFAULT_DRIFT_PX = 3;
    private static final int DEFAULT_INTERVAL_SEC = 30;

    private SharedPreferences mPrefs;
    private TextView mDriftValue;
    private TextView mIntervalValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefs = getSharedPreferences(PREFS_NAME, MODE_WORLD_READABLE);

        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        ll.setPadding(pad, pad, pad, pad);

        // 标题
        TextView title = new TextView(this);
        title.setText("状态栏漂流瓶");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        ll.addView(title);

        TextView sub = new TextView(this);
        sub.setText("\n周期性水平偏移状态栏，防止 OLED 烧屏\n");
        sub.setTextSize(13);
        sub.setTextColor(0xFFB0B0B0);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        ll.addView(sub);

        // 偏移幅度
        TextView driftLabel = new TextView(this);
        driftLabel.setText("偏移幅度");
        driftLabel.setTextSize(15);
        driftLabel.setTextColor(Color.WHITE);
        driftLabel.setPadding(0, dp(16), 0, dp(4));
        ll.addView(driftLabel);

        mDriftValue = new TextView(this);
        mDriftValue.setText(getCurrentDrift() + " px");
        mDriftValue.setTextSize(14);
        mDriftValue.setTextColor(0xFF4DD0E1);
        ll.addView(mDriftValue);

        SeekBar driftBar = new SeekBar(this);
        driftBar.setMax(10);
        driftBar.setProgress(getCurrentDrift() - 1);
        driftBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int px = progress + 1;
                mDriftValue.setText(px + " px");
                if (fromUser) {
                    mPrefs.edit().putInt(KEY_DRIFT_PX, px).apply();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        ll.addView(driftBar);

        // 切换周期
        TextView intervalLabel = new TextView(this);
        intervalLabel.setText("切换周期");
        intervalLabel.setTextSize(15);
        intervalLabel.setTextColor(Color.WHITE);
        intervalLabel.setPadding(0, dp(16), 0, dp(4));
        ll.addView(intervalLabel);

        mIntervalValue = new TextView(this);
        mIntervalValue.setText(getCurrentInterval() + " 秒");
        mIntervalValue.setTextSize(14);
        mIntervalValue.setTextColor(0xFF4DD0E1);
        ll.addView(mIntervalValue);

        SeekBar intervalBar = new SeekBar(this);
        intervalBar.setMax(115); // 5-120秒
        intervalBar.setProgress(getCurrentInterval() - 5);
        intervalBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int sec = progress + 5;
                mIntervalValue.setText(sec + " 秒");
                if (fromUser) {
                    mPrefs.edit().putInt(KEY_INTERVAL_SEC, sec).apply();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        ll.addView(intervalBar);

        // 重启 SystemUI 按钮
        Button restartBtn = new Button(this);
        restartBtn.setText("重启 SystemUI 立即生效");
        restartBtn.setTextColor(Color.WHITE);
        restartBtn.setBackgroundColor(0xFF00897B);
        restartBtn.setOnClickListener(v -> {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "killall com.android.systemui"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        btnLp.setMargins(0, dp(24), 0, 0);
        ll.addView(restartBtn, btnLp);

        // 说明
        TextView info = new TextView(this);
        info.setText(
            "\n\n【说明】\n" +
            "• 偏移幅度：状态栏左右漂移的最大距离\n" +
            "  1px = 几乎不可见，10px = 比较明显\n" +
            "  推荐 2-4px，防烧屏够用且不显眼\n\n" +
            "• 切换周期：每隔多久变一次位置\n" +
            "  周期越短防烧屏效果越好，但更耗电\n" +
            "  推荐 20-60 秒\n\n" +
            "修改后点「重启 SystemUI」立即生效\n\n" +
            "版本：1.2.0");
        info.setTextSize(12);
        info.setTextColor(0xFF909090);
        info.setLineSpacing(0, 1.3f);
        info.setPadding(0, dp(16), 0, 0);
        ll.addView(info);

        sv.addView(ll);
        sv.setBackgroundColor(0xFF121212);
        setContentView(sv);
    }

    private int getCurrentDrift() {
        return mPrefs.getInt(KEY_DRIFT_PX, DEFAULT_DRIFT_PX);
    }

    private int getCurrentInterval() {
        return mPrefs.getInt(KEY_INTERVAL_SEC, DEFAULT_INTERVAL_SEC);
    }

    private int dp(int px) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px,
                getResources().getDisplayMetrics());
    }
}
