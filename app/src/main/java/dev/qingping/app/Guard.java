package dev.qingping.app;

import android.accessibilityservice.AccessibilityService;
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
    private boolean attemptedSkip, attemptedCancel, bypass;
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
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (key == null || !key.startsWith("count/") && !key.startsWith("name/")) reset();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (book == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName()) && !Home.class.getName().contentEquals(
                event.getClassName() == null ? "" : event.getClassName())) return;
        if (!book.enabled() || !book.selected(pkg)) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) reset();
            return;
        }
        if (!source.equals(pkg)) {
            if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
            reset(); source = pkg; entered = now();
        }
        // A fixed deadline avoids starvation when an app emits a stream of events.
        if (!scheduled) schedule(Math.max(30, 130 - (now() - lastScan)));
    }
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
            if (confirmation.active()) later();
            return;
        }
        try (Scene scene = new Scene(root)) {
            if (!scene.complete || !scene.populated) {
                hide(); confirmation.observe(now(), false, false);
                if (confirmation.active()) later();
                return;
            }
            if (confirmation.active()) { verify(scene); return; }
            // Rearm cancellation after a complete snapshot without the dialog.
            if (!scene.question) attemptedCancel = false;
            if (!attemptedCancel && scene.marked && scene.canCancel()) {
                attemptedCancel = true; click(scene.cancel.node, Book.CANCEL); return;
            }
            boolean splash = now() - entered < Rules.START_MS && scene.marked;
            if (splash && !attemptedSkip && scene.canSkip()) {
                attemptedSkip = true; click(scene.skip.node, Book.SKIP); return;
            }
            if (splash && !bypass && book.touchOn() && scene.canShield() && !scene.question) {
                Rect box = new Rect();
                scene.link.node.getBoundsInScreen(box);
                show(box, root.getWindowId()); later();
            } else hide();
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
        boolean gone = confirmation.isSkip() ? scene.skip.node == null && !scene.marked
                : !scene.question && scene.cancel.node == null;
        String kind = confirmation.observe(now(), true, gone);
        if (!kind.isEmpty()) book.record(source, kind);
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
        attemptedSkip = false; attemptedCancel = false; bypass = false;
    }
    @Override public void onInterrupt() { reset(); }
    @Override public boolean onUnbind(Intent i) { disconnect(); return super.onUnbind(i); }
    @Override public void onDestroy() { disconnect(); super.onDestroy(); }
    private void disconnect() {
        connected = false; reset();
        if (book != null) book.p.unregisterOnSharedPreferenceChangeListener(this);
    }
}
