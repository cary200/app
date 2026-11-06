package dev.qingping.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;

public final class Fixture extends Activity {
    private LinearLayout box;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24),dp(72),dp(24),dp(32)); setContentView(box);
        String mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = getPreferences(0).getString("next","skip");
        show(mode);
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
    private void show(String mode) {
        status("等待操作"); box.removeAllViews();
        switch(mode) {
            case "cancel": label("是否打开广告链接？"); button("取消",() -> end("已留在原应用"));
                button("打开",() -> end("已主动打开")); break;
            case "link": label("广告"); button("点击查看详情",() -> end("已进入详情")); break;
            case "normal": label("是否打开第三方应用？"); button("取消",() -> end("取消正常链接")); break;
            case "guide": label("欢迎使用"); button("跳过",() -> end("跳过了引导")); break;
            case "stuck": label("广告"); button("跳过广告",() -> status("按钮有响应但广告仍在")); break;
            default: label("广告"); button("跳过 5s",() -> end("已进入首页"));
        }
        button("选择下次测试场景",this::menu);
    }
}
