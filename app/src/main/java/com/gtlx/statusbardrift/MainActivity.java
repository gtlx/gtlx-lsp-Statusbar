package com.gtlx.statusbardrift;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Button;
import android.graphics.Color;
import android.view.Gravity;
import android.util.TypedValue;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * 状态栏漂流瓶 —— 设置界面
 */
public class MainActivity extends Activity {

    private static final String CONFIG_FILE = "status_bar_drift.conf";
    private static final String KEY_DRIFT_PX = "drift_px";
    private static final String KEY_INTERVAL_SEC = "interval_sec";

    private static final int DEFAULT_DRIFT_PX = 3;
    private static final int DEFAULT_INTERVAL_SEC = 30;

    private TextView mDriftValue;
    private TextView mIntervalValue;
    private int mDriftPx = DEFAULT_DRIFT_PX;
    private int mIntervalSec = DEFAULT_INTERVAL_SEC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadConfig();

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
        mDriftValue.setText(mDriftPx + " px");
        mDriftValue.setTextSize(14);
        mDriftValue.setTextColor(0xFF4DD0E1);
        ll.addView(mDriftValue);

        SeekBar driftBar = new SeekBar(this);
        driftBar.setMax(10);
        driftBar.setProgress(mDriftPx - 1);
        driftBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mDriftPx = progress + 1;
                mDriftValue.setText(mDriftPx + " px");
                if (fromUser) saveConfig();
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
        mIntervalValue.setText(mIntervalSec + " 秒");
        mIntervalValue.setTextSize(14);
        mIntervalValue.setTextColor(0xFF4DD0E1);
        ll.addView(mIntervalValue);

        SeekBar intervalBar = new SeekBar(this);
        intervalBar.setMax(115); // 5-120秒
        intervalBar.setProgress(mIntervalSec - 5);
        intervalBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mIntervalSec = progress + 5;
                mIntervalValue.setText(mIntervalSec + " 秒");
                if (fromUser) saveConfig();
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

    private File getConfigFile() {
        // 存在外部存储 app 私有目录，SystemUI 可以读到
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        return new File(dir, CONFIG_FILE);
    }

    private void loadConfig() {
        try {
            File f = getConfigFile();
            if (!f.exists()) return;
            Properties props = new Properties();
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            props.load(fis);
            fis.close();
            mDriftPx = Integer.parseInt(props.getProperty(KEY_DRIFT_PX, String.valueOf(DEFAULT_DRIFT_PX)));
            mIntervalSec = Integer.parseInt(props.getProperty(KEY_INTERVAL_SEC, String.valueOf(DEFAULT_INTERVAL_SEC)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        new Thread(() -> {
            try {
                File f = getConfigFile();
                Properties props = new Properties();
                props.setProperty(KEY_DRIFT_PX, String.valueOf(mDriftPx));
                props.setProperty(KEY_INTERVAL_SEC, String.valueOf(mIntervalSec));
                FileOutputStream fos = new FileOutputStream(f);
                props.store(fos, "StatusBarDrift config");
                fos.close();
                // 设置全局可读，SystemUI 才能读
                f.setReadable(true, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private int dp(int px) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, px,
                getResources().getDisplayMetrics());
    }
}
