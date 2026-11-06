package dev.qingping.app;

public final class RulesCheck {
    private static int n;
    static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
        n++;
    }
    public static void main(String[] args) {
        for (String s : new String[]{"跳过", "跳过 5s", "3秒 | 跳过", "跳过广告（3秒）", "Skip Ad (2s)"})
            check(Rules.skip(Rules.norm(s)), s);
        for (String s : new String[]{"跳过支付", "跳过本章", "广告", "返回", "点击跳过验证", "跳过广告并领取", "跳过100s"})
            check(!Rules.skip(Rules.norm(s)), s);
        for (String s : new String[]{"广告", "Skip Ad (2s)", "跳过广告", "是否打开广告链接？"})
            check(Rules.marked(Rules.norm(s)), s);
        for (String s : new String[]{"我的广告偏好", "跳过教程", "是否打开第三方应用？", "取消", "免费广告设置"})
            check(!Rules.marked(Rules.norm(s)), s);
        check(Rules.question("是否打开广告链接？"),"explicit ad");
        check(Rules.cancel("取消"),"cancel"); check(!Rules.cancel("确定"),"confirm");
        check(Rules.link("点击查看详情"),"guard link"); check(!Rules.link("支付"),"payment");
        check(Rules.norm(null).isEmpty(),"null");
        Confirmation attempt = new Confirmation();
        attempt.start("skip", 0);
        check(attempt.observe(80, true, true).isEmpty(), "one observation");
        check(attempt.observe(120, false, true).isEmpty(), "unreadable interrupts observation");
        check(attempt.observe(200, true, true).isEmpty(), "new observation");
        check(attempt.observe(330, true, true).isEmpty(), "too early");
        check(attempt.observe(370, true, true).equals("skip"), "confirmed");
        check(attempt.observe(600, true, true).isEmpty(), "no double count");
        attempt.start("cancel", 800);
        check(attempt.observe(1000, true, false).isEmpty(), "prompt remains");
        check(attempt.observe(2800, true, true).isEmpty() && !attempt.active(), "timeout");
        attempt.start("cancel", 3000); attempt.clear();
        check(!attempt.active(), "leaving source discards pending attempt");
        System.out.println(n + " rule checks passed");
    }
}
