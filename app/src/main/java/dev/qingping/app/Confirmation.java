package dev.qingping.app;

/** Count only after two usable observations, separated in time, within one short attempt. */
final class Confirmation {
    private static final int DEADLINE_MS = 1800, STABLE_MS = 160;
    private String kind = "";
    private long started, firstMissing = -1;
    void start(String type, long time) {
        kind = type; started = time; firstMissing = -1;
    }
    boolean active() { return !kind.isEmpty(); }
    boolean isSkip() { return "skip".equals(kind); }
    void clear() { kind = ""; firstMissing = -1; }
    String observe(long time, boolean usable, boolean gone) {
        if (!active()) return "";
        if (time - started > DEADLINE_MS) { clear(); return ""; }
        if (!usable || !gone) firstMissing = -1;
        else if (firstMissing < 0) firstMissing = time;
        else if (time - firstMissing >= STABLE_MS) {
            String complete = kind; clear(); return complete;
        }
        return "";
    }
}
