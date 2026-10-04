# ⚡ wapeb-velocity - Official Velocity Proxy Companion for wapeB (v1.0.1)

[![Velocity](https://img.shields.io/badge/Velocity%20Proxy-3.3%2B-6366f1?logo=velocity&logoColor=white)](https://papermc.io/software/velocity)
[![wapeB Backend](https://img.shields.io/badge/wapeB-Backend%20Plugin-blue?logo=minecraft)](https://github.com/lftkraft/wapeB)
[![Java](https://img.shields.io/badge/Java-17%2B-red?logo=openjdk)](https://adoptium.net/)

**wapeb-velocity** is the official high-speed Velocity proxy companion plugin for the **[wapeB](https://github.com/lftkraft/wapeB)** Minecraft punishment management system.

It bridges punishment events and chat snapshots across backend Paper/Spigot servers with zero database polling lag using Minecraft's native Plugin Messaging channel (`wapeb:main`).

---

## ✨ Features

- ⚡ **Instant Cross-Server Forwarding**: Forward punishment broadcasts (`BAN`, `TEMPBAN`, `MUTE`, `WARN`, `KICK`, `UNBAN`, `LOCKDOWN`) instantly between all connected backend servers without database polling delay.
- 🛑 **Proxy-Level Kick Enforcement**: Automatically disconnects players at the proxy layer with formatted kick screens when a network-wide (`global`) or server-specific ban/kick occurs.
- 📁 **Centralized Chat Snapshot Storage**: Receives and categorizes player chat snapshots per server (`plugins/wapeb-velocity/snapshots/<server_name>/<punishment_id>.json`).
- 🛡️ **Client Spoofing Protection**: Validates message origins (`ServerConnection`) and automatically rejects any spoofed/injected packets sent from hacked clients (Meteor Client, etc.).
- 🎯 **Target Server Matching**: Respects comma-separated multi-server scopes (e.g. `survival,skyblock`) and only disconnects players if they are connected to one of the target servers.
- 🔁 **Smart Broadcast Deduplication**: Prevents duplicate chat and console messages across the network.

---

## 🚀 Installation & Setup

1. Download `wapeb-velocity-1.0.1.jar` from [Releases](https://github.com/lftkraft/wapeb-velocity/releases) or build with Gradle.
2. Place `wapeb-velocity-1.0.1.jar` into your Velocity proxy's `plugins/` directory.
3. Install **wapeB (v1.0.13-alpha.2+)** on all backend Paper/Spigot servers.
4. Ensure all backend servers have unique `server-name` values configured in `plugins/wapeB/config.yml`.
5. Restart your Velocity proxy and backend servers.

---

## 🛠️ Building from Source

```bash
git clone https://github.com/lftkraft/wapeb-velocity.git
cd wapeb-velocity
./gradlew build
```

The compiled jar will be available at `build/libs/wapeb-velocity-1.0.1.jar`.

---

## 📜 License & Related Projects
- **Backend Plugin**: [wapeB Repository](https://github.com/lftkraft/wapeB)
- **Developed for**: Velocity 3.3.0+ (Java 17+)
