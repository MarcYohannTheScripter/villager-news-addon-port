# Villager News Addon Port

A Fabric port of **Villager News 1.0.4** for Minecraft Java Edition 26.2.
It brings the original Villager News characters, models, animations, textures,
voice acting, and contextual dialogue to Java Edition while retaining normal
Minecraft villager gameplay.

## Features

- Detailed animated Villager News models converted for Entity Model Features
- Biome, profession, and profession-level villager textures
- The Mayor, Testificate Man, Villager Number 5, Villager Number 9, and
  Villager Unreachable as named characters
- Wooly the Sheep and the Villager News wandering trader
- 2,212 original voice clips across 523 dialogue groups
- Context-aware dialogue for player actions, nearby mobs, weather, dimensions,
  combat, trading, work, sleep, spawning, growth, and other world events
- Multi-part conversations between nearby villagers
- Facial expressions and gestures synchronized with each voice line
- Server-controlled dialogue selection, sound playback, cooldowns, and
  villager behavior
- Speakers look toward the player, entity, block, or villager they are talking
  about

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.5 or newer
- Fabric API for Minecraft 26.2
- Entity Model Features 3.3.5 or newer
- Entity Texture Features 7.2.1 or newer
- Entity Sound Features 0.8.2 or newer

EMF, ETF, and ESF are external dependencies. This project does not bundle or
modify them.

## Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Download Fabric API, EMF, ETF, and ESF for the same Minecraft version.
3. Put the dependency jars and the Villager News Addon Port jar in the
   Minecraft `mods` folder.
4. Start Minecraft with the Fabric profile.

The dialogue controller runs on the server. For multiplayer, install the mod
and its dependencies on both the server and every connecting client so models,
animations, textures, and sounds are available to everyone.

## Characters

Use a name tag on a villager to select a character model and voice:

| Name tag | Character |
| --- | --- |
| `Mayor`, `Mayor Villager`, or `The Mayor` | Mayor Villager |
| `Testificate Man` | Testificate Man |
| `Villager Number 5` or `Villager #5` | Villager Number 5 |
| `Villager Number 9` or `Villager #9` | Villager Number 9 |
| `Villager Unreachable` or `Can't Catch Me!` | Villager Unreachable |

Name a sheep `Wooly` or `Wooly The Sheep` to use Wooly's model, animations,
and sounds. Ordinary villagers and wandering traders receive their Villager
News appearance and dialogue automatically.

## Dialogue

Villagers react to what happens around them. They can comment when a player
approaches, stares, changes game mode, wears armor, receives an effect, breaks
or places a block, uses an item, completes a trade, or spawns a villager with a
spawn egg. They also react to their profession, workstation, level, biome,
weather, time of day, nearby entities, damage source, and other villagers.

The server chooses the exact voice variant and broadcasts its matching
animation. Each speaker remains occupied for the real length of the clip,
preventing unrelated lines from overlapping. Conversation partners take turns
and continue looking at each other throughout multi-part exchanges.

## Building from source

On Windows:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```bash
./gradlew build
```

The distributable jar is written to `build/libs`.

Run the asset and dialogue verification with:

```powershell
node tools/verify-port.mjs
```

## Credits

Villager News and the original add-on assets were created by **Oreville
Studios Ltd** and **Element Animation**. The converted models, textures,
animations, and audio remain the property of their respective owners. See
[`LICENSE`](LICENSE) for repository licensing details.
