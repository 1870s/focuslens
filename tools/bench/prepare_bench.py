#!/usr/bin/env python3
"""
Prepares a reproducible FocusLens benchmark setup for a development client.

1. Downloads the official dedicated server for the given Minecraft version from Mojang
   (checksum verified) and lets it generate a world with a fixed seed.
2. Copies that world into <loader>/runs/client/saves/FocusLensBench.
3. Writes an options.txt with benchmark-friendly settings (VSync off, unlimited FPS, fixed render
   distance, no pause on focus loss, onboarding screens skipped).

Then run, for example:
    ./gradlew :fabric:runClient -PbenchWorld=FocusLensBench
which joins the world, waits 15 s, runs the OFF/ON comparison and closes the game. Results are written
to <loader>/runs/client/focuslens/benchmarks/.

Usage:
    python tools/bench/prepare_bench.py --mc 26.3 --java "<path to java 25>" [--loaders fabric neoforge]
"""
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import math
import tempfile
import threading
import time
import urllib.request
from pathlib import Path

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
WORLD_NAME = "FocusLensBench"
SEED = "focuslens-benchmark"

SERVER_PROPERTIES = f"""level-seed={SEED}
level-name=world
gamemode=creative
difficulty=normal
spawn-monsters=true
generate-structures=true
view-distance=16
simulation-distance=8
online-mode=false
enable-rcon=false
motd=FocusLens benchmark world
"""

# Settings that keep the measurement from being capped or interrupted.
CLIENT_OPTIONS = {
    "enableVsync": "false",
    "maxFps": "260",               # 260 = "Unlimited"
    "inactivityFpsLimit": '"minimized"',
    "renderDistance": "16",
    "simulationDistance": "8",
    "pauseOnLostFocus": "false",
    "onboardAccessibility": "false",
    "skipMultiplayerWarning": "true",
    "tutorialStep": "none",
    "narrator": "0",
    "fullscreen": "false",
    "renderClouds": '"false"',     # clouds drift in real time and would add noise to screenshot comparisons
}


def fetch_json(url):
    with urllib.request.urlopen(url) as response:
        return json.load(response)


def download_server(mc_version, target):
    manifest = fetch_json(MANIFEST)
    entry = next((v for v in manifest["versions"] if v["id"] == mc_version), None)
    if entry is None:
        sys.exit(f"Minecraft {mc_version} not found in Mojang's version manifest")
    server = fetch_json(entry["url"])["downloads"]["server"]
    print(f"Downloading server {mc_version} ({server['size'] // 1024} KiB)")
    with urllib.request.urlopen(server["url"]) as response:
        data = response.read()
    if hashlib.sha1(data).hexdigest() != server["sha1"]:
        sys.exit("Server jar checksum mismatch")
    target.write_bytes(data)


MOB_TAG = "{NoAI:1b,PersistenceRequired:1b,Silent:1b}"
MOBS = ["zombie", "skeleton", "villager", "pig"]


