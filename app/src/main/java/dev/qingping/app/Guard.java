package dev.qingping.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

public final class Guard extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    static boolean connected;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean scheduled;
    private final Runnable scan = () -> { scheduled = false; inspect(); };
    private Book book;
    private String source = "";
    private final Confirmation confirmation = new Confirmation();
    private long entered, lastScan;
    private boolean skipped, attemptedCancel, bypass, gesturePending;
    private int skipAttempts, session;
    private TextView shield;
    private Rect shieldBox;
    private int shieldWindow;
    private WindowManager wm;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        book = new Book(this);
        book.p.registerOnSharedPreferenceChangeListener(this);
        wm = getSystemService(WindowManager.class);
        connected = true;
        connectForeground();
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (key == null || key.equals("enabled") || key.equals("consent") || key.equals("shield") || key.equals("corners") || key.startsWith("app/")) {
            reset(); connectForeground();
        }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (book == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName()) && !Home.class.getName().contentEquals(
                event.getClassName() == null ? "" : event.getClassName())) return;
        if (!book.enabled()) { reset(); return; }
        if (!book.selected(pkg)) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    String activePkg = String.valueOf(root.getPackageName()); root.recycle();
                    if (!activePkg.equals(source)) reset();
                }
            }
            return;
        }
        if (!source.equals(pkg)) {
            // Content events may arrive without an activity transition (warm starts/WebViews).
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                reset(); source = pkg; entered = now();
            } else {
                boolean foreground = pkg.contentEquals(root.getPackageName() == null ? "" : root.getPackageName());
                root.recycle();
                if (!foreground) return;
                reset(); source = pkg; entered = now();
            }
            note("已收到界面事件，正在识别");
        }
        // A fixed deadline avoids starvation when an app emits a stream of events.
        if (!scheduled) schedule(Math.max(30, 130 - (now() - lastScan)));
    }
    private void connectForeground() {
        if (book == null || !book.enabled()) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        String pkg = String.valueOf(root.getPackageName()); root.recycle();
        if (!pkg.equals(getPackageName()) && book.selected(pkg)) {
            source = pkg; entered = now(); note("服务已连接，正在识别"); later();
        }
    }
    private void note(String reason) { if (!source.isEmpty()) book.diagnose(source, reason); }
    private void pollLaunch() { if (!source.isEmpty() && now() - entered < Rules.START_MS && unlocked()) later(); }
    private long now() { return SystemClock.uptimeMillis(); }
    private boolean unlocked() {
        return getSystemService(PowerManager.class).isInteractive()
                && !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }
    private AccessibilityNodeInfo active() {
        if (source.isEmpty() || !book.enabled() || !book.selected(source) || !unlocked()) return null;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        if (source.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) return root;
        boolean overlayActive = shield != null && getPackageName().contentEquals(
                root.getPackageName() == null ? "" : root.getPackageName());
        root.recycle();
        if (!overlayActive) return null;
        // On some builds a touched accessibility overlay becomes the active window.
        // Inspect only the previously verified application window if it still holds input focus.
        List<AccessibilityWindowInfo> windows = getWindows();
        try {
            for (AccessibilityWindowInfo w : windows) {
                if (w.getId() != shieldWindow || !w.isFocused()
                        || w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo underlying = w.getRoot();
                if (underlying == null) continue;
                if (source.contentEquals(underlying.getPackageName() == null ? "" : underlying.getPackageName())) return underlying;
                underlying.recycle();
            }
            return null;
        } finally { for (AccessibilityWindowInfo w : windows) w.recycle(); }
    }
    private void inspect() {
        lastScan = now();
        AccessibilityNodeInfo root = active();
        if (root == null) {
            hide();
            confirmation.observe(now(), false, false);
            note("暂时读不到活动页面，等待加载");
            if (confirmation.active()) later(); else pollLaunch();
            return;
        }
        try (Scene scene = new Scene(root)) {
            if (gesturePending) { later(); return; }
            if (scene.truncated || !scene.populated) {
                hide(); confirmation.observe(now(), false, false);
                note(scene.truncated ? "页面控件过多，已停止本次识别" : "已收到页面，但没有可读的按钮文字");
                if (confirmation.active()) later(); else pollLaunch();
                return;
            }
            if (confirmation.active()) { verify(scene); return; }
            // Rearm cancellation after a complete snapshot without the dialog.
            if (!scene.question) attemptedCancel = false;
            if (!attemptedCancel && scene.marked && scene.canCancel()) {
                attemptedCancel = true; click(scene.cancel.node, Book.CANCEL); return;
            }
            boolean splash = now() - entered < Rules.START_MS;
            if (splash && !skipped && skipAttempts < 2 && scene.canSkip(book.cornersOn())) {
                skip(scene, root.getWindowId()); return;
            }
            if (splash && !skipped && skipAttempts == 0) {
                note(scene.skip.node == null ? "页面可读取，未找到匹配的跳过文字"
                        : scene.sensitive ? "遇到协议或引导内容，已停止自动点击" : "发现跳过文字，位置或候选不明确");
            }
            if (splash && scene.marked && !bypass && book.touchOn() && scene.canShield() && !scene.question) {
                Rect box = new Rect();
                scene.link.node.getBoundsInScreen(box);
                show(box, root.getWindowId()); later();
            } else { hide(); pollLaunch(); }
        } finally { root.recycle(); }
    }
    private void skip(Scene scene, int windowId) {
        hide();
        AccessibilityNodeInfo node = scene.skip.node;
        boolean fallback = skipAttempts > 0;
        skipAttempts++;
        if (!node.refresh() || !node.isVisibleToUser() || !node.isEnabled()) { pollLaunch(); return; }
        if (!fallback && node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            note("已点击跳过，等待确认"); confirmation.start(Book.SKIP, now()); later(); return;
        }
        // Re-read the active window and the candidate just before touching the exact label.
        AccessibilityNodeInfo root = active();
        if (root == null) { pollLaunch(); return; }
        try (Scene fresh = new Scene(root)) {
            if (root.getWindowId() != windowId || !fresh.canSkip(book.cornersOn())
                    || !fresh.skip.label.equals(scene.skip.label) || now() - entered >= Rules.START_MS
                    || getMagnificationController().getScale() > 1.01f) { pollLaunch(); return; }
            Rect bounds = fresh.skip.label;
            Path path = new Path(); path.moveTo(bounds.exactCenterX(), bounds.exactCenterY());
            int currentSession = session;
            gesturePending = true;
            boolean accepted = dispatchGesture(new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 60)).build(),
                    new GestureResultCallback() {
                        @Override public void onCompleted(GestureDescription gesture) {
                            if (session != currentSession) return;
                            gesturePending = false;
                            note("已模拟轻点跳过，等待确认"); confirmation.start(Book.SKIP, now()); later();
                        }
                        @Override public void onCancelled(GestureDescription gesture) {
                            if (session != currentSession) return;
                            gesturePending = false; note("模拟点击被系统取消"); pollLaunch();
                        }
                    }, handler);
            if (!accepted) { gesturePending = false; note("系统未接受模拟点击，请重新开关无障碍"); }
            later();
        } finally { root.recycle(); }
    }
    private void click(AccessibilityNodeInfo node, String type) {
        hide();
        if (node.refresh() && node.isVisibleToUser() && node.isEnabled()
                && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            confirmation.start(type, now());
        }
        later();
    }
    private void verify(Scene scene) {
        boolean gone = confirmation.isSkip() ? scene.skip.node == null
                : !scene.question && scene.cancel.node == null;
        String kind = confirmation.observe(now(), scene.complete, gone);
        if (!kind.isEmpty()) {
            book.record(source, kind);
            if (kind.equals(Book.SKIP)) { skipped = true; note("已确认跳过成功"); }
        } else if (!confirmation.active()) note("点击后未确认消失，正在检查是否需要重试");
        later();
    }
    private void later() { schedule(180); }
    private void schedule(long delay) {
        handler.removeCallbacks(scan); scheduled = true; handler.postDelayed(scan, delay);
    }
    private void show(Rect box, int window) {
        if (shield != null && box.equals(shieldBox) && window == shieldWindow) return;
        hide();
        TextView v = new TextView(this);
        v.setText("防误触"); v.setGravity(Gravity.CENTER); v.setTextColor(0xffffffff);
        v.setTextSize(12); v.setBackgroundColor(0xff236958);
        v.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        v.setContentDescription("广告入口防误触。轻点拦下，长按临时放行。");
        v.setOnClickListener(view -> { if (stillCoversTarget()) book.record(source, Book.TOUCH); });
        v.setOnLongClickListener(view -> { bypass = true; hide(); return true; });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(box.width(), box.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT; lp.x = box.left; lp.y = box.top;
        shield = v; shieldBox = new Rect(box); shieldWindow = window;
        try {
            wm.addView(v, lp);
            v.post(() -> {
                if (v != shield) return;
                int[] xy = new int[2]; v.getLocationOnScreen(xy);
                if (Math.abs(xy[0] - box.left) > 2 || Math.abs(xy[1] - box.top) > 2
                        || v.getWidth() != box.width() || v.getHeight() != box.height()) {
                    bypass = true; hide();
                }
            });
        } catch (WindowManager.BadTokenException | SecurityException error) { bypass = true; hide(); }
    }
    private boolean stillCoversTarget() {
        AccessibilityNodeInfo root = active();
        if (root == null) { hide(); return false; }
        try (Scene s = new Scene(root)) {
            Rect box = new Rect();
            if (s.canShield()) s.link.node.getBoundsInScreen(box);
            if (book.touchOn() && now() - entered < Rules.START_MS && root.getWindowId() == shieldWindow
                    && s.complete && s.marked && !s.question && box.equals(shieldBox)) return true;
            hide(); return false;
        } finally { root.recycle(); }
    }
    private void hide() {
        if (shield != null) {
            try { wm.removeViewImmediate(shield); }
            catch (IllegalArgumentException ignored) { /* The system already removed this window. */ }
            shield = null; shieldBox = null;
        }
    }
    private void reset() {
        handler.removeCallbacks(scan); scheduled = false; hide(); source = ""; confirmation.clear();
        skipped = false; skipAttempts = 0; attemptedCancel = false; bypass = false;
        gesturePending = false; session++;
    }
    @Override public void onInterrupt() { reset(); }
    @Override public boolean onUnbind(Intent i) { disconnect(); return super.onUnbind(i); }
    @Override public void onDestroy() { disconnect(); super.onDestroy(); }
    private void disconnect() {
        connected = false; reset();
        if (book != null) book.p.unregisterOnSharedPreferenceChangeListener(this);
    }
}
