#!/usr/bin/env python3
"""Loopback-only OpenAI-compatible adapter from ITRA Android to Hermes Agent.

Set HERMES_SOURCE_DIR to the extracted Hermes Agent 0.23.1 checkout.
The bridge deliberately binds to 127.0.0.1; do not expose it to an untrusted network.
"""
from __future__ import annotations
import json
import os
import secrets
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERMES_SOURCE = Path(os.environ.get("HERMES_SOURCE_DIR", "")).expanduser()
if not HERMES_SOURCE.is_dir():
    raise SystemExit("Set HERMES_SOURCE_DIR to the extracted hermes-agent-0.23.1 directory")
sys.path.insert(0, str(HERMES_SOURCE))

TOKEN = os.environ.get("ITRA_HERMES_TOKEN", "").strip()
if len(TOKEN) < 16:
    raise SystemExit("Set ITRA_HERMES_TOKEN to a random secret of at least 16 characters")
HOST = os.environ.get("ITRA_BRIDGE_HOST", "127.0.0.1")
if HOST not in {"127.0.0.1", "::1", "localhost"}:
    if HOST != "0.0.0.0" or os.environ.get("ITRA_ALLOW_LAN", "0") != "1":
        raise SystemExit("Non-loopback binding requires ITRA_BRIDGE_HOST=0.0.0.0 and ITRA_ALLOW_LAN=1. Only do this on a trusted private LAN.")
PORT = int(os.environ.get("ITRA_BRIDGE_PORT", "8766"))
MODEL = os.environ.get("ITRA_HERMES_MODEL", "")
BASE_URL = os.environ.get("ITRA_HERMES_BASE_URL", "") or None
API_KEY = os.environ.get("ITRA_HERMES_API_KEY", "") or None
PROVIDER = os.environ.get("ITRA_HERMES_PROVIDER", "") or None
MAX_ITERATIONS = max(1, min(24, int(os.environ.get("ITRA_HERMES_MAX_ITERATIONS", "12"))))
_agent = None
_agent_lock = threading.Lock()


def get_agent():
    global _agent
    if _agent is None:
        from run_agent import AIAgent
        kwargs = {"model": MODEL, "max_iterations": MAX_ITERATIONS, "quiet_mode": True,
                  "skip_background_review": True}
        if BASE_URL: kwargs["base_url"] = BASE_URL
        if API_KEY: kwargs["api_key"] = API_KEY
        if PROVIDER: kwargs["provider"] = PROVIDER
        _agent = AIAgent(**kwargs)
    return _agent


class Handler(BaseHTTPRequestHandler):
    server_version = "ITRA-Hermes-Bridge/1.0"
    def log_message(self, fmt, *args):
        print("[ITRA bridge] " + fmt % args, flush=True)

    def send_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/v1/health":
            return self.send_json(200, {"ok": True, "service": "itra-hermes-bridge", "version": "1.0"})
        return self.send_json(404, {"error": {"message": "not found"}})

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            return self.send_json(404, {"error": {"message": "not found"}})
        if not secrets.compare_digest(self.headers.get("Authorization", ""), "Bearer " + TOKEN):
            return self.send_json(401, {"error": {"message": "unauthorized"}})
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if size <= 0 or size > 65536:
                return self.send_json(413, {"error": {"message": "request body must be 1..65536 bytes"}})
            payload = json.loads(self.rfile.read(size))
            messages = payload.get("messages") or []
            user_message = next((m.get("content", "") for m in reversed(messages)
                                 if m.get("role") == "user" and isinstance(m.get("content"), str)), "")
            if not user_message.strip():
                return self.send_json(400, {"error": {"message": "missing user message"}})
            system_message = "\n\n".join(m.get("content", "") for m in messages
                                          if m.get("role") == "system" and isinstance(m.get("content"), str))
            history = [{"role": m.get("role"), "content": m.get("content", "")}
                       for m in messages[:-1] if m.get("role") in {"user", "assistant"}]
            system_message = (system_message or "") + "\n\nITRA INTEGRATION BOUNDARY: You are running as the reasoning engine for the ITRA Android assistant. You do not have direct access to ITRA's Android accessibility, app-launch, messaging, media, notification, or device-control tools. Never claim to have performed an Android phone action. Handle reasoning and the tools actually available in this Hermes runtime; ITRA itself handles phone actions through its native permission-gated tools."
            with _agent_lock:
                result = get_agent().run_conversation(
                    user_message, system_message=system_message,
                    conversation_history=history[-12:])
            answer = (result or {}).get("final_response") or "I couldn't complete that request."
            if not isinstance(answer, str): answer = str(answer)
            if payload.get("stream", False):
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream; charset=utf-8")
                self.send_header("Cache-Control", "no-cache, no-store")
                self.send_header("Connection", "close")
                self.end_headers()
                chunk = {"id": "chatcmpl-" + uuid.uuid4().hex, "object": "chat.completion.chunk",
                         "choices": [{"index": 0, "delta": {"content": answer}, "finish_reason": None}]}
                self.wfile.write(("data: " + json.dumps(chunk, ensure_ascii=False) + "\n\n").encode())
                self.wfile.write(b"data: [DONE]\n\n")
            else:
                self.send_json(200, {"id": "chatcmpl-" + uuid.uuid4().hex, "object": "chat.completion",
                    "choices": [{"index": 0, "message": {"role": "assistant", "content": answer}, "finish_reason": "stop"}]})
        except Exception as exc:
            print(f"[ITRA bridge] request failed: {type(exc).__name__}: {exc}", flush=True)
            self.send_json(500, {"error": {"message": "Hermes agent request failed; inspect bridge log."}})


def main():
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"ITRA ↔ Hermes bridge listening on http://{HOST}:{PORT}", flush=True)
    print("Health: GET /v1/health | Chat: POST /v1/chat/completions", flush=True)
    server.serve_forever()

if __name__ == "__main__":
    main()
