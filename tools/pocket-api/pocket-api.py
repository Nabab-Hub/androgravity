#!/usr/bin/env python3
"""
pocket-api — Companion Remote Sandbox Daemon for PocketAgent
Runs on your desktop PC, laptop, or Cloud VM (Linux/macOS/Windows).
Allows PocketAgent on Android to execute heavy builds, run full dev servers,
and bypass mobile thermal/memory restrictions seamlessly.

Usage:
    python pocket-api.py --port 8787 --token mysecret --workspace ~/projects
"""

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

class PocketApiHandler(http.server.BaseHTTPRequestHandler):

    def _check_auth(self) -> bool:
        if not AUTH_TOKEN:
            return True
        auth_header = self.headers.get("Authorization", "")
        expected = f"Bearer {AUTH_TOKEN}"
        return auth_header == expected

    def _send_json(self, status_code: int, data: dict):
        body = json.dumps(data).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
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
            self._send_json(401, {"error": "Unauthorized. Invalid Bearer token."})
            return

        if self.path == "/api/health" or self.path == "/api/ping":
            self._send_json(200, {
                "status": "ok",
                "os": f"{platform.system()} {platform.release()}",
                "version": "1.0.0",
                "uptime": int(time.time() - START_TIME),
                "workspace": WORKSPACE_ROOT
            })
        else:
            self._send_json(404, {"error": "Not found"})

    def do_POST(self):
        if not self._check_auth():
            self._send_json(401, {"error": "Unauthorized. Invalid Bearer token."})
            return

        content_length = int(self.headers.get("Content-Length", 0))
        post_body = self.rfile.read(content_length)

        try:
            req_json = json.loads(post_body.decode("utf-8")) if post_body else {}
        except Exception as e:
            self._send_json(400, {"error": f"Invalid JSON: {str(e)}"})
            return

        if self.path == "/api/exec":
            cmd = req_json.get("cmd", [])
            env_override = req_json.get("env", {})
            cwd = req_json.get("cwd", "") or WORKSPACE_ROOT

            if not cmd:
                self._send_json(400, {"error": "Missing 'cmd' parameter"})
                return

            if not os.path.exists(cwd):
                try:
                    os.makedirs(cwd, exist_ok=True)
                except Exception:
                    pass

            env = os.environ.copy()
            env.update(env_override)

            try:
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

def run_server(port: int, token: str, workspace: str):
    global AUTH_TOKEN, WORKSPACE_ROOT
    AUTH_TOKEN = token
    WORKSPACE_ROOT = os.path.abspath(workspace)
    os.makedirs(WORKSPACE_ROOT, exist_ok=True)

    server_address = ("", port)
    with socketserver.ThreadingTCPServer(server_address, PocketApiHandler) as httpd:
        print(f"🚀 PocketAgent Companion Daemon running on port {port}")
        print(f"📂 Workspace Root: {WORKSPACE_ROOT}")
        print(f"🔑 Auth Token: {'[Configured]' if token else '[None (Open)]'}")
        print(f"📡 Test with: curl http://localhost:{port}/api/health" + (f" -H 'Authorization: Bearer {token}'" if token else ""))
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nShutting down daemon...")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="PocketAgent Remote Sandbox Daemon")
    parser.add_argument("--port", type=int, default=8787, help="Port to listen on (default: 8787)")
    parser.add_argument("--token", type=str, default="", help="Optional Bearer authentication token")
    parser.add_argument("--workspace", type=str, default=".", help="Root directory for remote workspaces")
    args = parser.parse_args()

    run_server(args.port, args.token, args.workspace)
