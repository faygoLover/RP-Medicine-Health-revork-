"""Галерея моделей предметов RP Medicine: отдаёт tools/model_gallery и хранит отметки и правки вида в docs/model_review.json.

Запуск: python scripts/gallery_server.py [порт]   (по умолчанию 8766), потом http://127.0.0.1:8766/
Данные моделей — python scripts/gallery_export.py (после каждого изменения моделей).

Доступ по ссылке (домашний сервер, scripts/gallery_deploy.sh): переменная GALLERY_KEY — без неё никого не пускает;
ссылка вида https://<адрес>/?k=<ключ> ставит куку на год. GALLERY_REVIEW — свой путь к файлу отметок.
Сохранение — заплатками по предметам ({"patch": {id: запись или null}}), чтобы правки двух людей не затирали друг друга.
"""
import hmac
import http.cookies
import http.server
import json
import os
import sys
import threading
import time
import urllib.parse

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SITE = os.path.join(ROOT, "tools", "model_gallery")
REVIEW = os.environ.get("GALLERY_REVIEW") or os.path.join(ROOT, "docs", "model_review.json")
KEY = os.environ.get("GALLERY_KEY", "")
LOCK = threading.Lock()


def read_review():
    if not os.path.exists(REVIEW):
        return {"items": {}}
    with open(REVIEW, encoding="utf-8") as fh:
        data = json.load(fh)
    data.setdefault("items", {})
    return data


def write_review(data):
    data["saved_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
    tmp = REVIEW + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=1)
    os.replace(tmp, REVIEW)


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=SITE, **kw)

    def end_headers(self):
        self.send_header("Cache-Control", "no-cache")
        super().end_headers()

    def _allowed(self):
        """Ключ из куки. Без GALLERY_KEY (локальный запуск) — пускаем всех."""
        if not KEY:
            return True
        c = http.cookies.SimpleCookie(self.headers.get("Cookie", ""))
        return "rpmkey" in c and hmac.compare_digest(c["rpmkey"].value, KEY)

    def _key_from_link(self):
        """?k=<ключ> в ссылке: ставим куку и уводим на адрес без ключа."""
        url = urllib.parse.urlsplit(self.path)
        k = urllib.parse.parse_qs(url.query).get("k", [""])[0]
        if not KEY or not k or not hmac.compare_digest(k, KEY):
            return False
        self.send_response(302)
        self.send_header("Set-Cookie", f"rpmkey={KEY}; Path=/; Max-Age=31536000; HttpOnly; SameSite=Lax")
        self.send_header("Location", url.path or "/")
        self.send_header("Content-Length", "0")
        self.end_headers()
        return True

    def _denied(self):
        msg = "Нужна ссылка с ключом — попросите её у автора.".encode()
        self.send_response(403)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(msg)))
        self.end_headers()
        self.wfile.write(msg)

    def do_GET(self):
        if self._key_from_link():
            return
        if not self._allowed():
            self._denied()
            return
        if self.path == "/api/review":
            with LOCK:
                data = read_review()
            self._send(200, json.dumps(data, ensure_ascii=False).encode())
            return
        super().do_GET()

    def do_POST(self):
        if not self._allowed():
            self._denied()
            return
        if self.path != "/api/review":
            self._send(404, b"{}")
            return
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        try:
            data = json.loads(body)
        except ValueError:
            self._send(400, b'{"error":"bad json"}')
            return
        with LOCK:
            if isinstance(data.get("patch"), dict):
                cur = read_review()
                for item, rec in data["patch"].items():
                    if rec:
                        cur["items"][item] = rec
                    else:
                        cur["items"].pop(item, None)
                data = cur
            write_review(data)
        self._send(200, json.dumps({"saved_at": data["saved_at"], "items": data["items"]}, ensure_ascii=False).encode())

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
    print(f"Галерея: http://127.0.0.1:{port}/" + (" (по ключу)" if KEY else ""), flush=True)
    srv.serve_forever()
