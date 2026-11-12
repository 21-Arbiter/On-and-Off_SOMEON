#!/usr/bin/env python3
"""HTTP proxy on 127.0.0.1 that sends connections out through one network interface
(SO_BINDTODEVICE, allowed without root since Linux 5.7), so the emulator can use the
phone's USB tethering while the PC's default route stays on the wired network.

Names are looked up with the tether's own DNS server (the office DNS may not know
some of them); private and loopback addresses connect directly, as without the proxy.

usage: tether_proxy.py <interface> [port] [dns server]
"""
import asyncio
import ipaddress
import os
import random
import socket
import struct
import sys

IFACE = sys.argv[1].encode()
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 3128
DNS = sys.argv[3] if len(sys.argv) > 3 else "172.20.10.1"
VERBOSE = os.environ.get("PROXY_VERBOSE") == "1"  # log every connection


def bound_socket(kind):
    s = socket.socket(socket.AF_INET, kind)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_BINDTODEVICE, IFACE)
    s.setblocking(False)
    return s


def dns_query(name):
    qid = random.randint(0, 0xFFFF)
    question = b"".join(bytes([len(p)]) + p.encode() for p in name.rstrip(".").split(".")) + b"\0"
    return qid, struct.pack(">HHHHHH", qid, 0x0100, 1, 0, 0, 0) + question + struct.pack(">HH", 1, 1)


def skip_name(msg, i):
    while msg[i]:
        if msg[i] & 0xC0 == 0xC0:
            return i + 2
        i += msg[i] + 1
    return i + 1


def dns_answers(msg, qid):
    rid, _, qd, an, _, _ = struct.unpack(">HHHHHH", msg[:12])
    if rid != qid:
        return []
    i = 12
    for _ in range(qd):
        i = skip_name(msg, i) + 4
    found = []
    for _ in range(an):
        i = skip_name(msg, i)
        rtype, _, _, length = struct.unpack(">HHIH", msg[i:i + 10])
        i += 10
        if rtype == 1 and length == 4:
            found.append(socket.inet_ntoa(msg[i:i + 4]))
        i += length
    return found


async def resolve(host):
    """IPv4 addresses for [host]: the tether's DNS first, then the PC's resolver."""
    try:
        ipaddress.ip_address(host)
        return [host]
    except ValueError:
        pass
    loop = asyncio.get_running_loop()
    s = bound_socket(socket.SOCK_DGRAM)
    try:
        qid, query = dns_query(host)
        await loop.sock_connect(s, (DNS, 53))
        await loop.sock_sendall(s, query)
        found = dns_answers(await asyncio.wait_for(loop.sock_recv(s, 4096), 3), qid)
        if found:
            return found
    except (OSError, asyncio.TimeoutError, struct.error, IndexError):
        pass
    finally:
        s.close()
    infos = await loop.getaddrinfo(host, None, family=socket.AF_INET, type=socket.SOCK_STREAM)
    return [i[4][0] for i in infos]


async def open_upstream(host, port):
    ip = (await resolve(host))[0]
    if ipaddress.ip_address(ip).is_private or ipaddress.ip_address(ip).is_loopback:
        return await asyncio.wait_for(asyncio.open_connection(ip, port), 5)
    s = bound_socket(socket.SOCK_STREAM)
    await asyncio.wait_for(asyncio.get_running_loop().sock_connect(s, (ip, port)), 15)
    return await asyncio.open_connection(sock=s)


async def pipe(r, w):
    try:
        while data := await r.read(65536):
            w.write(data)
            await w.drain()
    except Exception:
        pass
    finally:
        w.close()


async def handle(cr, cw):
    target = "?"
    try:
        head = await cr.readuntil(b"\r\n\r\n")
        method, target, _ = head.split(b"\r\n", 1)[0].decode().split(" ", 2)
        if VERBOSE:
            print(f"{method} {target}", flush=True)
        if method == "CONNECT":
            host, port = target.rsplit(":", 1)
            ur, uw = await open_upstream(host, int(port))
            cw.write(b"HTTP/1.1 200 Connection established\r\n\r\n")
            await cw.drain()
        else:  # plain http://host[:port]/path
            hostport, _, path = target.split("://", 1)[1].partition("/")
            host, _, port = hostport.partition(":")
            ur, uw = await open_upstream(host, int(port or 80))
            uw.write(head.replace(target.encode(), b"/" + path.encode(), 1))
        await asyncio.gather(pipe(cr, uw), pipe(ur, cw))
    except Exception as e:
        print(f"proxy error {target}: {e!r}", flush=True)
        cw.close()


async def main():
    server = await asyncio.start_server(handle, "127.0.0.1", PORT)
    print(f"proxy on 127.0.0.1:{PORT} via {IFACE.decode()}, DNS {DNS}", flush=True)
    async with server:
        await server.serve_forever()


asyncio.run(main())
