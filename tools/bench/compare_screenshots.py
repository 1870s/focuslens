#!/usr/bin/env python3
"""
Compares the OFF/ON screenshot pairs written by a run with -PdocScreenshots
(focuslens-view-<direction>-off.png / -on.png). Lossless culling must not change the picture.

Usage:
    python tools/bench/compare_screenshots.py <screenshots directory>

Needs Pillow (pip install pillow). Prints, per direction, the share of pixels whose colour differs by more
than a small tolerance and writes an amplified difference image next to the screenshots.
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops

TOLERANCE = 12  # per channel, absorbs dithering/precision noise


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    folder = Path(sys.argv[1])
    worst = 0.0
    for off in sorted(folder.glob("focuslens-view-*-off.png")):
        on = off.with_name(off.name.replace("-off.png", "-on.png"))
        if not on.exists():
            continue
        a = Image.open(off).convert("RGB")
        b = Image.open(on).convert("RGB")
        diff = ImageChops.difference(a, b)
        pixels = diff.getdata()
        changed = sum(1 for p in pixels if max(p) > TOLERANCE)
        share = changed / (a.width * a.height) * 100
        worst = max(worst, share)
        diff.point(lambda v: 255 if v > TOLERANCE else 0).save(off.with_name(off.name.replace("-off.png", "-diff.png")))
        print(f"{off.name.replace('focuslens-view-', '').replace('-off.png', ''):6s} {changed:8d} px differ ({share:.4f} %)")
    print(f"worst: {worst:.4f} %")


if __name__ == "__main__":
    main()
