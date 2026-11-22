#!/opt/homebrew/bin/python3
"""Phone Browser — a local web UI for browsing, searching and copying files
between this Mac and an Android phone over USB (adb).

Runs only on 127.0.0.1. Every API call needs the per-run token embedded in the page.
"""
import hashlib, json, os, secrets, shlex, shutil, subprocess, sys, tempfile, threading, time, urllib.parse
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler

PORT = 8765
ADB = shutil.which("adb") or "/opt/homebrew/bin/adb"
ROOT = "/sdcard"
HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.expanduser("~/Library/Caches/phone-browser")
DEFAULT_DEST = os.path.expanduser("~/Downloads/FromPhone")
TOKEN = secrets.token_urlsafe(24)
SKIP = ("/sdcard/Android/data", "/sdcard/Android/obb")
IMAGE_EXT = {"jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp"}

os.makedirs(CACHE, exist_ok=True)

# The server shuts itself down once no open tab has checked in for IDLE_LIMIT seconds.
# Open tabs ping every few seconds (background tabs may be throttled to ~1/min, hence the margin);
# closing the tab sends a "bye" that shortens the wait to a few seconds (enough for a page reload).
IDLE_LIMIT = 150
BYE_GRACE = 4
_last_ping = time.time()


def _watchdog():
    while True:
        time.sleep(1)
        if time.time() - _last_ping > IDLE_LIMIT:
            os._exit(0)


threading.Thread(target=_watchdog, daemon=True).start()
_index = {"time": 0, "entries": [], "error": None}
_index_lock = threading.Lock()


def adb(*args, timeout=600, binary=False):
    r = subprocess.run([ADB, *args], capture_output=True, timeout=timeout)
    out = r.stdout if binary else r.stdout.decode("utf-8", "replace")
    return r.returncode, out, r.stderr.decode("utf-8", "replace")


def device_state():
    code, out, err = adb("get-state", timeout=10)
    s = (out + err).strip()
    if "device" == out.strip():
        return "ok", ""
    if "unauthorized" in s:
        return "unauthorized", "Unlock the phone and tap Allow on the USB debugging prompt."
    return "missing", "No phone found. Plug it in via USB and unlock it."


def device_name():
    _, out, _ = adb("shell", "getprop ro.product.model", timeout=10)
    return out.strip() or "Phone"


def build_index():
    prune = " -o ".join(f"-path {shlex.quote(p)}" for p in SKIP)
    cmd = (f"find {ROOT}/ \\( {prune} \\) -prune -o -mindepth 1 \\( -type f -o -type d \\) "
           f"-exec stat -c '%F|%s|%Y|%n' {{}} +")
    code, out, err = adb("shell", cmd, timeout=300)
    entries = []
    for line in out.replace("\r", "").split("\n"):
        parts = line.split("|", 3)
        if len(parts) != 4:
            continue
        kind, size, mtime, path = parts
        path = path.replace("//", "/")
        if path.rstrip("/") == ROOT:
            continue
        entries.append({
            "p": path, "d": kind == "directory",
            "s": int(size) if size.isdigit() else 0,
            "m": int(mtime) if mtime.isdigit() else 0,
        })
    with _index_lock:
        _index.update(time=time.time(), entries=entries,
                      error=None if entries else (err.strip() or "Could not list the phone's files."))


def stat_lines(out):
    entries = []
    for line in out.replace("\r", "").split("\n"):
        parts = line.split("|", 3)
        if len(parts) != 4:
            continue
        kind, size, mtime, path = parts
        entries.append({"p": path.replace("//", "/"), "d": kind == "directory",
                        "s": int(size) if size.isdigit() else 0,
                        "m": int(mtime) if mtime.isdigit() else 0})
    return entries


def index_add(remote):
    """Merge a newly pushed phone path (file or folder) into the index."""
    _, out, _ = adb("shell", f"find {shlex.quote(remote)} \\( -type f -o -type d \\) "
                    f"-exec stat -c '%F|%s|%Y|%n' {{}} +", timeout=120)
    new = stat_lines(out)
    paths = {e["p"] for e in new}
    with _index_lock:
        _index["entries"] = [e for e in _index["entries"] if e["p"] not in paths] + new


def phone_exists(p):
    code, out, _ = adb("shell", f"[ -e {shlex.quote(p)} ] && echo y", timeout=20)
    return out.strip() == "y"


def unique_phone(folder, name):
    base, ext = os.path.splitext(name)
    cand, n = f"{folder}/{name}", 1
    while phone_exists(cand):
        cand = f"{folder}/{base} ({n}){ext}"
        n += 1
    return cand


def mac_entry(path):
    try:
        st = os.stat(path)
    except OSError:
        return None
    import stat as S
    return {"p": path, "d": S.S_ISDIR(st.st_mode), "s": st.st_size, "m": int(st.st_mtime)}


def mac_list(folder):
    out = []
    with os.scandir(folder) as it:
        for de in it:
            e = mac_entry(de.path)
            if e:
                out.append(e)
    return out


