#!/usr/bin/env python3
"""Keeps the Pleos emulator online the right way for this PC's network.

While the phone's USB tethering is connected, the emulator goes out through it
(tools/tether_proxy.py + the emulator's -http-proxy); otherwise it goes out directly,
as normal. The PC's own default route is never touched (RustDesk etc. rely on it).
Every few seconds it checks; when the tethering has come or gone for a few checks in a
row, it restarts the emulator in the other mode. It also starts the emulator again if
it stopped (the emulator sometimes crashes on its own).

usage: tools/emulator_net.py [avd]       (stop: kill the pid in build/emulator-net/watchdog.pid)
"""
import os
import re
import subprocess
import sys
import time
from pathlib import Path

AVD = sys.argv[1] if len(sys.argv) > 1 else "Pleos_Connect_v2.1.0"
SDK = Path.home() / "Android/Sdk"
ADB = str(SDK / "platform-tools/adb")
EMULATOR = str(SDK / "emulator/emulator")
TOOLS = Path(__file__).resolve().parent
STATE = TOOLS.parent / "build/emulator-net"
PROXY_PORT = 3128
CHECK_S = 5
STEADY_CHECKS = 3  # a change must hold this many checks before the emulator restarts


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)


def tether():
    """(interface, gateway) of a USB tethering link with an IPv4 address, or None."""
    out = subprocess.run(["ip", "-4", "-o", "addr"], capture_output=True, text=True).stdout
    for line in out.splitlines():
        name = line.split()[1]
        if name.startswith(("enx", "usb", "rndis")):
            route = subprocess.run(["ip", "route", "show", "default", "dev", name], capture_output=True, text=True).stdout
            gateway = re.search(r"via (\S+)", route)
            if gateway:
                return name, gateway.group(1)
    return None


def emulator_mode():
    """'proxy' or 'direct' for the running emulator, None if it isn't running."""
    out = subprocess.run(["ps", "-C", "qemu-system-x86_64", "-o", "args="], capture_output=True, text=True).stdout.strip()
    if not out:
        return None
    return "proxy" if "-http-proxy" in out else "direct"


def stop_emulator():
    subprocess.run([ADB, "emu", "kill"], capture_output=True)
    for _ in range(60):
        if emulator_mode() is None:
            return
        time.sleep(1)
    subprocess.run(["bash", "-c", "ps -C qemu-system-x86_64 -o pid= | xargs -r kill"])
    time.sleep(3)


def start_emulator(mode):
    args = [EMULATOR, "-avd", AVD, "-no-snapshot-load", "-gpu", "host"]
    if mode == "proxy":
        args += ["-http-proxy", f"http://127.0.0.1:{PROXY_PORT}"]
    with open(STATE / "emulator.log", "w") as out:
        subprocess.Popen(args, stdout=out, stderr=subprocess.STDOUT, start_new_session=True)
    # Once booted, the Virtual Console port again (tools/vconsole.py).
    for _ in range(90):
        time.sleep(3)
        booted = subprocess.run([ADB, "shell", "getprop", "sys.boot_completed"], capture_output=True, text=True).stdout.strip()
        if booted == "1":
            subprocess.run([ADB, "forward", "tcp:8088", "tcp:8088"], capture_output=True)
            log(f"emulator up ({mode})")
            return
    log("emulator did not finish booting in time")


class Proxy:
    def __init__(self):
        self.process = None
        self.link = None

    def ensure(self, link):
        if self.process and self.process.poll() is None and self.link == link:
            return
        self.stop()
        iface, gateway = link
        out = open(STATE / "proxy.log", "a")
        self.process = subprocess.Popen(
            [sys.executable, "-u", str(TOOLS / "tether_proxy.py"), iface, str(PROXY_PORT), gateway],
            stdout=out, stderr=subprocess.STDOUT, start_new_session=True,
        )
        self.link = link
        log(f"proxy via {iface} (DNS {gateway})")
        time.sleep(1)

    def stop(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            self.process.wait(5)
        self.process = None
        self.link = None


def main():
    STATE.mkdir(parents=True, exist_ok=True)
    (STATE / "watchdog.pid").write_text(str(os.getpid()))
    proxy = Proxy()
    pending, count = None, 0
    log(f"watching: emulator {emulator_mode() or 'not running'}, tethering {tether() or 'none'}")
    while True:
        link = tether()
        want = "proxy" if link else "direct"
        if link:
            proxy.ensure(link)
        mode = emulator_mode()
        if mode is None:
            log(f"emulator not running: starting ({want})")
            start_emulator(want)
        elif mode != want:
            count = count + 1 if pending == want else 1
            pending = want
            if count >= STEADY_CHECKS:
                log(f"tethering {'connected' if link else 'gone'}: restarting emulator ({want})")
                stop_emulator()
                if not link:
                    proxy.stop()
                start_emulator(want)
                pending, count = None, 0
        else:
            pending, count = None, 0
        time.sleep(CHECK_S)


if __name__ == "__main__":
    main()
