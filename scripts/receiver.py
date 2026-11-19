#!/usr/bin/env python3
"""
A tiny webhook receiver for trying the platform locally. Standard library only.

  WEBHOOK_SECRET=whsec_...  FAIL_FIRST=3  python3 scripts/receiver.py

It verifies the X-Webhook-Signature exactly as a real consumer should (HMAC-SHA256 over
"<timestamp>.<body>", constant-time compare, and a 5-minute timestamp window), and answers 500 to the
first FAIL_FIRST requests so you can watch retries with growing delays, the DEAD state, and replay.

To let the platform call it you must run the platform with ALLOW_PRIVATE_WEBHOOK_TARGETS=true
(development only) and register http://localhost:9000/ as an endpoint.
"""
import hashlib
import hmac
import os
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

SECRET = os.environ.get("WEBHOOK_SECRET", "")
FAIL_FIRST = int(os.environ.get("FAIL_FIRST", "0"))
TOLERANCE_SECONDS = 300
seen = 0


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        global seen
        seen += 1
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        ts = self.headers.get("X-Webhook-Timestamp", "")
        sig = self.headers.get("X-Webhook-Signature", "")

        if not SECRET:
            return self._reply(500, "WEBHOOK_SECRET is not set on the receiver")
        if not ts.isdigit() or abs(time.time() - int(ts)) > TOLERANCE_SECONDS:
            return self._reply(400, "timestamp missing or too old")
        expected = "v1=" + hmac.new(
            SECRET.encode(), ts.encode() + b"." + body, hashlib.sha256
        ).hexdigest()
        if not hmac.compare_digest(expected, sig):
            return self._reply(400, "bad signature")

        if seen <= FAIL_FIRST:
            print(f"#{seen} signature OK, answering 500 on purpose", flush=True)
            return self._reply(500, "simulated outage")
        print(f"#{seen} signature OK, delivered: {body.decode()[:200]}", flush=True)
        self._reply(200, "ok")

    def _reply(self, status, text):
        data = text.encode()
        self.send_response(status)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):  # keep the console readable
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9000
    print(f"receiver on http://localhost:{port}  (failing the first {FAIL_FIRST} requests)", flush=True)
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
