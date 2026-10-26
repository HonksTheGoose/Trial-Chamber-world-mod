# Trial World (NeoForge 1.21.1)

Adds a world preset, **Trial Chamber World**, where the overworld is solid deepslate with
trial chambers generated densely through it. You start inside a chamber.

## Build

Needs JDK 21. The Gradle wrapper is included.

    ./gradlew build

The jar is in `build/libs/`.

**On GitHub:** push this folder to a repo. The *Build* workflow runs on every push. Open the repo's
**Actions** tab, click the latest run, and download the `trialworld-jar` artifact (it contains the mod jar). Drop it in `mods/`. If Gradle rejects `neo_version` or the
`net.neoforged.moddev` plugin version, copy the current values from the MDK.

## Use

- Singleplayer: Create New World -> More tab -> World Type -> **Trial Chamber World**.
- Server: `level-type=trialworld\:trial_world` in `server.properties`.

Only worlds created with the preset are affected. Normal worlds are untouched.

## How it works, and what to edit

| Piece | File | Edit it to... |
|---|---|---|
| World shape | `data/trialworld/worldgen/world_preset/trial_world.json` | Change chamber density (`spacing`/`separation`), the fill block, or the structure |
| Biome + base mobs | `data/trialworld/worldgen/biome/chamber.json` | Change the always-on mob list |
| Extra mobs | `data/trialworld/neoforge/biome_modifier/extra_spawns.json` | Add any mob, including modded ones, by entity id |
| Block palette | `config/trialworld-common.toml` (`palette_replacements`) | Add or remove `from=to` block swaps |
| Spawn search | `config/trialworld-common.toml` (`structure`) | Point at a different structure |

Why it survives small changes to the chamber:

- The chamber itself is the unmodified vanilla structure (`minecraft:trial_chambers`). Edits made by datapacks
  or other mods to its pools, templates or processors flow through automatically.
- The palette swap works on block ids after the structure is placed. It never references templates, pools or
  coordinates. Unknown block ids are skipped with a log warning.
- The spawn point is found at runtime by asking the world generator where a chamber is, then scanning the
  placed pieces for open floor. If that fails it carves a small lit room instead of dropping you in rock.

## Adding a modded mob

Add a line to `extra_spawns.json`, for example:

    { "type": "somemod:some_mob", "weight": 10, "minCount": 1, "maxCount": 1 }

An entry naming a mob that does not exist makes that whole file fail to load. To keep a modded mob optional,
put it in its own file (for example `extra_spawns_somemod.json`) and add a condition at the top level:

    "neoforge:conditions": [ { "type": "neoforge:mod_loaded", "modid": "somemod" } ]
