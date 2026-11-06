package dev.qingping.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.*;
import java.util.*;

public final class Home extends Activity {
    private static final int INK = 0xff17352f, MUTED = 0xff4a635c, GREEN = 0xff006855;
    private Book book;
    private LinearLayout content;
    private String page = "home";
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable update = this::render;
    private int connectionChecks;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener changes = (p,k) -> {
        if (k != null && k.startsWith("count/") && !page.equals("apps")) {
            ui.removeCallbacks(update); ui.postDelayed(update, 180);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); book = new Book(this);
        if (state != null) page = state.getString("page", "home");
    }
    @Override protected void onSaveInstanceState(Bundle s) { super.onSaveInstanceState(s); s.putString("page",page); }
    @Override protected void onResume() { super.onResume(); connectionChecks = 0; book.p.registerOnSharedPreferenceChangeListener(changes); render(); }
    @Override protected void onPause() { book.p.unregisterOnSharedPreferenceChangeListener(changes); ui.removeCallbacks(update); super.onPause(); }
    @Override public void onBackPressed() { if (!page.equals("home")) go("home"); else super.onBackPressed(); }
    private void go(String next) { page = next; render(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(0,dp(3),0,dp(5)); return v;
    }
    private GradientDrawable rounded(int color) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(18)); return bg;
    }
    private LinearLayout card(int color) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(14),dp(18),dp(14)); box.setBackground(rounded(color));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.bottomMargin = dp(12);
        content.addView(box,lp); return box;
    }
    private void button(LinearLayout parent, String label, boolean primary, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setTextColor(primary ? -1 : GREEN); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? GREEN : 0xffe2efe8));
        b.setOnClickListener(v -> action.run()); parent.addView(b,new LinearLayout.LayoutParams(-1,-2));
    }
    private void render() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(0xfff3f6f3);
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bar = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                v.setPadding(bar.left,bar.top,bar.right,bar.bottom); return insets;
            });
        }
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(18),dp(20),dp(24)); scroll.addView(content); setContentView(scroll);
        if (!page.equals("home")) button(content,"返回清屏",false,() -> go("home"));
        String title = page.equals("apps") ? "保护哪些应用" : page.equals("stats") ? "拦截统计" : "清屏";
        content.addView(text(title,32,INK,true));
        if (page.equals("apps")) apps(); else if (page.equals("stats")) stats(); else home();
    }
    private boolean authorized() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        ComponentName expected = new ComponentName(this, Guard.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ResolveInfo r = info.getResolveInfo();
            if (r != null && r.serviceInfo != null && expected.equals(new ComponentName(r.serviceInfo.packageName,r.serviceInfo.name))) return true;
        }
        return false;
    }
    private void home() {
        int selected = 0; long total = 0;
        for (String pkg : book.packages()) { if (book.selected(pkg)) selected++; total += book.total(pkg); }
        LinearLayout status = card(INK);
        String s = !book.p.getBoolean("consent",false) ? "等待授权" : !book.enabled() ? "防护已暂停"
                : !authorized() ? "无障碍未开启" : !Guard.connected ? "等待系统连接"
                : selected == 0 ? "请选择应用" : "防护已开启";
        status.addView(text(s,25,-1,true));
        if (s.equals("等待系统连接") && connectionChecks++ < 6) {
            ui.removeCallbacks(update); ui.postDelayed(update, 750);
        }
        status.addView(text("已选 " + selected + " 个应用   ·   累计处理 " + total + " 次",15,0xffc7e8d6,false));
        LinearLayout cfg = card(-1);
        button(cfg,book.p.getBoolean("consent",false) ? "无障碍服务设置" : "了解权限并启用",true,this::permission);
        button(cfg,"选择要保护的应用",false,() -> go("apps"));
        button(cfg,"查看各应用统计",false,() -> go("stats"));
        if (book.p.getBoolean("consent",false)) {
            button(cfg,book.enabled() ? "暂停防护" : "恢复防护",false,() -> {
                book.p.edit().putBoolean("enabled",!book.enabled()).apply(); render();
            });
        }
        Switch corners = new Switch(this); corners.setText("开屏角落加强识别"); corners.setTextColor(INK);
        corners.setMinHeight(dp(48)); corners.setChecked(book.cornersOn());
        corners.setOnCheckedChangeListener((v,on) -> book.p.edit().putBoolean("corners",on).apply()); cfg.addView(corners);
        cfg.addView(text("支持只写「跳过」的角落按钮。若某个应用的引导页被跳过，可关闭此项。",14,MUTED,false));
        Switch shield = new Switch(this); shield.setText("广告入口防误触"); shield.setTextColor(INK);
        shield.setMinHeight(dp(48)); shield.setChecked(book.touchOn());
        shield.setOnCheckedChangeListener((v,on) -> book.p.edit().putBoolean("shield",on).apply()); cfg.addView(shield);
        cfg.addView(text("识别到开屏广告入口时，用绿色「防误触」挡住按钮。轻点会拦下本次触摸，长按可在这次开屏期间放行。",14,MUTED,false));
        LinearLayout diagnosis = card(-1);
        diagnosis.addView(text("运行诊断 · 0.2.0",18,INK,true));
        diagnosis.addView(text("测试后回到这里查看识别结果。只记录应用和处理状态。",14,MUTED,false));
        for (String pkg : new TreeSet<>(book.packages())) {
            if (!book.selected(pkg)) continue;
            String name = book.p.getString("name/" + pkg,pkg);
            long seen = book.p.getLong("seen/" + pkg,0);
            String time = seen == 0 ? "" : " · " + new java.text.SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date(seen));
            diagnosis.addView(text(name + time + "\n" + book.p.getString("diag/" + pkg,"尚未收到此应用的界面事件"),14,MUTED,false));
        }
        LinearLayout guide = card(0xffe5eee8);
        guide.addView(text("开机与后台运行",18,INK,true));
        guide.addView(text("首次授权后由系统连接服务。重启并解锁后，可回到这里查看状态。若手机管家频繁清理服务，可在清屏的应用详情中允许自启动和后台运行（选项因机型而异）。强行停止后请重新打开清屏，检查无障碍开关。",14,MUTED,false));
        button(guide,"打开清屏的应用详情",false,() -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + getPackageName()))));
    }
    private void permission() {
        new AlertDialog.Builder(this).setTitle("允许清屏读取界面并操作广告按钮？")
                .setMessage("系统无障碍权限范围较广。清屏用窗口变更事件判断前台应用，只在所选应用内读取控件文字、位置和按钮状态。它会在应用进入前台的前15秒识别角落的跳过按钮。普通点击失败时，可在该按钮位置模拟轻点；也会取消明确的广告跳转询问，并可在广告入口上方挡住触摸。角落识别可能跳过部分引导页。\n\n统计只在本机保存应用名称、包名和次数。清屏没有联网权限，不保存页面文字、截图或输入内容。你随时可以暂停或在系统设置关闭服务。")
                .setNegativeButton("暂不开启",null).setPositiveButton("同意，前往设置",(d,w) -> {
                    book.p.edit().putBoolean("consent",true).apply(); startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }).show();
    }
    private void apps() {
        content.addView(text("只处理你勾选的应用。关闭保护会保留已有统计。",15,MUTED,false));
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = getPackageManager().queryIntentActivities(launcher,0);
        found.sort(new ResolveInfo.DisplayNameComparator(getPackageManager()));
        Set<String> seen = new HashSet<>();
        seen.add(getPackageName()); seen.add("com.android.settings"); seen.add("com.android.systemui");
        for (ResolveInfo home : getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0))
            seen.add(home.activityInfo.packageName);
        LinearLayout rows = card(-1);
        for (ResolveInfo item : found) {
            String pkg = item.activityInfo.packageName; if (!seen.add(pkg)) continue;
            String name = item.loadLabel(getPackageManager()).toString();
            CheckBox b = new CheckBox(this); b.setText(name + "\n" + pkg); b.setTextColor(INK);
            b.setMinHeight(dp(56)); b.setChecked(book.selected(pkg)); b.setOnCheckedChangeListener((v,on) -> book.select(pkg,name,on));
            rows.addView(b);
        }
        if (rows.getChildCount() == 0) rows.addView(text("当前未找到可选择的桌面应用。",15,MUTED,false));
    }
    private void stats() {
        List<String> pkgs = new ArrayList<>(book.packages());
        pkgs.sort((a,b) -> { int c = Long.compare(book.total(b),book.total(a)); return c != 0 ? c : a.compareTo(b); });
        long all = 0; for (String pkg : pkgs) all += book.total(pkg);
        LinearLayout hero = card(INK); hero.addView(text("累计处理次数",15,0xffcae6d7,false));
        hero.addView(text(Long.toString(all),38,-1,true));
        if (all == 0) content.addView(text("还没有拦截记录。选好应用并启用服务后，匹配到的操作会出现在这里。",15,MUTED,false));
        for (String pkg : pkgs) {
            long n = book.total(pkg); if (n == 0) continue;
            LinearLayout row = card(-1); row.addView(text(book.p.getString("name/" + pkg,pkg) + "  ·  " + n + " 次",19,INK,true));
            row.addView(text(pkg,12,MUTED,false));
            row.addView(text("跳过开屏  " + book.count(pkg,Book.SKIP) + "     取消外跳  " + book.count(pkg,Book.CANCEL)
                    + "     挡住触摸  " + book.count(pkg,Book.TOUCH),14,INK,false));
        }
        content.addView(text("前两项要求按钮响应，随后在原应用内连续观察到对应提示消失。防误触按挡住的轻点计数。多次轻点可能来自同一条广告。",14,MUTED,false));
        if (all > 0) button(content,"清空全部统计",false,() -> new AlertDialog.Builder(this).setTitle("清空全部拦截次数？")
                .setMessage("此操作无法撤销，应用选择会保留。").setNegativeButton("取消",null)
                .setPositiveButton("清空",(d,w) -> { book.clearStats(); render(); }).show());
    }
}
