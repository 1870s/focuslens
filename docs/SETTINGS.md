# Settings reference

Open the settings with the **FocusLens...** button in *Options → Video Settings* (top-right corner), from
Mod Menu (Fabric) or the Mods list (NeoForge), or with the optional *Open FocusLens Settings* key.
Hovering a setting shows the same explanation in the game. Everything is saved to `config/focuslens.json`
when the screen closes; key bindings are saved in Minecraft's `options.txt`.

## Zoom

| Setting | Default | Effect |
|---|---|---|
| Enable zoom | On | Turns the zoom off completely when disabled. |
| Zoom key | C | Key (or mouse button) for the zoom. Escape while assigning unbinds it. Starts unbound if another zoom mod is installed. |
| Activation | Hold | *Hold*: zoom while the key is held (OptiFine). *Toggle*: press to zoom in, press again to zoom out (stays zoomed while menus are open; reset when leaving the world). |
| Zoom factor | 4.0x | The final field of view is divided by this value. 4.0x is OptiFine's value. Range 1.5–16 (scroll zoom reaches 50). |
| Mouse while zoomed | Cinematic (OptiFine) | *Cinematic*: smoothed camera while zoomed, like OptiFine; your own cinematic setting is untouched. *Scaled to FOV*: precise aim, rotation scaled by `tan(zoomedFov/2)/tan(fov/2)`. *Unchanged*: vanilla mouse. |
| Zoom sensitivity | 100% | Multiplier on mouse movement while zoomed, applied on top of the mode. |
| Zoom the hand | On | Applies the zoom to the first-person hand as OptiFine does. |
| Scroll to adjust zoom | Off | Mouse wheel changes the zoom (×1.2 per notch, 1–50×) instead of the hotbar while zooming. |
| Reset scroll zoom on release | On | Each zoom starts at the zoom factor. Off keeps the last scrolled level until restart. |
| Show zoom level | On | Shows e.g. `6.9x` under the crosshair briefly after scrolling. |
| Smooth zoom animation | Off | Eases the FOV in/out. OptiFine switches instantly. |
| Animation speed | 14 | Easing rate (1/s). Higher is faster. |
| Restore OptiFine behaviour | – | Resets all zoom settings and the key to the OptiFine defaults. |

## Performance

| Setting | Default | Effect | Visual change |
|---|---|---|---|
| Profile | Default | *Default*: lossless culling only. *Balanced*: + distance limits 48/48/48. *Performance*: + 32/32/24. Becomes *Custom* when any setting is changed by hand. | Only via the limits |
| Entity section culling | On | Skips entities in sections that vanilla's occlusion graph proved invisible. Off with Sodium (Sodium culls entities itself). | No |
| Entity ray culling | On | Background ray test hides entities fully behind opaque blocks. Off when the Entity Culling mod is installed. | No¹ |
| Block entity ray culling | On | Same for chests, signs, banners, skulls, shulker boxes, … | No¹ |
| Block entity frustum culling | On | Per-block-entity view-frustum test (Fabric; NeoForge has it built in). | No |
| Particle occlusion culling | On | Skips particles in sections proven invisible. Off with Sodium. | No |
| Keep name tags visible | On | Never culls players or entities with a shown name tag (vanilla draws name tags through walls). | Turning it off can hide name tags behind walls |
| Block entity distance | Off | Hide block entities beyond N blocks (vanilla draws most up to 64). | Yes |
| Decorative entity distance | Off | Hide dropped items, item frames, paintings, armor stands, XP orbs beyond N blocks. | Yes |
| Particle distance | Off | Hide particles beyond N blocks. | Yes |

¹ Results are one frame behind the camera. Turning quickly around a corner can show an entity or block
entity one frame late. Results are thrown away when the camera moves more than 0.75 blocks.

## General

| Setting | Default | Effect |
|---|---|---|
| Performance overlay | Off | Top-left: FPS, average/max frame time, objects hidden in the last frame per optimization. |
| Open settings key | Unbound | Opens the settings in game. |
| Benchmark key | Unbound | Starts or cancels the OFF-vs-ON benchmark. |
| Pass length | 30 s | Length of one recorded benchmark pass. |
| Compare OFF vs ON / Measure current settings | – | See [BENCHMARKING.md](BENCHMARKING.md). |

## Config file

`config/focuslens.json` is plain JSON. Invalid values are clamped when loaded; an unreadable file is
renamed to `focuslens.json.broken` and defaults are used.