def mac_search(folder, q):
    terms = [t for t in q.split() if t]
    if not terms:
        return []
    clauses = " && ".join("kMDItemFSName == \"*%s*\"c" % t.replace('"', "").replace("\\", "") for t in terms)
    r = subprocess.run(["mdfind", "-onlyin", folder, clauses], capture_output=True, text=True, timeout=30)
    out = []
    for line in r.stdout.splitlines()[:3000]:
        e = mac_entry(line)
        if e:
            out.append(e)
    return out


def mac_thumb(path):
    st = os.stat(path)
    key = hashlib.sha1(f"mac|{path}|{st.st_mtime}".encode()).hexdigest()
    out = os.path.join(CACHE, key + ".jpg")
    if not os.path.exists(out):
        subprocess.run(["sips", "-s", "format", "jpeg", "-Z", "320", path, "--out", out], capture_output=True)
    return out if os.path.exists(out) else None


def safe_phone_path(p):
    p = os.path.normpath("/" + p.lstrip("/"))
    if not (p == ROOT or p.startswith(ROOT + "/")):
        raise ValueError("path outside phone storage")
    return p


def thumb(path, mtime):
    key = hashlib.sha1(f"{path}|{mtime}".encode()).hexdigest()
    out = os.path.join(CACHE, key + ".jpg")
    if os.path.exists(out):
        return out
    with tempfile.TemporaryDirectory() as td:
        local = os.path.join(td, "src" + os.path.splitext(path)[1])
        code, _, _ = adb("pull", path, local, timeout=120)
        if code != 0:
            return None
        r = subprocess.run(["sips", "-s", "format", "jpeg", "-Z", "320", local, "--out", out],
                           capture_output=True)
        return out if r.returncode == 0 and os.path.exists(out) else None


