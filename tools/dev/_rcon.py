import socket, struct, sys

def rcon(port, pw, cmds):
    s = socket.create_connection(('127.0.0.1', port), timeout=120)
    def send(i, t, body):
        b = struct.pack('<ii', i, t) + body.encode('utf-8') + b'\x00\x00'
        s.sendall(struct.pack('<i', len(b)) + b)
    def recv():
        n = struct.unpack('<i', s.recv(4))[0]
        data = b''
        while len(data) < n:
            data += s.recv(n - len(data))
        return data[8:-2].decode('utf-8', 'replace')
    send(1, 3, pw)
    recv()
    out = []
    for c in cmds:
        send(2, 2, c)
        out.append(c + ' -> ' + recv())
    s.close()
    return out

if __name__ == '__main__':
    for line in rcon(int(sys.argv[1]), sys.argv[2], sys.argv[3:]):
        print(line)
