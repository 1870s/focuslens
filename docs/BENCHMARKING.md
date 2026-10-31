# Benchmarking

FocusLens contains its own benchmark so that every performance claim can be checked on your machine.

## Method

* **Fixed camera path.** The camera makes exactly one full horizontal turn per pass at constant speed with
  the pitch locked; mouse input is ignored. Every pass sees the same scene in the same order, independent
  of the frame rate.
* **Same session, alternating passes.** *Compare OFF vs ON* runs four passes: OFF, ON, OFF, ON. In OFF passes
  every FocusLens optimization is suspended (the hooks stay installed but return vanilla results); the zoom
  is unaffected. Alternating cancels slow drift (chunk loading, thermal throttling, background tasks).
* **Settle time.** Each pass starts with 4 unrecorded seconds after switching.
* **No throttling.** Vanilla caps FPS at 30 after 60 s without input ("AFK"); the benchmark counts as input.
  It warns in chat when VSync or an FPS limit would cap the result.
* **Frame time** = time between the starts of two consecutive frames (`Minecraft#renderFrame`), measured
  with `System.nanoTime()`.

### Reported metrics

| Metric | Definition |
|---|---|
| Average FPS | frames / total time |
| 1% low FPS | 1000 / mean frame time of the slowest 1% of frames |
| 0.1% low FPS | same for the slowest 0.1% (at least one frame) |
| P50 / P95 / P99 frame time | nearest-rank percentiles in ms |
| Max frame time, std. deviation | in ms |
| Culled per frame | average number of entities / block entities / particles hidden by each optimization |

Output: chat summary plus `focuslens/benchmarks/benchmark-<timestamp>.json` (environment: Minecraft, loader,
Java, OS, CPU, GPU and driver, window size, render/simulation distance, VSync, FPS limit, relevant mods;
all FocusLens settings; per-pass statistics) and `...-frametimes.csv` (every frame).

## Running it in game

1. Disable VSync and set *Max Framerate* to *Unlimited* (otherwise results are capped).
2. Stand somewhere representative (a base with many chests, a mob farm, a village, a cave system) and
   do not touch the mouse or keyboard.
3. *FocusLens settings → General → Compare OFF vs ON*, or press the *Benchmark* key.

"Without the mod" vs "with the mod": the OFF passes run with FocusLens installed but suspended, which is the
fairest comparison inside one session (same world state, same JIT warm-up). For a strict "not installed"
baseline, run *Measure current settings* in a separate session without FocusLens installed — that baseline
can only be measured with another tool (for example PresentMon or CapFrameX) because the benchmark is part
of FocusLens.

## Reproducible development setup

```bash
python tools/bench/prepare_bench.py --mc 26.3 --java "<path to Java 25>"
```

```bash
./gradlew :fabric:runClient -PbenchWorld=FocusLensBench
```

The script downloads Mojang's official dedicated server (checksum verified), generates a world with the fixed
seed `focuslens-benchmark`, prepares a deterministic scene, copies the world into `fabric/runs/client/saves`
and `neoforge/runs/client/saves`, and writes benchmark-friendly client options (VSync off, unlimited FPS,
render distance 16, clouds off, no pause on focus loss). The run joins the world, waits 15 s, runs the
comparison at 1600×900 and closes the game. Use `-PbenchMode=current` for a single pass with the current
settings.

Scenes (`--scene`):

| Scene | Contents | Purpose |
|---|---|---|
| `structures` (default) | 576 pre-generated chunks, time/weather/mob spawning frozen. East of spawn a sealed 32×21×32 stone bunker, west of spawn an open-topped walled courtyard of the same size. Each holds 900 chests, 900 signs and 120 motionless (NoAI) mobs. 40 motionless cows stand in a ring around spawn. | A base-like scene with a lot of hidden content. |
| `empty` | Same world preparation, only the 40 visible cows. | Overhead: FocusLens' cost when there is nothing to cull. |
| `none` | Fresh world, nothing prepared. | Quick smoke test. |

`-PdocScreenshots` instead of a benchmark saves screenshots of the settings, the overlay, and the same four
views with optimizations OFF and ON; `python tools/bench/compare_screenshots.py <run dir>/screenshots`
reports how many pixels differ between OFF and ON. `-PmixinAudit` verifies that every FocusLens mixin is applied
and closes the game (the log ends with `Mixin check result: 8 of 8 target classes OK`).

## Interpreting results

* Gains depend entirely on the scene. An open field with few entities will show ≈0% difference — there is
  nothing to cull. Scenes with many entities or block entities that are hidden behind terrain or walls
  (underground bases, storage rooms, villages behind hills, mob farms) are where culling helps.
* If the *Culled per frame* counters are near zero, the scene has nothing for FocusLens to remove and any FPS
  difference is noise.
* Differences below about ±2% between OFF and ON are within run-to-run noise on most systems.
