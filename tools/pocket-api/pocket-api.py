#!/usr/bin/env python3
"""
pocket-api — Companion Remote Sandbox Daemon for Androgravity
Runs inside Termux on Android, or on your desktop PC / Cloud VM (Linux/macOS/Windows).
Allows Androgravity to execute commands, run build tools (python3, node, git, npm, pkg),
and bypass mobile sandbox restrictions seamlessly.

Usage in Termux:
    python3 pocket-api.py
    (or: python3 pocket-api.py --port 8787)
"""

import utils.net
import utils.clients
import utils.http_session
import utils.transport
import utils.session
import argparse
import http.server
import json
import os
import platform
import socketserver
import subprocess
import sys
import time

START_TIME = time.time()
AUTH_TOKEN = ""
WORKSPACE_ROOT = os.getcwd()

# Ensure Termux bin is always in PATH if running inside Termux
TERMUX_BIN = "/data/data/com.termux/files/usr/bin"
if os.path.exists(TERMUX_BIN) and TERMUX_BIN not in os.environ.get("PATH", ""):
    os.environ["PATH"] = f"{TERMUX_BIN}:{os.environ.get('PATH', '')}"
    os.environ.setdefault("PREFIX", "/data/data/com.termux/files/usr")
    os.environ.setdefault("HOME", os.path.expanduser("~"))

class ReusableThreadingServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

class PocketApiHandler(http.server.BaseHTTPRequestHandler):

    def _check_auth(self) -> bool:
        token = AUTH_TOKEN.strip()
        if not token:
            # If no token configured on daemon, allow all localhost requests
            return True
        auth_header = self.headers.get("Authorization", "").strip()
        expected = f"Bearer {token}"
        return auth_header == expected or auth_header == token

    def _send_json(self, status_code: int, data: dict):
        body = json.dumps(data).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
        self.end_headers()

    def do_GET(self):
        if not self._check_auth():
            self._send_json(401, {"error": "Unauthorized. Invalid or missing Bearer token in Androgravity Settings."})
            return

        if self.path in ("/api/health", "/api/ping"):
            self._send_json(200, {
                "status": "ok",
                "os": f"{platform.system()} {platform.release()}",
                "version": "1.1.0",
                "uptime": int(time.time() - START_TIME),
                "workspace": WORKSPACE_ROOT
            })
        else:
            self._send_json(404, {"error": "Endpoint not found"})

    def do_POST(self):
        if not self._check_auth():
            self._send_json(401, {"error": "Unauthorized. Invalid or missing Bearer token in Androgravity Settings."})
            return

        content_length = int(self.headers.get("Content-Length", 0))
        post_body = self.rfile.read(content_length)

        try:
            req_json = json.loads(post_body.decode("utf-8")) if post_body else {}
        except Exception as e:
            self._send_json(400, {"error": f"Invalid JSON payload: {str(e)}"})
            return

        if self.path == "/api/exec":
            cmd = req_json.get("cmd", [])
            env_override = req_json.get("env", {})
            raw_cwd = req_json.get("cwd", "")

            if not cmd:
                self._send_json(400, {"error": "Missing 'cmd' parameter"})
                return

            # Determine safe execution working directory
            cwd = WORKSPACE_ROOT
            if raw_cwd:
                try:
                    # Check if requested cwd is accessible directly (e.g. /sdcard or Termux path)
                    if os.path.isdir(raw_cwd) and os.access(raw_cwd, os.R_OK | os.W_OK):
                        cwd = raw_cwd
                    else:
                        # Path is not accessible (e.g. Android private app dir /data/user/0/dev.pocketagent/...)
                        # Map project subfolder name into Termux WORKSPACE_ROOT/<projectName>
                        proj_name = os.path.basename(raw_cwd.rstrip("/\\"))
                        if proj_name and proj_name not in (".", ".."):
                            mapped_dir = os.path.join(WORKSPACE_ROOT, proj_name)
                            os.makedirs(mapped_dir, exist_ok=True)
                            cwd = mapped_dir
                except Exception:
                    cwd = WORKSPACE_ROOT

            env = os.environ.copy()
            if os.path.exists(TERMUX_BIN) and TERMUX_BIN not in env.get("PATH", ""):
                env["PATH"] = f"{TERMUX_BIN}:{env.get('PATH', '')}"
            env.update(env_override)

            try:
                cmd_str = " ".join(cmd) if isinstance(cmd, list) else str(cmd)
                print(f"⚡ [EXEC] {cmd_str[:80]} in {cwd}")

                result = subprocess.run(
                    cmd,
                    cwd=cwd,
                    env=env,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    timeout=300
                )
                self._send_json(200, {
                    "exitCode": result.returncode,
                    "stdout": result.stdout,
                    "stderr": result.stderr
                })
            except subprocess.TimeoutExpired:
                self._send_json(200, {
                    "exitCode": 124,
                    "stdout": "",
                    "stderr": "Command execution timed out after 300 seconds."
                })
            except Exception as e:
                self._send_json(200, {
                    "exitCode": -1,
                    "stdout": "",
                    "stderr": f"Failed to spawn process: {str(e)}"
                })
        else:
            self._send_json(404, {"error": "Unknown POST endpoint"})

    def log_message(self, format, *args):
        # Concise logging to keep Termux console clean
        pass

def run_server(port: int, token: str, workspace: str):
    global AUTH_TOKEN, WORKSPACE_ROOT
    AUTH_TOKEN = token
    WORKSPACE_ROOT = os.path.abspath(workspace)
    os.makedirs(WORKSPACE_ROOT, exist_ok=True)

    server_address = ("", port)
    with ReusableThreadingServer(server_address, PocketApiHandler) as httpd:
        token_status = f"Bearer {token}" if token else "[Open / No Token Required]"
        print("=" * 60)
        print("  🚀 Androgravity Termux Bridge Daemon Running")
        print("=" * 60)
        print(f"  📡 Port:      {port}")
        print(f"  🔗 URL:       http://127.0.0.1:{port}")
        print(f"  📂 Workspace: {WORKSPACE_ROOT}")
        print(f"  🔑 Token:     {token_status}")
        print("-" * 60)
        print("  ✅ Ready for Androgravity! Leave Termux running.")
        print("  💡 Tip: Run 'termux-wake-lock' so Android does not kill Termux.")
        print("=" * 60)
        sys.stdout.flush()
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nShutting down Androgravity Bridge daemon...")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Androgravity Termux Bridge Daemon")
    parser.add_argument("--port", type=int, default=8787, help="Port to listen on (default: 8787)")
    parser.add_argument("--token", type=str, default="", help="Optional Bearer authentication token")
    parser.add_argument("--workspace", type=str, default=".", help="Root directory for workspaces")
    args = parser.parse_args()

    run_server(args.port, args.token, args.workspace)