def structure(tag, x, z, roofed):
    """
    A 32x21x32 stone structure standing on the terrain at (x, z), built relative to a marker entity
    placed on the surface first. Inside: 900 chests on the floor, 900 signs on a stone shelf and 120
    motionless mobs. With a roof it is sealed (vanilla's section graph proves the inside invisible);
    without a roof it is a walled courtyard that the section graph can reach from above, while the walls
    still hide everything from a camera standing outside.
    """
    at = f"execute at @e[type=minecraft:marker,tag={tag},limit=1] run "
    cmds = [
        f"execute positioned {x} 320 {z} positioned over motion_blocking run summon minecraft:marker ~ ~ ~ {{Tags:[\"{tag}\"]}}",
        at + "fill ~-15 ~ ~-15 ~16 ~20 ~16 minecraft:stone",
        at + ("fill ~-14 ~1 ~-14 ~15 ~19 ~15 minecraft:air" if roofed else "fill ~-14 ~1 ~-14 ~15 ~20 ~15 minecraft:air"),
        at + "fill ~-14 ~1 ~-14 ~15 ~1 ~15 minecraft:chest",
        at + "fill ~-14 ~3 ~-14 ~15 ~3 ~15 minecraft:stone",
        at + "fill ~-14 ~4 ~-14 ~15 ~4 ~15 minecraft:oak_sign",
    ]
    for i in range(120):
        dx = -13 + (i % 15) * 2
        dz = -13 + (i // 15) * 3
        cmds.append(at + f"summon minecraft:{MOBS[i % 4]} ~{dx} ~6 ~{dz} {MOB_TAG}")
    return cmds


def scene_commands(with_structures=True):
    """
    A deterministic test scene around spawn (0, 0):
      * the world is frozen (no day/night, weather or mob spawning) and the 576 chunks around spawn are
        generated up front, so nothing generates during the measurement;
      * east of spawn a sealed stone bunker, west of spawn an open-topped walled courtyard (see structure());
        together 1800 chests, 1800 signs and 240 motionless mobs, none of them visible from spawn;
      * 40 motionless cows stand in a ring around spawn and must stay visible.
    """
    setup = [
        "gamerule advance_time false",
        "gamerule advance_weather false",
        "gamerule spawn_mobs false",
        "gamerule respawn_radius 0",
        "time set 6000",
        "weather clear",
        "setworldspawn 0 100 0",
        # /forceload accepts at most 256 chunks per command: four quadrants of 12x12 chunks.
        "forceload add -192 -192 -1 -1",
        "forceload add 0 -192 191 -1",
        "forceload add -192 0 -1 191",
        "forceload add 0 0 191 191",
    ]
    scene = structure("bunker", 42, 0, roofed=True) + structure("courtyard", -42, 0, roofed=False) if with_structures else []
    for i in range(40):
        angle = i * 9
        x = round(10 * math.cos(math.radians(angle)))
        z = round(10 * math.sin(math.radians(angle)))
        scene.append(f"execute positioned {x} 320 {z} positioned over motion_blocking run summon minecraft:cow ~ ~ ~ {MOB_TAG}")
    scene.append("kill @e[type=minecraft:marker]")
    finish = ["forceload remove all", "save-all flush", "stop"]
    return setup, scene, finish


def generate_world(java, server_jar, work, scene_name):
    (work / "eula.txt").write_text("eula=true\n")
    (work / "server.properties").write_text(SERVER_PROPERTIES)
    process = subprocess.Popen([java, "-Xmx3G", "-jar", str(server_jar), "--nogui"], cwd=work,
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    done = threading.Event()

    def pump():
        for line in process.stdout:
            sys.stdout.write("  [server] " + line)
            if "Done (" in line:
                done.set()

    reader = threading.Thread(target=pump, daemon=True)
    reader.start()
    if not done.wait(900):
        process.kill()
        sys.exit("Server did not start in 15 minutes")

    def send(commands, pause=0.05):
        for command in commands:
            process.stdin.write(command + "\n")
            process.stdin.flush()
            time.sleep(pause)

    if scene_name != "none":
        setup, scene, finish = scene_commands(scene_name == "structures")
        send(setup)
        print("Generating 576 chunks around spawn (about 2 minutes)...")
        time.sleep(120)
        send(scene)
        time.sleep(5)
        send(finish, pause=2)
    else:
        send(["stop"])
    if process.wait(600) != 0:
        sys.exit("Server exited with an error")
    reader.join(5)
    return work / "world"


def write_options(run_dir):
    options = run_dir / "options.txt"
    existing = {}
    if options.exists():
        for line in options.read_text(encoding="utf-8").splitlines():
            if ":" in line:
                key, value = line.split(":", 1)
                existing[key] = value
    existing.update(CLIENT_OPTIONS)
    options.write_text("".join(f"{k}:{v}\n" for k, v in existing.items()), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--mc", required=True, help="Minecraft version, e.g. 26.3")
    parser.add_argument("--java", default="java", help="Java 25 executable used to run the server")
    parser.add_argument("--loaders", nargs="+", default=["fabric", "neoforge"])
    parser.add_argument("--scene", choices=["structures", "empty", "none"], default="structures",
                        help="structures: bunker + courtyard + cows (default); empty: same frozen, pre-generated world "
                             "with only the visible cows, to measure overhead; none: fresh world, nothing prepared")
    parser.add_argument("--world-name", default=WORLD_NAME)
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[2]
    with tempfile.TemporaryDirectory(prefix="focuslens-bench-") as tmp:
        work = Path(tmp)
        server_jar = work / "server.jar"
        download_server(args.mc, server_jar)
        world = generate_world(args.java, server_jar, work, args.scene)
        for loader in args.loaders:
            run_dir = root / loader / "runs" / "client"
            saves = run_dir / "saves" / args.world_name
            if saves.exists():
                shutil.rmtree(saves)
            shutil.copytree(world, saves)
            write_options(run_dir)
            print(f"Prepared {saves}")


if __name__ == "__main__":
    main()
