#!/usr/bin/env python3
"""Scales prototype for the pillar outer layer.

Scales are small tiles with stagger x plus a tail. The tail is the block of the
ring groove row next to the tile center column, on the tail side. It stays wall
and caps the axis groove of the next course, so each scale reads as a shingle.

The tail points toward +axis (down) or -axis (up). The pillar random picks the
direction unless --tail sets it. tiles.py holds the layout, the surface paths,
the loop share rationing and the checks.

Written by Claude Code; conversation transcript:
llm/transcripts/transcript_2026-11-02_09-29_550f132f-0def-4354-8b7f-e97e27655707.md
"""

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tiles  # noqa: E402

TAILS = ("up", "down", "seeded")


def scale_params(tail="seeded", over=False, seed=1, pillar=0, surface=False, cut=False, share=1.0, box=None,
                 chance=0.75, orphans=False):
    """The parameters of one scales render."""
    return tiles.Params("small", "x", False, tail, over, seed, pillar, surface or cut, cut, share, box, chance,
                        orphans)


def check(chunks, seeds):
    """Assert the rules on both tail directions, paths over tails on and off, every mode, loop share and box."""
    runs = 0
    for seed in seeds:
        for tail in ("up", "down"):
            for over in (False, True):
                for surface, cut in ((False, False), (True, False), (True, True)):
                    if over and not surface:
                        continue
                    for share in (0.0, 0.5, 1.0):
                        for box in (None, tiles.DEFAULT_BOX):
                            P = scale_params(tail, over, seed, 0, surface, cut, share, box)
                            tiles.check_scene(P, chunks, one_tree=False, connected=False)
                            runs += 1
        print(f"seed {seed}: ok")
    print(f"{runs} scenes passed, each chunk also rendered alone")


def deliverables(chunks, scale, seed):
    """Render the image set of the README."""
    out = []
    for tail in TAILS:
        out.append(tiles.render(scale_params(tail, seed=seed), chunks, scale))
    for over in (False, True):
        for surface, cut in ((True, False), (True, True)):
            out.append(tiles.render(scale_params(over=over, seed=seed, surface=surface, cut=cut), chunks, scale))
    for share in (0.0, 0.5):
        out.append(tiles.render(scale_params(seed=seed, cut=True, share=share), chunks, scale))
    out.append(tiles.render(scale_params(seed=seed, box=tiles.DEFAULT_BOX), chunks, scale))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--tail", choices=TAILS, default="seeded", help="the tail direction")
    ap.add_argument("--over-tails", action="store_true", help="let a surface path run over the tail")
    tiles.common_args(ap)
    args = ap.parse_args()
    if args.check:
        check(args.chunks, range(1, args.check_seeds + 1))
        return
    if args.all:
        for p in deliverables(args.chunks, args.scale, args.seed):
            print(p)
        return
    share = 0.0 if args.no_loops else args.loops
    P = scale_params(args.tail, args.over_tails, args.seed, args.pillar, args.surface, args.cut, share, args.box,
                     args.chance, args.orphans)
    why = tiles.refusal(P)
    if why:
        ap.error(why)
    print(tiles.render(P, args.chunks, args.scale))


if __name__ == "__main__":
    main()
