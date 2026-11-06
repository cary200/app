package dev.qingping.app;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;

/** One active window, held only for the current inspection. */
final class Scene implements AutoCloseable {
    boolean marked, question, explicitQuestion, sensitive, complete = true, truncated, populated;
    int visited;
    final Rect window = new Rect();
    private final ArrayList<AccessibilityNodeInfo> adParents = new ArrayList<>();
    private final ArrayList<AccessibilityNodeInfo> questionParents = new ArrayList<>();
    final Choice skip = new Choice(), cancel = new Choice(), link = new Choice();

    static final class Choice {
        AccessibilityNodeInfo node;
        final Rect label = new Rect();
        boolean ambiguous, explicit;
        void add(AccessibilityNodeInfo next, Rect bounds, boolean evidence) {
            if (node == null) {
                node = AccessibilityNodeInfo.obtain(next); label.set(bounds); explicit = evidence;
            } else if (!node.equals(next) && !label.equals(bounds)) ambiguous = true;
        }
        boolean ready() { return node != null && !ambiguous; }
        void release() { if (node != null) node.recycle(); }
    }

    Scene(AccessibilityNodeInfo root) {
        root.getBoundsInScreen(window);
        ArrayDeque<AccessibilityNodeInfo> work = new ArrayDeque<>();
        work.add(AccessibilityNodeInfo.obtain(root));
        try {
            while (!work.isEmpty() && visited++ < 800) {
                AccessibilityNodeInfo n = work.removeFirst();
                try {
                    if (n.isPassword()) continue;
                    if (n.isVisibleToUser()) inspect(n);
                    // Some containers report invisible while their descendants are visible.
                    for (int i = 0; i < n.getChildCount(); i++) {
                        if (visited + work.size() >= 800) { truncated = true; complete = false; break; }
                        AccessibilityNodeInfo child = n.getChild(i);
                        if (child != null) work.addLast(child); else complete = false;
                    }
                } finally { n.recycle(); }
            }
            if (!work.isEmpty()) { truncated = true; complete = false; }
        } finally { while (!work.isEmpty()) work.removeFirst().recycle(); }
    }
    private void inspect(AccessibilityNodeInfo n) {
        String text = Rules.norm(n.getText()), desc = Rules.norm(n.getContentDescription());
        populated |= !text.isEmpty() || !desc.isEmpty();
        sensitive |= Rules.sensitive(text) || Rules.sensitive(desc);
        boolean ad = Rules.marked(text) || Rules.marked(desc);
        marked |= ad;
        if (ad) addParent(n, adParents);
        question |= Rules.question(text) || Rules.question(desc);
        boolean explicit = (Rules.question(text) && Rules.marked(text))
                || (Rules.question(desc) && Rules.marked(desc));
        explicitQuestion |= explicit;
        if (explicit) addParent(n, questionParents);
        boolean s = Rules.skip(text) || Rules.skip(desc), c = Rules.cancel(text) || Rules.cancel(desc);
        boolean l = Rules.link(text) || Rules.link(desc);
        if (!s && !c && !l) return;
        Rect label = new Rect(); n.getBoundsInScreen(label);
        if (label.isEmpty() || !window.contains(label) || !n.isEnabled()) return;
        AccessibilityNodeInfo button = clickable(n, label);
        try {
            // Retain exact skip labels for a bounded gesture fallback even if no node is clickable.
            if (s && compact(label)) skip.add(button == null ? n : button, label,
                    Rules.explicitSkip(text) || Rules.explicitSkip(desc));
            if (button != null) {
                if (c) cancel.add(button, label, false);
                if (l) link.add(button, label, false);
            }
        } finally { if (button != null) button.recycle(); }
    }
    private boolean compact(Rect b) {
        return b.width() <= window.width() * .48 && b.height() <= window.height() * .15;
    }
    private AccessibilityNodeInfo clickable(AccessibilityNodeInfo labelNode, Rect label) {
        AccessibilityNodeInfo n = AccessibilityNodeInfo.obtain(labelNode);
        for (int depth = 0; n != null && depth < 4; depth++) {
            Rect box = new Rect(); n.getBoundsInScreen(box);
            if (n.isClickable() && n.isEnabled() && n.isVisibleToUser() && window.contains(box)
                    && box.contains(label) && box.width() <= window.width() * .88
                    && box.height() <= window.height() * .27
                    && (depth == 0 || box.width() <= label.width() * 3 && box.height() <= label.height() * 3))
                return n;
            AccessibilityNodeInfo parent = n.getParent(); n.recycle(); n = parent;
        }
        if (n != null) n.recycle();
        return null;
    }
    private static void addParent(AccessibilityNodeInfo n, ArrayList<AccessibilityNodeInfo> parents) {
        AccessibilityNodeInfo p = n.getParent();
        if (p != null) parents.add(p);
    }
    private boolean nearby(Choice c, ArrayList<AccessibilityNodeInfo> parents) {
        if (!c.ready()) return false;
        AccessibilityNodeInfo parent = c.node.getParent();
        if (parent == null) return false;
        try {
            if (parents.contains(parent)) return true;
            AccessibilityNodeInfo group = parent.getParent();
            if (group == null) return false;
            try {
                Rect bounds = new Rect(); group.getBoundsInScreen(bounds);
                return bounds.height() < window.height() * .75 && parents.contains(group);
            } finally { group.recycle(); }
        } finally { parent.recycle(); }
    }
    boolean cornerSkip() {
        Rect b = skip.label;
        return b.centerY() < window.top + window.height() * .32
                && (b.centerX() > window.left + window.width() * .65
                || b.centerX() < window.left + window.width() * .25);
    }
    boolean canSkip(boolean corners) {
        return !truncated && !sensitive && skip.ready()
                && (skip.explicit || corners && cornerSkip() || nearby(skip, adParents));
    }
    boolean canShield() { return !truncated && nearby(link, adParents); }
    boolean canCancel() { return !truncated && explicitQuestion && nearby(cancel, questionParents); }
    public void close() {
        skip.release(); cancel.release(); link.release();
        for (AccessibilityNodeInfo p : adParents) p.recycle();
        for (AccessibilityNodeInfo p : questionParents) p.recycle();
    }
}
