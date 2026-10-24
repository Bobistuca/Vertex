"""Minimal Source RCON client (standard library only) used by the smoke tests."""
import socket
import struct


class Rcon:
    def __init__(self, host, port, password, timeout=15):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        self._id = 0
        self._send(3, password)
        if self._recv()[0] == -1:
            raise RuntimeError("RCON authentication failed")

    def _send(self, kind, payload):
        self._id += 1
        data = struct.pack("<ii", self._id, kind) + payload.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)

    def _recv_exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise RuntimeError("RCON connection closed")
            buf += chunk
        return buf

    def _recv(self):
        (length,) = struct.unpack("<i", self._recv_exact(4))
        body = self._recv_exact(length)
        req_id, kind = struct.unpack("<ii", body[:8])
        return req_id, body[8:-2].decode("utf-8", "replace")

    def command(self, text):
        self._send(2, text)
        return self._recv()[1]

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass
