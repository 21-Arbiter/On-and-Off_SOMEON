#!/usr/bin/env python3
"""Takes the emulator's dropouts out of a recorded voice. Its audio falls behind every ~31 ms and
fills in about 1 ms of silence (the speech then goes on where it stopped), which sounds like crackle.
Each dropout is a near-silent run inside loud speech with the resampler's ramps on both sides; the
silence it put in is cut out and the few samples about the join are filled by linear prediction
from both sides.
usage: declick.py <rate> <in.raw> <out.raw>  (16-bit mono PCM)"""
import math
import struct
import sys


def _ncc(x, i, j, n):
    a, b = x[i:i + n], x[j:j + n]
    ma, mb = sum(a) / n, sum(b) / n
    num = sum((p - ma) * (q - mb) for p, q in zip(a, b))
    return num / (math.sqrt(sum((p - ma) ** 2 for p in a) * sum((q - mb) ** 2 for q in b)) + 1e-9)


def _peak(f, lo, hi):
    vals = {k: f(k) for k in range(lo, hi)}
    k = max(vals, key=vals.get)
    if lo < k < hi - 1:
        a, b, c = vals[k - 1], vals[k], vals[k + 1]
        if a - 2 * b + c:
            return k + (a - c) / (2 * (a - 2 * b + c)), b
    return k, vals[k]


def _rms(v):
    return math.sqrt(sum(s * s for s in v) / len(v)) if v else 0.0


def dropouts(x, rate, lo=50.0):
    """(start, end) of each near-silent run (|x| <= 2) inside speech louder than [lo] on both sides."""
    s = rate / 44100
    shortest, longest, w = max(4, round(8 * s)), rate // 50, rate // 100
    found, n = [], len(x)
    # resampled down (16 kHz) the silence rings a little: near-silent is then within 1.2 % of the speech about it
    for t in (2, 5, 10, 20, 40):
        i = 0
        while i < n:
            if -t <= x[i] <= t:
                j = i
                while j < n and -t <= x[j] <= t:
                    j += 1
                if shortest <= j - i <= longest and i > w and j + w < n:
                    side = min(_rms(x[i - w:i]), _rms(x[j:j + w]))
                    if side > lo and (t <= 2 or t <= 0.012 * side):
                        found.append((i, j))
                i = j
            else:
                i += 1
    runs = []
    for g, e in sorted(found):
        if runs and g <= runs[-1][1]:
            runs[-1] = (runs[-1][0], max(e, runs[-1][1]))
        else:
            runs.append((g, e))
    return runs


def _extra(x, rate, g, e, typical=None):
    """The silence put in beyond the visible run, from the voice's period on both sides (None: not voiced)."""
    s = rate / 44100
    m, n = round(14 * s), round(260 * s)
    a, b = g - m, e + m
    if a - n - round(rate / 90) < 0 or b + n + round(rate / 90) > len(x):
        return None
    lo, hi = round(rate / 350), round(rate / 90)
    pl, cl = _peak(lambda k: _ncc(x, a - n, a - n - k, n), lo, hi)
    if cl < 0.85:
        return None
    pr, cr = _peak(lambda k: _ncc(x, b, b + k, n), lo, hi)
    if cr < 0.85 or abs(pl - pr) > 0.08 * pl:
        return None
    p, nd = (pl + pr) / 2, round(120 * s)
    if b - (e - g + 2 * m + nd + round(p) + round(60 * s)) < 0:
        return None
    d, cd = _peak(lambda d: _ncc(x, b, b - d, nd), e - g + 2 * m + nd, e - g + 2 * m + nd + round(p) + round(60 * s))
    if cd < 0.85:
        return None
    guess = typical if typical is not None else 20 * s
    k = max(1, round((d - (e - g) - guess) / p))
    return d - k * p - (e - g)


def _lpc(v, order):
    """Prediction coefficients (Levinson-Durbin on the Hann-windowed autocorrelation)."""
    n = len(v)
    w = [v[i] * (0.5 - 0.5 * math.cos(2 * math.pi * i / (n - 1))) for i in range(n)]
    r = [sum(w[i] * w[i + k] for i in range(n - k)) for k in range(order + 1)]
    if r[0] <= 0:
        return [0.0] * order
    r[0] *= 1.0001
    a, err = [0.0] * order, r[0]
    for i in range(order):
        k = (r[i + 1] - sum(a[j] * r[i - j] for j in range(i))) / err
        a = [a[j] - k * a[i - 1 - j] for j in range(i)] + [k] + a[i + 1:]
        err *= 1 - k * k
        if err <= 0:
            break
    return a


