"""End-to-end checks for a disposable Android emulator. Never use on a personal phone."""
import subprocess
import time
import xml.etree.ElementTree as ET

APP = "dev.qingping.app"
FIX = "dev.qingping.fixture"


def adb(*args, data=None, check=True):
    return subprocess.run(["adb", *args], input=data, capture_output=True,
                          text=True, check=check).stdout.strip()


def shell(*args):
    return adb("shell", *args)


def prefs(pkg, name):
    raw = adb("exec-out", "run-as", pkg, "cat", f"shared_prefs/{name}.xml", check=False)
    try:
        return {e.get("name"): e.get("value", e.text or "") for e in ET.fromstring(raw)}
    except ET.ParseError:
        return {}


def until(get, expected, msg, timeout=6):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        val = get()
        if val == expected:
            return
        time.sleep(.12)
    raise AssertionError(f"{msg}: expected {expected!r}, got {val!r}")


def result():
    return prefs(FIX, "Fixture").get("result")


def count(kind):
    return int(prefs(APP, "qingping").get(f"count/{FIX}/{kind}", "0"))


def launch(mode):
    shell("input", "keyevent", "KEYCODE_HOME")
    shell("am", "force-stop", FIX)
    time.sleep(.35)
    shell("am", "start", "-W", "-n", f"{FIX}/.Fixture", "--es", "mode", mode)


def main():
    assert shell("getprop", "ro.kernel.qemu") == "1", "Disposable emulator required"
    for module in ("app", "fixture"):
        adb("install", "-r", f"{module}/build/outputs/apk/debug/{module}-debug.apk")
    previous = shell("settings", "get", "secure", "enabled_accessibility_services")
    enabled = shell("settings", "get", "secure", "accessibility_enabled")
    assert previous in ("null", ""), "Use a fresh test emulator"
    try:
        # Only this disposable debug build is preconfigured. Production has no IPC for this.
        shell("am", "start", "-W", "-n", f"{APP}/.Home")
        shell("am", "force-stop", APP)
        shell("run-as", APP, "mkdir", "-p", "shared_prefs")
        xml = (f'<map><boolean name="consent" value="true"/><boolean name="app/{FIX}" '
               f'value="true"/><string name="name/{FIX}">fixture</string></map>')
        adb("shell", f"run-as {APP} sh -c 'cat > shared_prefs/qingping.xml'", data=xml)
        shell("am", "start", "-W", "-n", f"{APP}/.Home")
        shell("settings", "put", "secure", "enabled_accessibility_services", f"{APP}/.Guard")
        shell("settings", "put", "secure", "accessibility_enabled", "1")
        time.sleep(2)
        launch("skip")
        until(result, "已进入首页", "skip operates on fixture")
        until(lambda: count("skip"), 1, "attributed skip")
        launch("cancel")
        until(result, "已留在原应用", "cancel ad confirmation")
        until(lambda: count("cancel"), 1, "attributed cancel")
        for mode in ("guide", "normal", "stuck"):
            launch(mode)
            time.sleep(2.3)
            expected = "按钮有响应但广告仍在" if mode == "stuck" else "等待操作"
            assert result() == expected, mode
            assert count("skip") == 1 and count("cancel") == 1, mode
        launch("link")
        time.sleep(.6)
        xy = prefs(FIX, "Fixture")
        x, y = xy["x"], xy["y"]
        shell("input", "tap", x, y)
        until(lambda: count("touch"), 1, "physical tap intercepted")
        assert result() == "等待操作"
        shell("input", "swipe", x, y, x, y, "900")
        shell("input", "tap", x, y)
        until(result, "已进入详情", "long press bypass")
        assert count("touch") == 1, "bypass is not an interception"
        print("Cross-package fixture checks passed")
    finally:
        shell("settings", "delete", "secure", "enabled_accessibility_services")
        if enabled == "null":
            shell("settings", "delete", "secure", "accessibility_enabled")
        else:
            shell("settings", "put", "secure", "accessibility_enabled", enabled)


if __name__ == "__main__":
    main()
