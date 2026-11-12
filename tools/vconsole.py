#!/usr/bin/env python3
"""Drive the Pleos Connect emulator's Virtual Console from the command line.

The Virtual Console only accepts commands while a "browser" is attached, which it
detects through a heartbeat POST every second. This script sends that heartbeat
itself, so no browser tab is needed.

Usage (run `adb forward tcp:8088 tcp:8088` first):
    tools/vconsole.py gear D                 # P / R / N / D
    tools/vconsole.py drive 60               # set speed (km/h), brake released
    tools/vconsole.py brake 80               # press brake pedal (0-100 %)
    tools/vconsole.py park                   # speed 0, brake, gear P
    tools/vconsole.py seat passenger on      # someone sits down (driver / passenger / rear_left / rear_right; on / off)
    tools/vconsole.py state                  # print gear / speed / pedals
    tools/vconsole.py replay route.csv       # replay "seconds,gear,speed_kph,brake_pct[,label]" keyframes,
                                             # speed eased between keyframes every 0.25 s
"""
import argparse
import csv
import json
import sys
import threading
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8088"
CLIENT = "together-ro-cli"
GEARS = {"P": "Park", "R": "Reverse", "N": "Neutral", "D": "Drive"}


def post(path):
    req = urllib.request.Request(BASE + path, method="POST", data=b"")
    with urllib.request.urlopen(req, timeout=5) as resp:
        return json.loads(resp.read() or b"{}")


def get(path):
    with urllib.request.urlopen(BASE + path, timeout=5) as resp:
        return json.loads(resp.read())


class Heartbeat:
    """Keeps the Virtual Console session active while commands run."""

    def __init__(self):
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, daemon=True)

    def _run(self):
        while not self._stop.is_set():
            try:
                post(f"/browser/session?client={CLIENT}")
            except (urllib.error.URLError, OSError):
                pass
            self._stop.wait(1.0)

    def __enter__(self):
        self._thread.start()
        # The console needs a beat or two before it reports emulator_active.
        for _ in range(20):
            if get("/vehicle/state")["controls"].get("emulator_active"):
                break
            time.sleep(0.25)
        return self

    def __exit__(self, *exc):
        self._stop.set()


def set_gear(letter):
    result = post(f"/vehicle/gear?gear={GEARS[letter.upper()]}")
    if "error" in result:
        sys.exit(f"gear: {result['error']}")


def set_controls(speed, brake=0, accelerator=0, steering=0):
    # The console only accepts whole numbers (its own page sends Math.round values).
    result = post(
        f"/vehicle/controls?speed={round(speed)}&brake={round(brake)}"
        f"&accelerator={round(accelerator)}&steering={round(steering)}"
    )
    if "error" in result:
        sys.exit(f"controls: {result['error']}")


def set_seat(area, occupied):
    result = post(f"/vehicle/seat?kind=occupied&area={area}&value={'true' if occupied else 'false'}")
    if "error" in result:
        sys.exit(f"seat: {result['error']}")


def print_state():
    state = get("/vehicle/state")
    c = state["controls"]
    gear = (state["gear"].get("actual") or {}).get("value", "?")
    seated = [seat for seat, yes in state["seat"]["occupied"].items() if yes]
    print(f"gear={gear} speed={c['speed_kph']}km/h brake={c['brake_pct']}% accel={c['accelerator_pct']}% seated={','.join(seated) or '-'}")


def load_keyframes(path):
    """Rows: seconds_from_start,gear,speed_kph,brake_pct[,label]. Lines starting with # are comments."""
    with open(path, newline="", encoding="utf-8") as f:
        rows = [r for r in csv.reader(f) if r and r[0].strip() and r[0].strip()[0].isdigit()]
    return [(float(t), g.strip().upper(), float(v), float(b), (rest[0].strip() if rest else ""))
            for t, g, v, b, *rest in rows]


def interpolate(keyframes, step=0.25):
    """Fill in frames every `step` seconds between keyframes that share a gear,
    so the car accelerates smoothly instead of jumping. Filled-in frames have no label (None).
    RouteScenarioTest mirrors this, so keep the two in step."""
    frames = [keyframes[0]]
    for (t0, g0, v0, b0, _), (t1, g1, v1, b1, label1) in zip(keyframes, keyframes[1:]):
        if g0 == g1 and t1 - t0 > step:
            n = int((t1 - t0) / step)
            for i in range(1, n):
                f = i / n
                frames.append((t0 + (t1 - t0) * f, g0, v0 + (v1 - v0) * f, b0 + (b1 - b0) * f, None))
        frames.append((t1, g1, v1, b1, label1))
    return frames


def replay(path, smooth=True):
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("# expect:"):
                print(line.strip(), flush=True)
    keyframes = load_keyframes(path)
    frames = interpolate(keyframes) if smooth else keyframes
    start = time.monotonic()
    gear = None
    for t, g, speed, brake, label in frames:
        delay = t - (time.monotonic() - start)
        if delay > 0:
            time.sleep(delay)
        if g != gear:
            set_gear(g)
            gear = g
        set_controls(speed, brake=brake)
        if label is not None:
            print(f"t={t:6.1f}s {g} {speed:5.1f}km/h brake={brake:3.0f}%  {label}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("gear").add_argument("letter", choices=list("PRNDprnd"))
    sub.add_parser("drive").add_argument("speed", type=float)
    sub.add_parser("brake").add_argument("pct", type=float)
    sub.add_parser("park")
    seat_parser = sub.add_parser("seat")
    seat_parser.add_argument("area", choices=["driver", "passenger", "rear_left", "rear_right"])
    seat_parser.add_argument("state", choices=["on", "off"])
    sub.add_parser("state")
    replay_parser = sub.add_parser("replay")
    replay_parser.add_argument("csv")
    replay_parser.add_argument("--no-smooth", action="store_true", help="jump between keyframes")
    args = parser.parse_args()

    if args.cmd == "state":
        print_state()
        return

    with Heartbeat():
        if args.cmd == "gear":
            set_gear(args.letter)
        elif args.cmd == "drive":
            set_gear("D")
            set_controls(args.speed)
        elif args.cmd == "brake":
            speed = get("/vehicle/state")["controls"]["speed_kph"]
            set_controls(speed, brake=args.pct)
        elif args.cmd == "park":
            set_controls(0, brake=100)
            set_gear("P")
            set_controls(0, brake=0)
        elif args.cmd == "seat":
            set_seat(args.area, args.state == "on")
        elif args.cmd == "replay":
            replay(args.csv, smooth=not args.no_smooth)
        time.sleep(1.5)  # let the emulator pick up the last value before the session drops
    print_state()


if __name__ == "__main__":
    main()
