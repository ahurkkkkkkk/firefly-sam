# Firefly / SAM: Complete Combustion

A fan-made Minecraft Java 1.21.1 NeoForge mod source project inspired by Firefly and SAM from *Honkai: Star Rail*. It adds an energy blade, a large SAM transformation, animated twin-sword combat, a combo-charged cinematic ground strike, and a toughness / Weakness Break / Super Break system.

## Public source release

This repository publishes the mod's code and original project assets. It does **not** include a playable JAR or the supplied SAM and sword models, their derived mesh files, or the sword's imported textures. Those files were provided for local use, but the SAM model's redistribution terms were not provided and the downloaded sword archive did not contain a license file. They are excluded from this public release; see [CREDITS.md](CREDITS.md).

The code builds without those files, but the resulting JAR will not show the detailed SAM or sword models. A complete playable build requires replacement models and textures that you have the right to use, placed in the corresponding asset paths under `src/main/resources/assets/firefly_sam/`. No compiled binaries or model archives are published here.

## Build and test

Requirements: Java 21 and a network connection for the first Gradle dependency download. The project targets Minecraft Java 1.21.1 and NeoForge 21.1.209.

On Windows PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

The JAR is written to `build/libs/`. Treat it as a code-only development build until you have added properly licensed model assets.

## Controls and combat

Craft the Firefly Blade or use the Combat creative tab. Press **R** to enter or leave SAM. In SAM form, attacks deal twice their normal damage. Land ten charged primary sword hits on mobs to ready the ultimate, then press **G** or right-click while aiming at ground within 48 blocks. The keybinds can be changed in Options → Controls → Firefly / SAM.

The ultimate reveals broad energy wings, raises the blades, dives, and detonates a large terrain strike. It deals very high damage to targets with Weakness Break active. The crater damages mobs, applies hidden fire damage over time to survivors, and protects bedrock, block entities, fluids, unloaded chunks, and the world border. The holder is fire immune while holding the blade. SAM movement, sword attacks, break bars, Super Break, and the compact animated HUD are part of the source project.

The terrain strike edits blocks, so test it in a disposable world. Its range and other combat settings are configurable in `config/firefly_sam-common.toml` after launching a complete local build.

## License and attribution

Original code is licensed under the MIT License in [LICENSE](LICENSE). The retargeted walk keyframes come from Quaternius's Universal Animation Library, released as CC0 1.0; see [CREDITS.md](CREDITS.md) for the reference and asset notes. The excluded SAM and sword assets are not covered by the code license.

This is an unofficial fan project and is not affiliated with or endorsed by HoYoverse. Character names and designs belong to their respective owners.
