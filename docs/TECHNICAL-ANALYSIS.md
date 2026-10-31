# Technical analysis

This document records what was investigated before FocusLens was written: how the OptiFine zoom behaves,
where Minecraft 26.x still spends avoidable work, what existing performance mods do, and why FocusLens
implements the optimizations it does — and deliberately not others.

All statements about vanilla Minecraft refer to the decompiled 26.1.2, 26.2 and 26.3 sources produced by
NeoForm/ModDevGradle during the build (Mojang mappings). Nothing here is based on OptiFine source code;
OptiFine is closed source and its license does not allow reuse.

## 1. Versions and toolchain (verified 2026-10-31)

| Minecraft | Status | Fabric API | NeoForge | Java |
|---|---|---|---|---|
| 26.1.2 | released | 0.155.3+26.1.2 | 26.1.2.125 | 25 |
| 26.2 | released | 0.161.0+26.2 | 26.2.0.93 | 25 |
| 26.3 | released (latest) | 0.165.0+26.3 | 26.3.0.100 | 25 |

Sources: Mojang `version_manifest_v2.json`, `meta.fabricmc.net`, Modrinth (Fabric API), NeoForged Maven.
All 26.x versions ship unobfuscated (Mojang names), so neither loader needs a remapping step.
Toolchain: Gradle 9.8.1, Fabric Loom 1.19.3 (`net.fabricmc.fabric-loom`), ModDevGradle 2.0.148.

## 2. The OptiFine zoom

OptiFine's zoom, as players experience it:

1. It is active only while the zoom key (default **C**) is held and no screen (inventory, chat, menu) is open.
2. The field of view is divided by **4**. The division happens after all vanilla FOV effects (sprinting,
   speed/slowness, bow drawing, water/lava, death), so those effects still apply while zoomed.
3. There is no zoom animation: the FOV switches instantly in both directions.
4. While zoomed, the **cinematic (smooth) camera** is switched on. This is what makes OptiFine's zoom feel
   "slow and floaty": vanilla's cinematic camera smooths and damps mouse movement. When the key is released,
   the player's previous cinematic-camera setting is restored.
5. The first-person hand and held item are zoomed together with the world.
6. There is no scroll-wheel adjustment and no toggle mode.

### How FocusLens reproduces it

| Behaviour | Implementation |
|---|---|
| FOV ÷ 4 after all effects | `Camera#calculateFov` returns the fully modified FOV; FocusLens divides the **return value** (`CameraMixin`). All vanilla modifiers are multiplicative, so this is equivalent to OptiFine's order. |
| Hand zoomed too | Same for `Camera#calculateHudFov` (option "Zoom the hand"). |
| Cinematic camera while zoomed | `MouseHandler#turnPlayer` reads `Options.smoothCamera`; FocusLens changes the value of that **read** to `true` while zoomed (`MouseHandlerMixin`). The option itself is never written, so the player's setting cannot get lost — even if the game crashes while zoomed. |
| No jump when releasing | On the first frame without cinematic camera, vanilla resets its smoothing filters (`smoothTurnX/Y.reset()`), so leftover smoothing is discarded exactly like OptiFine does. |
| Key C, hold, no screen | `KeyMapping` with default C, checked once per frame together with "no screen open". |

Mouse sensitivity: OptiFine does not scale sensitivity; its "reduced sensitivity" is the cinematic camera's
damping. FocusLens offers two extra modes: **Scaled to FOV** (rotation multiplied by
`tan(zoomedFov/2) / tan(fov/2)`, so the picture moves across the screen at the same speed as without zoom,
no smoothing) and **Unchanged**. A sensitivity multiplier applies on top of every mode.

Extras that OptiFine does not have are all off by default: toggle mode, scroll-wheel zoom (×1.2 per notch,
1–50×), smooth animation (frame-rate independent exponential easing), zoom-level indicator.

## 3. Where Minecraft 26.x spends avoidable work

Rendering in 26.x is split into an **extract** phase (build render-state objects on the main thread) and a
**submit/render** phase. `LevelExtractor#extract` (26.2/26.3; `LevelRenderer` in 26.1.2) runs every frame and
walks entities, block entities, particles, weather, sky, border and clouds.

| Area | What vanilla does | Gap |
|---|---|---|
| Terrain sections | Async section occlusion graph (`SectionOcclusionGraph`, "cave culling") + frustum; compiled on worker threads. | Already good. Not touched. |
| Entities | `EntityRenderDispatcher#shouldRender`: per-entity frustum test + distance; `isEntityVisible` additionally checks that the section is compiled (fade-in alpha). | **No occlusion test at all.** An entity behind a hill or inside a cave that the occlusion graph already proved invisible is still extracted (allocating a render state) and submitted every frame. |
| Block entities | Taken only from `visibleSections()` (graph + frustum, per 16³ section). | No **per-block-entity** frustum test on vanilla/Fabric (NeoForge adds one). No fine occlusion inside visible sections — a chest room behind a wall in a visible section is fully drawn. |
| Particles | `QuadParticleGroup` tests each particle with `Frustum#pointInFrustum`. | No occlusion test: particles in caves behind terrain are drawn. |
| Frame pacing | `FramerateLimitTracker` caps FPS when minimized / AFK; `inactivityFpsLimit` option. | Already handled by vanilla. |
| GUI | Retained-mode GUI renderer since 26.2 batches GUI draws. | ImmediatelyFast-style HUD batching is largely obsolete. |

## 4. Existing performance mods

