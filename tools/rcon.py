#!/usr/bin/env python
"""最小の rcon クライアント (マルチの確かめ用)。使い方: python tools/rcon.py [--port 25766] [--password test] "<command>" ..."""
import socket, struct, sys

def pkt(i, t, body):
    b = body.encode('utf-8')
    return struct.pack('<iii', 10 + len(b), i, t) + b + b'\x00\x00'

def read(s):
    n = struct.unpack('<i', s.recv(4))[0]
    d = b''
    while len(d) < n:
        d += s.recv(n - len(d))
    return struct.unpack('<ii', d[:8]), d[8:-2].decode('utf-8', 'replace')

def main():
    args = sys.argv[1:]
    port, pw = 25766, 'test'
    while args and args[0].startswith('--'):
        k = args.pop(0)
        v = args.pop(0)
        if k == '--port': port = int(v)
        if k == '--password': pw = v
    s = socket.create_connection(('127.0.0.1', port), timeout=10)
    s.sendall(pkt(1, 3, pw))
    (rid, _), _ = read(s)
    if rid == -1:
        sys.exit('rcon: bad password')
    for c in args:
        s.sendall(pkt(2, 2, c))
        print(c, '->', read(s)[1])

main()