def unique_dest(folder, name):
    base, ext = os.path.splitext(name)
    cand, n = os.path.join(folder, name), 1
    while os.path.exists(cand):
        cand = os.path.join(folder, f"{base} ({n}){ext}")
        n += 1
    return cand


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _ok_origin(self):
        host = self.headers.get("Host", "")
        return host in (f"127.0.0.1:{PORT}", f"localhost:{PORT}")

    def _json(self, obj, code=200):
        b = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(b)

    def _authed(self, q):
        return self._ok_origin() and (q.get("t", [""])[0] == TOKEN or self.headers.get("X-Token") == TOKEN)

    def do_GET(self):
        if not self._ok_origin():
            return self.send_error(403)
        u = urllib.parse.urlparse(self.path)
        q = urllib.parse.parse_qs(u.query)
        if u.path in ("/", "/index.html"):
            with open(os.path.join(HERE, "index.html"), "rb") as f:
                b = f.read().replace(b"__TOKEN__", TOKEN.encode())
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(b)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            return self.wfile.write(b)
        if not self._authed(q):
            return self.send_error(403)
        try:
            if u.path == "/api/status":
                st, msg = device_state()
                return self._json({"state": st, "message": msg,
                                   "name": device_name() if st == "ok" else "",
                                   "dest": DEFAULT_DEST, "home": os.path.expanduser("~")})
            if u.path == "/api/index":
                if q.get("refresh") or not _index["entries"]:
                    st, msg = device_state()
                    if st != "ok":
                        return self._json({"error": msg, "entries": []})
                    build_index()
                with _index_lock:
                    return self._json({"time": _index["time"], "entries": _index["entries"],
                                       "error": _index["error"]})
            if u.path == "/api/mac/list":
                folder = os.path.expanduser(q.get("dir", ["~"])[0])
                try:
                    return self._json({"dir": folder, "entries": mac_list(folder)})
                except OSError as e:
                    return self._json({"dir": folder, "entries": [], "error": e.strerror or str(e)})
            if u.path == "/api/mac/search":
                folder = os.path.expanduser(q.get("dir", ["~"])[0])
                return self._json({"entries": mac_search(folder, q.get("q", [""])[0])})
            if u.path == "/api/mac/thumb":
                f = mac_thumb(q["p"][0])
                if not f:
                    return self.send_error(404)
                b = open(f, "rb").read()
                self.send_response(200)
                self.send_header("Content-Type", "image/jpeg")
                self.send_header("Content-Length", str(len(b)))
                self.send_header("Cache-Control", "max-age=86400")
                self.end_headers()
                return self.wfile.write(b)
            if u.path in ("/api/thumb", "/api/file"):
                path = safe_phone_path(q["p"][0])
                if u.path == "/api/thumb":
                    f = thumb(path, q.get("m", ["0"])[0])
                    if not f:
                        return self.send_error(404)
                    ctype = "image/jpeg"
                else:
                    td = tempfile.mkdtemp(dir=CACHE)
                    f = os.path.join(td, os.path.basename(path))
                    if adb("pull", path, f)[0] != 0:
                        return self.send_error(404)
                    import mimetypes
                    ctype = mimetypes.guess_type(f)[0] or "application/octet-stream"
                size = os.path.getsize(f)
                self.send_response(200)
                self.send_header("Content-Type", ctype)
                self.send_header("Content-Length", str(size))
                self.send_header("Cache-Control", "max-age=86400" if u.path == "/api/thumb" else "no-store")
                self.end_headers()
                with open(f, "rb") as fh:
                    shutil.copyfileobj(fh, self.wfile)
                if u.path == "/api/file":
                    shutil.rmtree(os.path.dirname(f), ignore_errors=True)
                return
        except (ValueError, KeyError) as e:
            return self._json({"error": str(e)}, 400)
        self.send_error(404)

    def do_POST(self):
        u = urllib.parse.urlparse(self.path)
        q = urllib.parse.parse_qs(u.query)
        if not self._authed(q):
            return self.send_error(403)
        length = int(self.headers.get("Content-Length", "0"))
        global _last_ping
        if u.path == "/api/ping":
            _last_ping = time.time()
            return self._json({"ok": True})
        if u.path == "/api/quit":
            self._json({"ok": True})
            self.wfile.flush()
            threading.Timer(0.3, lambda: os._exit(0)).start()
            return
        if u.path == "/api/bye":
            _last_ping = min(_last_ping, time.time() - IDLE_LIMIT + BYE_GRACE)
            return self._json({"ok": True})
        _last_ping = time.time()
        try:
            if u.path == "/api/pull":
                body = json.loads(self.rfile.read(length) or b"{}")
                dest = os.path.expanduser(body.get("dest") or DEFAULT_DEST)
                os.makedirs(dest, exist_ok=True)
                done, failed = [], []
                for p in body.get("paths", []):
                    p = safe_phone_path(p)
                    target = unique_dest(dest, os.path.basename(p))
                    code, _, err = adb("pull", "-a", p, target)
                    (done if code == 0 else failed).append(p if code == 0 else f"{p}: {err.strip()}")
                return self._json({"done": done, "failed": failed, "dest": dest})
            if u.path == "/api/push":
                folder = safe_phone_path(q["dir"][0])
                name = os.path.basename(q["name"][0]).strip()
                if not name or name in (".", ".."):
                    raise ValueError("bad file name")
                with tempfile.TemporaryDirectory(dir=CACHE) as td:
                    local = os.path.join(td, name)
                    with open(local, "wb") as fh:
                        remaining = length
                        while remaining > 0:
                            chunk = self.rfile.read(min(1 << 20, remaining))
                            if not chunk:
                                break
                            fh.write(chunk)
                            remaining -= len(chunk)
                    remote = f"{folder}/{name}"
                    code, _, err = adb("push", local, remote)
                if code != 0:
                    return self._json({"error": err.strip()}, 500)
                adb("shell", "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "
                    + shlex.quote("file://" + remote), timeout=20)
                _, out, _ = adb("shell", "stat -c '%s|%Y' " + shlex.quote(remote), timeout=20)
                s, _, m = out.strip().partition("|")
                entry = {"p": remote, "d": False, "s": int(s or 0), "m": int(m or time.time())}
                with _index_lock:
                    _index["entries"] = [e for e in _index["entries"] if e["p"] != remote] + [entry]
                return self._json({"ok": True, "entry": entry})
            if u.path == "/api/push-local":
                body = json.loads(self.rfile.read(length) or b"{}")
                folder = safe_phone_path(body["dir"])
                done, failed = [], []
                for local in body.get("paths", []):
                    if not os.path.exists(local):
                        failed.append(f"{local}: not found"); continue
                    remote = unique_phone(folder, os.path.basename(local.rstrip("/")))
                    code, _, err = adb("push", local, remote, timeout=3600)
                    if code == 0:
                        done.append(remote)
                        index_add(remote)
                        adb("shell", "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "
                            + shlex.quote("file://" + remote), timeout=20)
                    else:
                        failed.append(f"{local}: {err.strip()}")
                return self._json({"done": done, "failed": failed})
            if u.path == "/api/mac/open":
                body = json.loads(self.rfile.read(length) or b"{}")
                subprocess.run(["open", body["path"]])
                return self._json({"ok": True})
            if u.path == "/api/reveal":
                body = json.loads(self.rfile.read(length) or b"{}")
                target = os.path.expanduser(body.get("path") or DEFAULT_DEST)
                os.makedirs(DEFAULT_DEST, exist_ok=True)
                subprocess.run(["open", "-R", target] if os.path.isfile(target) else ["open", target])
                return self._json({"ok": True})
            if u.path == "/api/choose-folder":
                r = subprocess.run(["osascript", "-e",
                    'POSIX path of (choose folder with prompt "Copy phone files to:" default location (path to downloads folder))'],
                    capture_output=True, text=True)
                return self._json({"path": r.stdout.strip() or None})
        except (ValueError, KeyError) as e:
            return self._json({"error": str(e)}, 400)
        self.send_error(404)


if __name__ == "__main__":
    srv = ThreadingHTTPServer(("127.0.0.1", PORT), H)
    print(f"Phone Browser on http://127.0.0.1:{PORT}", flush=True)
    srv.serve_forever()