| Mod | License | What it optimizes | Client-only? | Relation to FocusLens |
|---|---|---|---|---|
| OptiFine | Proprietary, closed | Rendering options, zoom, shaders, connected textures, etc. | Yes | Zoom behaviour reproduced from observation only. Not used, not compatible. |
| Sodium | PolyForm Shield 1.0.0 (source-available) | Replaces the terrain renderer (vertex formats, multidraw, own section graph), culls entities by section, animates only visible sprites. | Yes | Complementary. With Sodium installed FocusLens disables its section-graph based features (Sodium has its own graph) and keeps ray-traced culling and the zoom. |
| Lithium | LGPL-3.0-only | Game logic (ticking, AI, physics, collisions). | Works on the integrated server too | No overlap; recommended alongside. |
| Entity Culling | tr7zw Protective License | Asynchronous ray-traced culling of entities and block entities. | Yes | Same idea as FocusLens' ray culling. FocusLens' implementation is independent (own DDA ray traversal, own scheduling). If Entity Culling is installed, FocusLens turns its ray culling off. |
| ImmediatelyFast | LGPL-3.0-or-later | Batching of immediate-mode rendering (HUD, text, maps, signs). | Yes | No overlap; compatible. |
| FerriteCore | MIT | Memory: deduplicates block-state and model data. | Yes | No overlap; recommended alongside. |

Licensing consequence: no code from any of these mods is included in FocusLens. Only publicly documented
algorithms are used (frustum/occlusion culling, Amanatides & Woo voxel traversal from 1987).

## 5. What FocusLens implements and why

1. **Entity section culling** — reuse vanilla's own occlusion graph (`SectionOcclusionGraph#getNode`) for
   entities: if the graph search never reached the entity's section(s), the entity cannot be visible.
   Cost: one array lookup per entity, cached per section. No visual change; it is exactly the test vanilla
   already trusts for terrain and block entities. Disabled with Sodium (vanilla's graph is not maintained then).
2. **Ray-traced occlusion culling (entities and block entities)** — a background thread casts up to nine rays
   (box centre and eight corners, box padded by 0.5 blocks) through the client's block data and treats only
   full, opaque blocks (`BlockState#isSolidRender`) as blockers. Glass, leaves, slabs, fences etc. never block.
   Main thread never waits; results are at most one or two frames old; objects not yet tested are drawn.
   Results are discarded when the camera moved more than 0.75 blocks or are older than 250 ms.
3. **Per-block-entity frustum culling** (Fabric only; NeoForge already has it) — allocation-free JOML
   `FrustumIntersection` test via the existing private `Frustum#cubeInFrustum`, with one block of margin.
4. **Particle occlusion culling** — section-graph test per particle (cached per section).
5. **Optional distance limits** for block entities, decorative entities and particles (visible trade-off,
   off in the Default profile).

Safety rules that apply to every culling path:

* Never hide the camera entity, the local player, entities with a glowing outline (drawn through walls),
  players and entities with a visible name tag (name tags are drawn through walls), leashed entities,
  or anything larger than 12 blocks.
* Never touch block entities whose renderer draws "off screen" (beacon beams, structure blocks, end gateways).
* Pause during an Iris shader pack's shadow pass (shadows of hidden entities stay correct).
* Camera inside an opaque block (spectator) → nothing is ray-culled.

### Considered and rejected

| Idea | Why not |
|---|---|
| Replace terrain rendering (Sodium-style) | Huge, version-fragile surface; Sodium already does it well and is compatible. |
| "Smart animations" (only animate visible sprites) | Needs sprite tracking inside chunk meshing; high risk of visible errors; Sodium covers it. |
| Pooling/reusing entity render states | Render states may be referenced by the submit queue and by other mods; risk of corrupted frames for a small GC gain on modern JVMs. |
| Fast-math replacements, "smooth FPS"/`glFinish` tricks | Not measurable or harmful on modern drivers. |
| Lowering simulation distance or chunk updates silently | Changes gameplay/visuals; vanilla already exposes these as options. |

## 6. Compatibility notes

* **Mixin footprint**: 9 injectors in 6 vanilla classes (`Camera`, `MouseHandler`, `Minecraft`,
  `EntityRenderDispatcher`, `BlockEntityRenderDispatcher`, `QuadParticleGroup`), one field added to
  `Entity`/`BlockEntity` (the culling result) and 3 accessors. Injectors are `@Inject` or MixinExtras
  (`@ModifyReturnValue`, `@ModifyExpressionValue`, `@WrapOperation`), which chain with other mods' injections
  instead of replacing code. No `@Overwrite`, no `@Redirect`. Every target method exists under the same name
  in 26.1.2, 26.2 and 26.3; where a signature differs (26.3 added a parameter to
  `EntityRenderDispatcher#shouldRender`) the injector matches by name and captures the needed argument by type.
* **Overloads**: NeoForge keeps vanilla's `BlockEntityRenderDispatcher#tryExtractRenderState` as a deprecated
  wrapper and adds an overload with a `Frustum` parameter. A name-only Mixin selector picks the first match —
  the wrapper the game never calls — which is how an early build silently culled no block entities on
  NeoForge (caught by the benchmark's per-optimization counters). The block-entity hook therefore lives in
  each loader module: Fabric selects the single vanilla method by name, NeoForge selects the overload whose
  descriptor contains `Frustum` (`name=/^tryExtractRenderState$/ desc=/Frustum;\)/`).
* **NeoForge**: `MouseHandler#turnPlayer` passes the smooth-camera flag into `CalculatePlayerTurnEvent`;
  FocusLens modifies the value before the event, so other mods' handlers still see and can change it.
* **Other zoom mods** (Zoomify, Ok Zoomer, Logical Zoom, …): detected; FocusLens' zoom key then starts unbound.
