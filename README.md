# RPGuard

**RPGuard** is a protection and block restoration plugin for Minecraft servers running Paper.

The plugin helps server administrators control block placement and breaking while keeping the world protected from unwanted modifications. Blocks broken by authorized players can be automatically restored after a configurable delay, helping maintain the original state of the map.

## Features

- **Block Placement Protection** — Prevents players from placing blocks without the required permission.
- **Controlled Block Breaking** — Allows administrators to control who can break blocks.
- **Automatic Block Restoration** — Broken blocks can be restored after a configurable amount of time.
- **Permanent Block Breaking** — Players with the appropriate permission can break blocks without restoration.
- **Explosion Protection** — Prevents explosions from destroying blocks, including explosions caused by TNT and hostile mobs.
- **Fire Protection** — Prevents fire from burning blocks.
- **SQLite Storage** — Stores pending block restorations in a database.
- **Configurable Settings** — Customize restoration delays and processing options.

## Permissions

| Permission | Description |
|---|---|
| `rpguard.build` | Allows placing blocks despite RPGuard's restrictions. |
| `rpguard.mine` | Allows breaking blocks. |
| `rpguard.mine.permanent` | Allows breaking blocks permanently without restoration. |

## Configuration

RPGuard provides configuration options for block restoration, including the restoration delay and batch processing size.

## Compatibility

- Minecraft server software: Paper
- Platform: Bukkit / Paper plugin API
- Storage: SQLite

## Purpose

RPGuard is designed for servers that need controlled world modification without losing the original map. It can be useful for adventure maps, protected public areas, resource zones, and other server environments where blocks should return to their original state after being mined.

## License

See the repository's license file for usage and distribution terms.

Created and maintained by Date44.

Copyright (c) 2026 Date44.

Licensed under the GNU General Public License v3.0.
See [LICENSE](LICENSE) for details.
