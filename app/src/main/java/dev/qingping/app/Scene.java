package dev.qingping.app;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;

/** A transient snapshot of one active window. Never persists text or coordinates. */
final class Scene implements AutoCloseable {
    boolean marked, question, explicitQuestion, complete = true, populated;
    final Rect window = new Rect();
    private final ArrayList<AccessibilityNodeInfo> adParents = new ArrayList<>();
    private final ArrayList<AccessibilityNodeInfo> questionParents = new ArrayList<>();
    boolean explicitSkip;
    final Choice skip = new Choice(), cancel = new Choice(), link = new Choice();

    static final class Choice {
        AccessibilityNodeInfo node;
        boolean ambiguous;
        void add(AccessibilityNodeInfo next) {
            if (node == null) node = AccessibilityNodeInfo.obtain(next);
            else if (!node.equals(next)) ambiguous = true;
        }
        boolean ready() { return node != null && !ambiguous; }
        void release() { if (node != null) node.recycle(); }
    }

    Scene(AccessibilityNodeInfo root) {
        root.getBoundsInScreen(window);
        ArrayDeque<AccessibilityNodeInfo> work = new ArrayDeque<>();
        work.add(AccessibilityNodeInfo.obtain(root));
        int visits = 0;
        try {
            while (!work.isEmpty() && visits++ < 220) {
                AccessibilityNodeInfo n = work.removeFirst();
                try {
                    if (!n.isVisibleToUser() || n.isPassword()) continue;
                    inspect(n);
                    for (int i = 0; i < n.getChildCount(); i++) {
                        if (visits + work.size() >= 220) { complete = false; break; }
                        AccessibilityNodeInfo child = n.getChild(i);
                        if (child != null) work.addLast(child); else complete = false;
                    }
                } finally { n.recycle(); }
            }
            if (!work.isEmpty()) complete = false;
        } finally { while (!work.isEmpty()) work.removeFirst().recycle(); }
    }
    private void inspect(AccessibilityNodeInfo n) {
        String text = Rules.norm(n.getText()), desc = Rules.norm(n.getContentDescription());
        populated |= !text.isEmpty() || !desc.isEmpty();
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
        // Many real layouts put the label one level inside its clickable button.
        // Never climb into a full-screen ad or accept a substantially larger parent.
        AccessibilityNodeInfo button = n;
        Rect label = new Rect(), box = new Rect();
        n.getBoundsInScreen(label);
        if (!n.isClickable()) button = n.getParent();
        if (button == null) return;
        try {
            button.getBoundsInScreen(box);
            if (!button.isClickable() || !button.isEnabled() || !button.isVisibleToUser()
                    || box.isEmpty() || !window.contains(box) || box.width() > window.width() * .88
                    || box.height() > window.height() * .27) return;
            if (button != n && (!box.contains(label) || box.width() > label.width() * 2.8
                    || box.height() > label.height() * 2.8)) return;
            if (s) { skip.add(button); explicitSkip |= ad; }
            if (c) cancel.add(button);
            if (l) link.add(button);
        } finally { if (button != n) button.recycle(); }
    }
    private static void addParent(AccessibilityNodeInfo n, ArrayList<AccessibilityNodeInfo> parents) {
        AccessibilityNodeInfo p = n.getParent();
        if (p != null) parents.add(p);
    }
    // A label in an unrelated feed card must not authorize a button elsewhere.
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
    boolean canSkip() { return skip.ready() && (explicitSkip || nearby(skip, adParents)); }
    boolean canShield() { return nearby(link, adParents); }
    boolean canCancel() { return explicitQuestion && nearby(cancel, questionParents); }
    public void close() {
        skip.release(); cancel.release(); link.release();
        for (AccessibilityNodeInfo p : adParents) p.recycle();
        for (AccessibilityNodeInfo p : questionParents) p.recycle();
    }
}
