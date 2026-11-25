# Firefly / SAM: Complete Combustion

A fan-made Minecraft Java 1.21.1 NeoForge mod source project inspired by Firefly and SAM from *Honkai: Star Rail*. It adds an energy blade, a large SAM transformation, animated twin-sword combat, a combo-charged cinematic ground strike, and a toughness / Weakness Break / Super Break system.

## Public source release

This repository publishes the mod code and original project assets. It does **not** include a playable JAR, the supplied SAM and sword models, their derived meshes, textures, or Blockbench project. The supplied model readme permits modification but prohibits redistribution and commercial use. Those assets and their derivatives stay local; see [CREDITS.md](CREDITS.md).

The source is designed around local copies of the supplied model assets, so a clean checkout builds the code but cannot render the detailed SAM and sword. The model ZIP is not stored or redistributed by this project. For a local build, extract the supplied files to `artifacts/sam-mmd-source` and install the Blender MMD Tools add-on. Place the Quaternius UAL1 Standard `.gltf`, `.bin`, and CC0 license in `artifacts/animations/quaternius-ual`. Then run:

```powershell
blender -b --python scripts/import_firefly_sword_pmx.py
blender -b --python scripts/import_sam_pmx.py
blender -b --python scripts/bake_walk_cycle.py
python scripts/export_sam_blockbench.py
```

The SAM source mesh, generated GeckoLib geometry and animations, imported textures, walk keyframes, and editable Blockbench project stay ignored by Git. The test profile also needs GeckoLib 4.9.4 at runtime; Gradle resolves it from GeckoLib's Maven repository. Do not share a local model-bearing JAR or the converted files.

## Build and test

Requirements: Java 21, NeoForge 21.1.209, GeckoLib 4.9.4, and a network connection for the first Gradle dependency download. The project targets Minecraft Java 1.21.1.

On Windows PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

The JAR is written to `build/libs/`. A build made with the local supplied assets is for local testing only and must not be redistributed.

## Controls and combat

Craft the Firefly Blade or use the Combat creative tab. Press **R** to enter or leave SAM. In SAM form, attacks deal twice their normal damage. Land ten charged primary sword hits on mobs to ready the ultimate, then press **G** or right-click while aiming at ground within 48 blocks. The keybinds can be changed in Options → Controls → Firefly / SAM.

The ultimate reveals broad energy wings, raises the blades, dives, and detonates a large terrain strike. It deals very high damage to targets with Weakness Break active. The crater damages mobs, applies hidden fire damage over time to survivors, and protects bedrock, block entities, fluids, unloaded chunks, and the world border. The holder is fire immune while holding the blade. SAM movement, sword attacks, break bars, Super Break, and the compact animated HUD are part of the source project.

The terrain strike edits blocks, so test it in a disposable world. Its range and other combat settings are configurable in `config/firefly_sam-common.toml` after launching a complete local build.

## License and attribution

Original code is licensed under the MIT License in [LICENSE](LICENSE). The retargeted walk and sprint keyframes come from Quaternius's Universal Animation Library, released as CC0 1.0. The SAM specific attack and ultimate keyframes were created for this project. See [CREDITS.md](CREDITS.md) for references and asset notes. GeckoLib 4 provides the Blockbench animation runtime. The excluded SAM and sword assets and their derivatives are not covered by the code license.

This is an unofficial fan project and is not affiliated with or endorsed by HoYoverse. Character names and designs belong to their respective owners.
