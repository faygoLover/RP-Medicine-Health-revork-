"""Галерея моделей предметов RP Medicine: отдаёт tools/model_gallery и хранит отметки и правки вида в docs/model_review.json.

Запуск: python scripts/gallery_server.py [порт]   (по умолчанию 8766), потом http://127.0.0.1:8766/
Данные моделей — python scripts/gallery_export.py (после каждого изменения моделей).
"""
import http.server
import json
import os
import sys
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SITE = os.path.join(ROOT, "tools", "model_gallery")
REVIEW = os.path.join(ROOT, "docs", "model_review.json")


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=SITE, **kw)

    def end_headers(self):
        self.send_header("Cache-Control", "no-cache")
        super().end_headers()

    def do_GET(self):
        if self.path == "/api/review":
            data = open(REVIEW, "rb").read() if os.path.exists(REVIEW) else b'{"items":{}}'
            self._send(200, data)
            return
        super().do_GET()

    def do_POST(self):
        if self.path != "/api/review":
            self._send(404, b"{}")
            return
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        try:
            data = json.loads(body)
        except ValueError:
            self._send(400, b'{"error":"bad json"}')
            return
        data["saved_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
        tmp = REVIEW + ".tmp"
        with open(tmp, "w", encoding="utf-8") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=1)
        os.replace(tmp, REVIEW)
        self._send(200, json.dumps({"saved_at": data["saved_at"]}).encode())

    def _send(self, code, data):
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        if self.path.startswith("/api") and self.command == "POST":
            return
        if args and str(args[1]) >= "400":
            super().log_message(fmt, *args)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8766
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"Галерея: http://127.0.0.1:{port}/", flush=True)
    srv.serve_forever()
