# Sources and credits

Firefly / SAM and the source character designs: Honkai: Star Rail, HoYoverse / miHoYo.

Visual and ability research:

- [Official Firefly ability showcase — Keeping Up With Star Rail](https://www.youtube.com/watch?v=1Jct7Gt_-MA), published by Honkai: Star Rail on June 11, 2024. Reference for Complete Combustion, twin blades, flight, and the Deathstar Overload attack beats.
- [Official character introduction](https://www.hoyolab.com/article/29770753), Honkai: Star Rail, June 11, 2024.
- [S.H.Figuarts SAM official product review](https://tamashiiweb.com/tn_blog/200/), TAMASHII NATIONS, September 24, 2025. Reference for the silver armor, gold trim, helmet, shoulder shapes, swords, and energy wing silhouette.
- [Firefly HoYoWiki entry](https://wiki.hoyolab.com/pc/hsr/entry/2494?lang=en-us), official HoYoWiki. Reference for the relationship between Complete Combustion, increased break efficiency, and enhanced attacks.
- [Ham_gyu's Fire Fly Sword model listing](https://sketchfab.com/3d-models/honkai-star-rail-fire-fly-sword-1560ca79fd40480fa784c7e1b51b1279), listed as CC Attribution during the original research. The player supplied the downloaded sword ZIP. The local build imports its `FireFly_Sword.fbx` geometry and base-color/emissive maps. The archive contained no license document or version information, so the mesh and textures are excluded from this public repository and are not relicensed under MIT. Credit: Ham_gyu, "Honkai Star Rail Fire Fly Sword". Character design: HoYoverse / miHoYo.

The player also supplied a SAM OBJ and a still reference of SAM floating with broad green wings. The sculpt's author and redistribution license were not included. The mesh and its derived rig files are excluded from this public repository and are not relicensed under MIT. The local conversion segments the armor into a mechanical rig and reconstructs silver/gold/teal surface colors because the referenced MTL file was absent. No source animation or skeleton was supplied.

- [Universal Animation Library — Quaternius](https://quaternius.itch.io/universal-animation-library), `Walk_Loop` from the free Standard distribution. The source page lists the pack as CC0 1.0 Universal, permits personal and commercial use, and describes its humanoid clips as ready for retargeting. The Standard GLTF files used to bake this clip came from [J-Ponzo's mirror of the June 10, 2025 Standard distribution](https://github.com/J-Ponzo/gltf-universal-animation-library), which includes the CC0 license. The clip is retargeted to SAM's 15 named body joints and tuned with short steps, reduced knee bend, a centered hip arc, and smooth looping; the sword arms use a restrained blend. Only the baked joint keyframes are loaded by Minecraft. Optional credit: Quaternius, "Universal Animation Library" (CC0).

Original mod code and mechanics, the inventory icon, procedural energy wings and atmosphere, and the reconstructed eight-second ultimate were created for this project with AI assistance (Codex). The ultimate adapts the pose and visual beats for Minecraft; it does not reproduce a source Honkai: Star Rail ultimate animation. The 10-hit gate, 2x damage mode, terrain crater, and real-time toughness rules are custom Minecraft mechanics requested by the player.

Minecraft provides the explosion, armor, beacon, elytra, particle, and thunder sounds/effects at runtime. No Minecraft game files, voices, extracted animation clips, or music are included. The public repository omits the supplied SAM mesh and rig, sword mesh and textures, compiled JARs, and local game/test data. The MIT code license does not grant rights to those excluded third-party assets.

Built with Java 21 and NeoForge. Models previewed with Blender. Recon, lab backup, recording, and packaging checks use the [universal-modder plugin](https://github.com/rehan-remade/universal-modder).

Technical references: [NeoForge 1.21.1 custom payloads](https://docs.neoforged.net/docs/1.21.1/networking/payload/), [key mappings](https://docs.neoforged.net/docs/1.21.1/misc/keymappings/), and the locally resolved Minecraft / NeoForge 21.1.209 source artifacts (kept out of this project's distributable).
