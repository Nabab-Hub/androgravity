# Remote Mode Setup Guide

PocketAgent supports running in **Remote Mode**, offloading heavy compilation (`npm install`, `gradle build`, Python ML scripts) and tool execution from your mobile device to your PC, laptop, or Cloud VM.

---

## 1. Quick Start

### Step 1: Run the Companion Daemon on your Host Machine

On your desktop/server (Linux, macOS, or Windows):

```bash
# Clone or copy the pocket-api daemon
cd tools/pocket-api

# Start the daemon on port 8787 with an optional auth token
python3 pocket-api.py --port 8787 --token my_super_secret_token --workspace ~/projects
```

Expected output:
```text
🚀 PocketAgent Companion Daemon running on port 8787
📂 Workspace Root: /home/user/projects
🔑 Auth Token: [Configured]
📡 Test with: curl http://localhost:8787/api/health -H 'Authorization: Bearer my_super_secret_token'
```

---

### Step 2: Configure PocketAgent on Android

1. Open PocketAgent on your phone.
2. Tap **Settings** (gear icon) in the bottom navigation.
3. Scroll down to **Execution Sandbox Mode**.
4. Select **Remote Desktop / Cloud VM**.
5. Enter:
   - **Host / IP**:
     - Android Emulator: `10.0.2.2`
     - Same Wi-Fi Network: Your PC's local IP (e.g. `192.168.1.105`)
     - Remote/Tailscale: Your Tailscale IP (e.g. `100.85.x.x`)
   - **Port**: `8787`
   - **Auth Bearer Token**: `my_super_secret_token`
6. Tap **Test Remote Daemon Connection**.
7. Once verified (`Online: Linux (Latency: ~15ms)`), tap **Save Settings**.

---

## 2. Remote Access Over the Internet

If your phone is on mobile data (4G/5G) or outside your home network, use one of the following secure tunneling methods:

### Option A: Tailscale (Recommended)
1. Install [Tailscale](https://tailscale.com/) on both your desktop and your Android phone.
2. Copy your desktop's 100.x.y.z IP into PocketAgent's **Host** field.
3. Traffic is end-to-end encrypted with zero router port forwarding required.

### Option B: Cloudflare Tunnel
```bash
cloudflared tunnel --url http://localhost:8787
```
Copy the generated `https://xxxx.trycloudflare.com` domain into PocketAgent with **Use HTTPS** enabled.

### Option C: SSH Port Forwarding via ADB (USB tethered)
```bash
adb reverse tcp:8787 tcp:8787
```
Then use `127.0.0.1:8787` directly in PocketAgent!
