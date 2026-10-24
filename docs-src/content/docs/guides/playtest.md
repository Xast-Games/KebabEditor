+++
title = "Playtest"
description = "Run the level and read the results."
weight = 50
template = "docs/page.html"

[extra]
lead = 'A playtest runs a capsule actor through the level with simple physics.'
toc = true
top = false
+++

## Starting

Press `F5`, use **Playtest > Run / stop level**, or use the run button. `Esc` stops it. A playtest needs something solid to stand on and a **Player Start**. If several exist, the selected one is used, otherwise the first.

During a playtest the scene is read-only: editing, undo and the Inspector are disabled.

## Controls

`W` `A` `S` `D` or the arrow keys run, `Space` jumps and dragging looks around.

## What is solid

- Greyboxes are solid, except a greybox that carries a **Trigger**.
- Box and sphere colliders are solid.
- Triggers are not solid. They report the actor entering them, and a trigger the actor is inside lights up green.

## The actor

The Player Start archetype sets the actor: `run_speed`, `jump_height`, `gravity`, `radius` and `height`. The height must be at least twice the radius.

## What it reports

Jumps, drops of more than one metre and trigger entries are written to the Logs panel. An actor that falls far below the level respawns at the start.

## Path rays

After each run its path stays in the viewport, with the newest brightest and the last eight kept. Ground and air segments have different colors, and jumps and trigger hits are marked. Use **Playtest > Show path rays**, **Clear path rays** and **Path thickness**.

## Reachability

Press `F7` or use **Playtest > Show reachability** to see which platforms the actor can get to, without running a playtest. Every solid counts as a platform, simplified to its bounding box. The check follows the editor while it is on: moving, adding or deleting objects and changing the selection update it a moment later.

With one object or nothing selected, it checks the whole level from the Player Start. The platforms are outlined in the viewport:

- Blue: the platform under the Player Start.
- Green: reachable, and the actor can get back.
- Yellow: one-way. The actor can get there but not back, so a player who goes there is stuck.
- Red: unreachable.

With two or more objects selected (`Shift`+click), it shows the way with the fewest jumps from the first selected to the last, drawn as a line through each take-off and landing. If there is no way, the target is red and everything the actor can get to is green.

The results are written to the Logs panel, such as how many platforms are reachable, the farthest one and the route with its jump count. A new line appears only when the outcome changes.

**Playtest > Jump limit** counts only platforms the actor gets to within that many jumps. Choose 1 to 5 or **Custom...**, or **No limit**. Walking onto a touching platform is free, and each jump or drop counts as one.

The check is an estimate: jumps are worked out from the Player Start's settings at full run speed, and walls and ceilings in the way are not checked. It shows the best a player can do, so confirm a tricky jump with a real playtest.
