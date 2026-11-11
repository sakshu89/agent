# ITRA ↔ Hermes Agent 0.23.1 bridge

This is the companion runtime adapter used by the ITRA Android app. It exposes the subset of the OpenAI Chat Completions API needed by ITRA and invokes Hermes through its public `AIAgent.run_conversation()` interface.

## 1. Prepare Hermes

The exact user-supplied `hermes-agent-0.23.1.zip` is bundled beside this README. Run `./setup_hermes.sh` to extract it under `agent-engine/vendor/` and install its optional dependencies, or extract it manually. Configure Hermes using the official README shipped in that source tree. Hermes supports Python `>=3.11,<3.15`; use a supported Python and its documented environment setup. For a complete optional-tool installation, use the Hermes project's own extras/setup instructions rather than copying Python packages into the Android APK.

The bridge expects the Hermes checkout path in `HERMES_SOURCE_DIR`. The bundled `./run_bridge.sh` sets this automatically when using the bundled vendor directory.

## 2. Start a local model endpoint

Configure Hermes to use your local OpenAI-compatible inference server. Example environment (change model and URL to match your server):

```bash
export HERMES_SOURCE_DIR="$HOME/hermes-agent-0.23.1"
export ITRA_HERMES_BASE_URL="http://127.0.0.1:8080/v1"
export ITRA_HERMES_MODEL="your-local-model-id"
export ITRA_HERMES_API_KEY="local-placeholder-if-required"
export ITRA_HERMES_TOKEN="replace-with-a-long-random-secret"
export ITRA_BRIDGE_HOST="127.0.0.1"
export ITRA_BRIDGE_PORT="8766"
python /path/to/ITRA/agent-engine/itra_hermes_bridge.py
```

`ITRA_HERMES_API_KEY` is optional for local servers that do not require a key. If you use Hermes's configured provider profile instead, configure Hermes first and omit the explicit endpoint/model overrides as appropriate for that profile.

## 3. Connect the Android app

Open ITRA → Settings → **HERMES AGENT ENGINE**.

- Enable **Use Hermes as ITRA reasoning engine**.
- Set URL to `http://127.0.0.1:8766` when Hermes bridge runs on the same Android device (for example, in Termux).
- Paste the exact `ITRA_HERMES_TOKEN` value.
- Save.

ITRA sends ordinary reasoning/research prompts to Hermes. If the bridge is offline or returns an error, the app falls back to its configured ITRA local model. Explicit Android actions (open/play/send/tap/scroll/volume/notification/etc.) bypass Hermes and continue through ITRA's native Android tool/permission pipeline.

## 4. Trusted private LAN (optional)

Loopback is the default and safest configuration. To allow another device on a trusted private LAN to reach the bridge, explicitly start it with:

```bash
export ITRA_BRIDGE_HOST="0.0.0.0"
export ITRA_ALLOW_LAN="1"
export ITRA_HERMES_TOKEN="a-long-random-secret-at-least-16-characters"
```

Then enter the host computer's private IP in ITRA, e.g. `http://192.168.1.20:8766`. Keep the firewall restricted to your phone and never expose this port to the public internet. The Android app rejects public hostnames/IPs and stores the bridge token encrypted with Android Keystore.

## Security and limitations

- The bridge requires bearer-token authentication and caps request bodies at 64 KiB.
- It serializes agent turns to avoid overlapping use of one Hermes session.
- Hermes tools run with the operating-system permissions of the Python process. Browser, terminal, filesystem and other tools can have significant side effects; configure Hermes tool availability and run under a dedicated, least-privilege account.
- ITRA's Android accessibility and device-control tools are not exposed to Hermes. Those actions remain in the Android app and use ITRA's existing confirmations, allowlists and emergency-stop logic.
- This is a companion Python service, not an embedded engine in the APK. Termux/device support for individual Hermes dependencies and browser automation varies; some advanced tools may require a desktop Linux host.
- Streaming is adapted as one final SSE chunk (not token-by-token streaming). The Android client still receives a compatible completion stream.
