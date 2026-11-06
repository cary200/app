package dev.qingping.app;

import java.util.Locale;
import java.util.regex.Pattern;

final class Rules {
    static final long START_MS = 8_000;
    private static final Pattern SPACE = Pattern.compile("[\\s\\p{Z}]+");
    private static final Pattern SKIP = Pattern.compile(
            "(?:跳过(?:广告)?|skip(?:ad)?)(?:[（(]?\\d{1,2}(?:s|秒)?[)）]?)?|(?:\\d{1,2}(?:s|秒)?[|｜]?)跳过");
    static String norm(CharSequence text) {
        return text == null || text.length() > 96 ? "" : SPACE.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }
    static boolean marked(String s) {
        return s.equals("广告") || s.equals("廣告") || s.equals("ad") || s.equals("advertisement")
                || s.equals("商业推广") || s.equals("开屏广告") || s.equals("跳过广告")
                || s.startsWith("跳过广告") && SKIP.matcher(s).matches()
                || s.startsWith("skipad") && SKIP.matcher(s).matches()
                || s.equals("即将打开广告链接") || s.equals("是否打开广告链接？");
    }
    static boolean skip(String s) { return SKIP.matcher(s).matches(); }
    static boolean question(String s) {
        return s.equals("即将打开广告链接") || s.equals("是否打开广告链接？")
                || s.equals("是否打开第三方应用？") || s.equals("即将打开第三方应用")
                || s.equals("是否允许打开外部应用？");
    }
    static boolean cancel(String s) {
        return s.equals("取消") || s.equals("留在当前应用") || s.equals("暂不打开") || s.equals("cancel");
    }
    static boolean link(String s) {
        return s.equals("点击跳转") || s.equals("点击前往") || s.equals("点击查看详情")
                || s.equals("立即了解") || s.equals("进入第三方应用");
    }
}
