+++
title = "Inspector"
description = "Edit the selected object in the side pane."
weight = 30
template = "docs/page.html"

[extra]
lead = 'The Inspector shows the transform and every archetype of the selected object.'
toc = true
top = false
+++

Select an object and the Inspector on the right shows:

- **Name.** Press Enter or leave the field to rename.
- **Transform.** Position, rotation in degrees and a uniform scale. The rotation fields show Euler angles; an untouched rotation is never rewritten.
- **Greybox**, for greybox boxes and spheres. Shape, size along X, Y and Z, colour and pattern. The size is what the scale gizmo stretches along one axis, so this is where you type exact dimensions. Sizes must be positive. The uniform scale above multiplies the size. Greyboxes are covered with [Kenney's prototype textures](https://kenney.nl/assets/prototype-textures), repeating once per metre: **Pattern** picks one of the 13 patterns, shown in grey, and **Color** picks which of Kenney's six colours it comes in (dark, light, green, orange, purple or red). New greyboxes use the orange "1x1 meter" wall, pattern 1.
- **One section per archetype.** Each parameter has its own editor. Values are applied on Enter, on leaving the field or when you change a control. A field that is still being edited is not overwritten by outside changes.
- **Add Archetype** and **Remove archetype.**

Edits made in the Inspector are undoable steps. The Inspector is disabled while a playtest runs, because the scene being played is read-only.
