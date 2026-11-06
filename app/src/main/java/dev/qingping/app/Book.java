package dev.qingping.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

final class Book {
    static final String SKIP = "skip", CANCEL = "cancel", TOUCH = "touch";
    final SharedPreferences p;
    Book(Context context) { p = context.getSharedPreferences("qingping", Context.MODE_PRIVATE); }
    boolean enabled() { return p.getBoolean("consent", false) && p.getBoolean("enabled", true); }
    boolean selected(String pkg) { return p.getBoolean("app/" + pkg, false); }
    boolean cornersOn() { return p.getBoolean("corners", true); }
    boolean touchOn() { return p.getBoolean("shield", true); }
    void select(String pkg, String label, boolean on) {
        p.edit().putBoolean("app/" + pkg, on).putString("name/" + pkg, label).apply();
    }
    long count(String pkg, String kind) { return p.getLong("count/" + pkg + "/" + kind, 0); }
    void record(String pkg, String kind) {
        p.edit().putLong("count/" + pkg + "/" + kind, count(pkg, kind) + 1).apply();
    }
    void diagnose(String pkg, String reason) {
        if (reason.equals(p.getString("diag/" + pkg, ""))) return;
        p.edit().putString("diag/" + pkg, reason).putLong("seen/" + pkg, System.currentTimeMillis()).apply();
    }
    long total(String pkg) { return count(pkg,SKIP) + count(pkg,CANCEL) + count(pkg,TOUCH); }
    Set<String> packages() {
        Set<String> result = new HashSet<>();
        for (String key : p.getAll().keySet()) if (key.startsWith("name/")) result.add(key.substring(5));
        return result;
    }
    void clearStats() {
        SharedPreferences.Editor editor = p.edit();
        for (String key : p.getAll().keySet()) if (key.startsWith("count/")) editor.remove(key);
        editor.apply();
    }
}
