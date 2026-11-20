+++
title = "Viewport and objects"
description = "Move the camera, select, transform and place objects."
weight = 10
template = "docs/page.html"

[extra]
lead = 'The viewport is where you look at and edit the scene.'
toc = true
top = false
+++

## Camera

| Action | How |
|---|---|
| Look around | Drag with the mouse |
| Move | `W` `A` `S` `D` |
| Up and down | `Space` and `Ctrl` |
| Fly speed, 1 to 500 m/s | Mouse wheel |
| Fly three times faster | Hold `Shift` |
| Frame the selection | `F` |

The fly speed starts at 10 m/s and shows briefly in the corner of the viewport when you change it. `F` jumps the camera to the selected objects and their children, keeping the direction it looks in.

## Outliner

The Outliner above the Inspector lists every object in the scene as a tree. Selecting there selects in the viewport and the other way round. Double-click an object, or press `Enter` or `F`, to fly the camera to it. Right-click an object to frame it or open its prefab.

## Opening prefabs

Select an object with a **Prefab Reference** and choose **Edit > Open prefab** (`Ctrl+Shift+O`) to open the scene it points at in the editor. This is how you go from a world map into one of its locations. The scene must be inside the project's asset root. If the current scene has unsaved changes, you are asked to save or discard them first.

## Selecting and transforming

Click an object or its icon to select it. The gizmo has three modes: `1` move, `2` rotate and `3` scale. Press `Ctrl+Shift+G` to snap to the grid. The grid step comes from a **Snap Distance** object in the scene, or defaults to 0.5.

## Adding objects

| Object | Shortcut |
|---|---|
| Greybox box | `Ctrl+B` |
| Greybox sphere | `Ctrl+Shift+B` |
| Import a glTF model | `Ctrl+I` |
| Empty, player start, lights | **Edit** menu |

## Placement ray

`Ctrl+Shift+P` starts a placement ray that spawns objects on surfaces. The mouse wheel changes the distance, `Alt` ignores surfaces and `Esc` leaves.

## Icons

Every archetype on an object is drawn as a billboard icon. Several icons are arranged in a pyramid. Empty objects get an icon too, so they stay selectable. Toggle them in **View > Show icons**.

Greyboxes with the **No Volume** archetype are hidden. Turn on **View > Render No Volume** to draw them as wireframes, which can then be clicked.

## Panels

Press `N` to hide or show the Outliner and Inspector, and `T` to hide or show the bottom panel with the Asset Explorer and Logs. The viewport takes the freed space, and showing a panel again puts its divider back where it was. The same check boxes are in **View > Show side panel** and **Show bottom panel**. You can also drag a divider to bring a hidden panel back.

## Undo and redo

`Ctrl+Z` and `Ctrl+Y` undo and redo scene edits while the viewport has focus. Undo is disabled during a playtest.
