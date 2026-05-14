# Cosmic Proximity

Proximity voice chat for Minecraft 1.21.11 (Fabric). Hear nearby players, fade out distant ones. Voice routes through a relay server you control — works on any Minecraft server you join, no server-side mod required.

## Features

- **Proximity audio** — linear distance falloff with stereo panning based on listener orientation
- **Push-to-talk or Open Mic** (voice activity detection)
- **Adaptive noise suppression** — Discord-style noise gate with envelope follower
- **Per-server isolation** — players on different MC servers can't hear each other
- **In-world speaking indicators** — green ♪ appears above currently-talking players
- **Speaker badge in nametags** — light-cyan ♪ prefix on names of players who have the mod
- **HUD pill** — bottom-left shows connection / speaking / muted / deafened state
- **Personal mute** — settings screen list of recent speakers with toggles, plus `/voicemute <name>` chat command
- **Encrypted UDP** — AES-256-GCM, password-derived shared key
- **AWS-friendly relay** — runs anywhere with JDK 21+, ~25 MB jar, ~75 MB RAM at idle

## Requirements

- Minecraft 1.21.11
- [Fabric Loader](https://fabricmc.net/use/installer/)
- [Fabric API](https://modrinth.com/mod/fabric-api) (recent build for 1.21.11)
- JDK 21 to build from source

## Install (for players)

1. Drop `cosmicproximity-<version>.jar` into your `mods/` folder.
2. Drop the Fabric API jar in the same folder.
3. Launch the **Fabric 1.21.11** profile.
4. Press **K** in-game → fill in your relay host/port/password if not pre-configured.
5. Join any Minecraft server.

The mod auto-connects to the relay the moment you finish joining a world.

## Build from source

```bash
git clone https://github.com/<your-user>/cosmicproximity.git
cd cosmicproximity
./gradlew build
```

Output: `build/libs/cosmicproximity-<version>.jar`. JDK 21 required (Gradle's wrapper will fetch its own Gradle).

### Bake in your relay coordinates

By default the mod ships with no hardcoded relay. Users configure host/password in-game.

If you want a private friend-group build where the jar auto-connects without any UI configuration, create:

```
src/main/resources/cosmicproximity/relay-local.properties
```

with contents:

```properties
enabled=true
host=your.relay.example.com
port=24454
password=your-shared-room-password
voiceRange=48
```

This file is **gitignored** — it won't be committed. Build with `./gradlew build` and the resulting jar will auto-connect to your relay.

## In-game controls

| Key | Action |
|---|---|
| `V` (hold) | Push to talk |
| `M` | Toggle mic mute |
| `N` | Toggle deafen |
| `K` | Open voice settings |

Settings menu lets you switch between Push-to-Talk and Open Mic (VAD), pick mic/speaker devices, adjust noise suppression and volume, test your audio, and mute individual players.

Chat commands:

- `/voicemute <player>` — personal client-side mute
- `/voiceunmute <player>` — unmute
- `/voicemutes` — list current mutes

## Architecture

```
       ┌─────────────────────────┐                ┌─────────────────────────┐
       │ Minecraft client A      │                │ Minecraft client B      │
       │  + Cosmic Proximity mod │                │  + Cosmic Proximity mod │
       └──────────┬──────────────┘                └─────────────┬───────────┘
                  │ encrypted UDP                               │
                  ▼                                             ▼
                  ┌───────────────────────────────────────────────┐
                  │   Cosmic Proximity Relay                       │
                  │   - Decrypts, looks up sender position         │
                  │   - For each other client within voiceRange    │
                  │     in same room + dimension, re-encrypts      │
                  │     and forwards the voice frame.              │
                  └───────────────────────────────────────────────┘
```

The Minecraft server doesn't need the mod. Clients self-report their world position to the relay; routing happens entirely at the relay.

## Relay

This repo contains only the client mod. To run voice you need a relay server speaking the protocol defined in [`net/VoicePackets.java`](src/main/java/com/cosmicproximity/net/VoicePackets.java). Anyone can write one. The protocol is small:

- Encrypted UDP with AES-256-GCM
- Shared key derived from password via SHA-256
- Packet types: HELLO, REGISTER, POSITION, VOICE, KEEPALIVE, DISCONNECT, MUTED_STATUS, PARTICIPANT_LIST
- Outer envelope: `[16 byte sessionId][12 byte nonce][ciphertext + 16 byte GCM tag]`
- Inner format documented inline in `VoicePackets.java`

## Trust model

⚠ **Designed for trusted friend groups.** Everyone with the room password shares the same encryption key — so a participant *could* decrypt other participants' voice if they sniffed UDP. Per-client ECDH key exchange would be needed for adversarial use. Pull requests welcome.

Also:
- Players self-report position to the relay; the relay can't independently verify
- Anyone with the password can register as any Minecraft UUID

Both are acceptable for "small group of friends" use cases and not safe for public-facing deployment.

## License

MIT. See [LICENSE](LICENSE).
