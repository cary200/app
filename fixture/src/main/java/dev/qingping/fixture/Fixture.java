package dev.qingping.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.*;

public final class Fixture extends Activity {
    private LinearLayout box;
    private boolean resumed;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24),dp(72),dp(24),dp(32)); setContentView(box);
        String mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = getPreferences(0).getString("next","skip");
        show(mode);
    }
    @Override protected void onResume() {
        super.onResume();
        if (resumed && "reentry".equals(getIntent().getStringExtra("mode"))) {
            status("等待操作"); corner("corner");
        }
        resumed = true;
    }
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        String mode = intent.getStringExtra("mode");
        show(mode == null ? "skip" : mode);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void label(String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(20); box.addView(t); }
    private void button(String s, Runnable r) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setOnClickListener(v -> r.run());
        box.addView(b,new LinearLayout.LayoutParams(dp(180),dp(54)));
        if (s.equals("点击查看详情")) b.post(() -> {
            int[] xy = new int[2]; b.getLocationOnScreen(xy);
            getPreferences(0).edit().putInt("x",xy[0]+b.getWidth()/2).putInt("y",xy[1]+b.getHeight()/2).apply();
        });
    }
    private void status(String result) {
        getPreferences(0).edit().putString("result",result).apply();
    }
    private void end(String result) {
        status(result); box.removeAllViews(); label(result);
        button("选择下次测试场景",this::menu);
    }
    private void menu() {
        box.removeAllViews(); label("选好场景后，从桌面重新打开测试场");
        for (String[] option : new String[][]{{"skip","开屏跳过"},{"cancel","广告外跳确认"},
                {"link","广告入口防误触"},{"normal","正常外链"},{"guide","引导页跳过"},{"stuck","无效跳过按钮"}}) {
            button(option[1],() -> { getPreferences(0).edit().putString("next",option[0]).apply(); finishAndRemoveTask(); });
        }
    }
    private void corner(String mode) {
        box.removeAllViews();
        if (mode.equals("protected")) label("新手引导与用户协议");
        String caption = mode.equals("separator") ? "5s｜跳过" : "跳过";
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.RIGHT);
        box.addView(row,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView label = new TextView(this); label.setText(caption); label.setTextSize(16);
        label.setGravity(Gravity.CENTER); label.setBackgroundColor(0xffdddddd);
        if (mode.equals("deep")) {
            FrameLayout outer = new FrameLayout(this), middle = new FrameLayout(this);
            outer.setOnClickListener(v -> end("已进入首页"));
            outer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            middle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            middle.addView(label,new FrameLayout.LayoutParams(-1,-1));
            outer.addView(middle,new FrameLayout.LayoutParams(-1,-1));
            row.addView(outer,new LinearLayout.LayoutParams(dp(96),dp(44)));
        } else {
            row.addView(label,new LinearLayout.LayoutParams(dp(96),dp(44)));
            if (mode.equals("touchonly")) {
                label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                label.setOnTouchListener((v,event) -> {
                    if (event.getAction() == MotionEvent.ACTION_UP) end("已进入首页");
                    return true;
                });
            } else label.setOnClickListener(v -> end("已进入首页"));
            if (mode.equals("ignored")) label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (mode.equals("enablelater")) {
                label.setEnabled(false); label.postDelayed(() -> label.setEnabled(true),1200);
            }
        }
        if (mode.equals("ambiguous")) button("跳过广告", () -> end("误点了第二候选"));
        TextView body = new TextView(this); body.setText("活动展示内容"); body.setTextSize(28);
        box.addView(body,new LinearLayout.LayoutParams(-1,dp(320)));
    }
    private void show(String mode) {
        status("等待操作"); box.removeAllViews();
        if (mode.equals("corner") || mode.equals("deep") || mode.equals("touchonly")
                || mode.equals("ignored") || mode.equals("separator") || mode.equals("protected")
                || mode.equals("ambiguous") || mode.equals("late") || mode.equals("enablelater")) {
            if (mode.equals("late")) {
                label("加载中");
                box.postDelayed(() -> corner("corner"), 9000);
            } else corner(mode);
            return;
        }
        switch(mode) {
            case "cancel": label("是否打开广告链接？"); button("取消",() -> end("已留在原应用"));
                button("打开",() -> end("已主动打开")); break;
            case "link": label("广告"); button("点击查看详情",() -> end("已进入详情")); break;
            case "reentry":
            case "normal": label("是否打开第三方应用？"); button("取消",() -> end("取消正常链接")); break;
            case "guide": label("欢迎使用"); button("跳过",() -> end("跳过了引导")); break;
            case "stuck": label("广告"); button("跳过广告",() -> status("按钮有响应但广告仍在")); break;
            default: label("广告"); button("跳过 5s",() -> end("已进入首页"));
        }
        button("选择下次测试场景",this::menu);
    }
}
