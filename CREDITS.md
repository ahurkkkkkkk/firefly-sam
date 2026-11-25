# Sources and credits

Firefly / SAM and the source character designs: Honkai: Star Rail, HoYoverse / miHoYo.

Visual and ability research:

- [Official Firefly ability showcase — Keeping Up With Star Rail](https://www.youtube.com/watch?v=1Jct7Gt_-MA), published by Honkai: Star Rail on June 11, 2024. Reference for Complete Combustion, twin blades, flight, and the Deathstar Overload attack beats.
- [Official character introduction](https://www.hoyolab.com/article/29770753), Honkai: Star Rail, June 11, 2024.
- [S.H.Figuarts SAM official product review](https://tamashiiweb.com/tn_blog/200/), TAMASHII NATIONS, September 24, 2025. Reference for the silver armor, gold trim, helmet, shoulder shapes, swords, and energy wing silhouette.
- [Firefly HoYoWiki entry](https://wiki.hoyolab.com/pc/hsr/entry/2494?lang=en-us), official HoYoWiki. Reference for the relationship between Complete Combustion, increased break efficiency, and enhanced attacks.
- The player supplied `Firefly.zip`, which contains Firefly, SAM, and sword PMX files, textures, and `使用规则.txt`. The readme identifies model editor 神帝宇 and copyright owner miHoYo. It permits modification, but says not to redistribute the models or use them commercially. The project imports `Sam.pmx` and `Sword.pmx`; the sword mesh is bound to each SAM palm and also powers the standalone blade item. The archive, converted meshes, textures, Blockbench project, and derived animations remain local and are excluded from public Git and release archives. The restrictions apply to derivatives too. No official source animation clip was supplied.

- [Universal Animation Library — Quaternius](https://quaternius.com/packs/universalanimationlibrary.html), `Walk_Loop` and `Sprint_Loop` from the free Standard distribution. The source page and included license identify the pack as CC0 1.0. The Standard GLTF files used to bake the clips came from [J-Ponzo's mirror of the June 10, 2025 Standard distribution](https://github.com/J-Ponzo/gltf-universal-animation-library), which includes the CC0 license. The clips are retargeted to SAM's 15 body joints and tuned for shorter steps, reduced knee bend, steady torso height, and a restrained sword-arm blend. The user selected an original SAM-specific three-hit sword combo because the optional second Quaternius pack was not available locally. Version 2.0.2 replaces the retargeted sprint with an original twin-sword running cycle.

Original mod code and mechanics, the inventory icon, procedural energy wings and atmosphere, and the reconstructed eight-second ultimate were created for this project with AI assistance (Codex). GeckoLib 4.9.4 provides the Blockbench animation runtime. The ultimate adapts the pose and visual beats for Minecraft; it does not reproduce a source Honkai: Star Rail ultimate animation. The 10-hit gate, alternating right/left/paired sword combo, 2x damage mode, terrain crater, and real-time toughness rules are custom Minecraft mechanics requested by the player.

Minecraft provides the explosion, armor, beacon, elytra, particle, and thunder sounds/effects at runtime. No Minecraft game files, voices, extracted animation clips, or music are included. The public repository omits the supplied SAM mesh and rig, sword mesh and textures, compiled JARs, and local game/test data. The MIT code license does not grant rights to those excluded third-party assets.

Built with Java 21 and NeoForge. Models previewed with Blender. Recon, lab backup, recording, and packaging checks use the [universal-modder plugin](https://github.com/rehan-remade/universal-modder).

Technical references: [NeoForge 1.21.1 custom payloads](https://docs.neoforged.net/docs/1.21.1/networking/payload/), [key mappings](https://docs.neoforged.net/docs/1.21.1/misc/keymappings/), and the locally resolved Minecraft / NeoForge 21.1.209 source artifacts (kept out of this project's distributable).