def _predict(history, a, count):
    h = list(history)
    for _ in range(count):
        h.append(sum(c * h[-1 - j] for j, c in enumerate(a)))
    return h[len(history):]


def repair(x, rate):
    """[x] without its dropouts, and how many were taken out."""
    s = rate / 44100
    runs = dropouts(x, rate)
    if not runs:
        return list(x), 0
    # how much silence a dropout puts in beyond its run: the voiced ones say, the rest go by them
    seen = sorted(v for v in (_extra(x, rate, g, e) for g, e in runs[::max(1, len(runs) // 40)]) if v is not None)
    typical = seen[len(seen) // 2] if len(seen) >= 5 else (20.5 if rate >= 44100 else 11.0) * s
    m = math.ceil(typical / 2) + max(3, round(6 * s))
    order, context = max(10, round(24 * s)), round(1024 * s)
    # dropouts closer together than their margins are one
    merged = []
    for g, e in runs:
        if merged and g - m <= merged[-1][1] + m + max(3, round(4 * s)):
            merged[-1] = (merged[-1][0], e)
        else:
            merged.append((g, e))
    y, done = [], 0
    for n, (g, e) in enumerate(merged):
        a, b = max(done, g - m), e + m
        extra = _extra(x, rate, g, e, typical)
        if extra is None or abs(extra - typical) > 6 * s:
            extra = typical
        fill = max(2, round((b - a) - (e - g) - extra))
        y.extend(x[done:a])
        left = y[-context:]
        stop = min(len(x), b + context, merged[n + 1][0] - m if n + 1 < len(merged) else len(x))
        right = x[b:stop]
        if len(left) > 2 * order and len(right) > 2 * order:
            fwd = _predict(left, _lpc(left, order), fill)
            bwd = _predict(right[::-1], _lpc(right[::-1], order), fill)[::-1]
            for t in range(fill):
                wt = 0.5 * (1 + math.cos(math.pi * (t + 1) / (fill + 1)))
                y.append(wt * fwd[t] + (1 - wt) * bwd[t])
        else:
            lv, rv = (left[-1] if left else 0), (right[0] if right else 0)
            y.extend(lv + (rv - lv) * (t + 1) / (fill + 1) for t in range(fill))
        done = b
    y.extend(x[done:])
    return [max(-32767, min(32767, int(round(v)))) for v in y], len(merged)



def patch(x, rate, lo=3.0):
    """[x] with each dropout filled in where it is: the length stays, for a recording that must keep its
    timing (a film's sound). The speech about each one is a little late or early for 1 ms. Down to
    quiet sounds (a chime dying away): filling one that wasn't there changes next to nothing."""
    s = rate / 44100
    order, context, m = max(10, round(24 * s)), round(1024 * s), max(3, round(12 * s))
    y, count = list(x), 0
    for g, e in dropouts(x, rate, lo):
        a, b = g - m, e + m
        left, right = y[max(0, a - context):a], y[b:b + context]
        if len(left) <= 2 * order or len(right) <= 2 * order:
            continue
        fwd = _predict(left, _lpc(left, order), b - a)
        bwd = _predict(right[::-1], _lpc(right[::-1], order), b - a)[::-1]
        for t in range(b - a):
            wt = 0.5 * (1 + math.cos(math.pi * (t + 1) / (b - a + 1)))
            y[a + t] = wt * fwd[t] + (1 - wt) * bwd[t]
        count += 1
    return y, count

if __name__ == "__main__":
    rate = int(sys.argv[1])
    d = open(sys.argv[2], "rb").read()
    x = list(struct.unpack("<%dh" % (len(d) // 2), d))
    y, count = repair(x, rate)
    open(sys.argv[3], "wb").write(struct.pack("<%dh" % len(y), *y))
    print(f"{sys.argv[2]}: {count} dropouts out, {len(x) / rate:.2f}s -> {len(y) / rate:.2f}s, left: {len(dropouts(y, rate))}")
