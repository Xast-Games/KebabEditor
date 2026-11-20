+++
title = "Projects and assets"
description = "Project files, the Asset Explorer and the asset scope."
weight = 40
template = "docs/page.html"

[extra]
lead = 'A project is a kebab-project.json that registers glTF scenes, with one archetypes.json shared by all of them.'
toc = true
top = false
+++

## Project layout

```text
my-game/
├── kebab-project.json  the project file: asset scope and registered scenes
├── archetypes.json     your archetype definitions, shared by every scene and prefab
├── scenes/
│   └── level.gltf      scene: nodes, transforms and archetype instances
├── assets/             project assets
└── models/             imported models (created when importing)
```

**File > Create new project...** asks for a location and a scenes folder (`scenes` by default). It writes `kebab-project.json` and `archetypes.json` into the location and creates the scenes folder. Leave the scenes folder empty to keep scenes next to `kebab-project.json`. A new project has no scenes yet.

**File > New scene...** (`Ctrl+Shift+N`) asks for a folder, relative to the asset root, and a name. It writes an empty scene there, registers it in `kebab-project.json` and opens it. The folder starts as the project's scenes folder, which you can change in **Project > Asset folders...**. Leave it empty to put the scene in the asset root. A folder outside the asset folders is added to them, so the scenes and prefabs you put next to the new scene show up in the explorer. If the current scene has unsaved changes, you are asked first.

**File > Open project** (`Ctrl+O`) opens a `kebab-project.json` at the first scene it registers, or with no scene if it registers none. A glTF scene file still opens directly, which is how projects without a `kebab-project.json` are opened.

Every scene and prefab of the project uses the same `archetypes.json`, however deep its folder is. A project whose `archetypes.json` still sits next to its scene keeps working: the editor reads that file and the next save writes the project's own. Moving the asset root takes `archetypes.json` along with `kebab-project.json`, unless the new root already has one.

Saving is atomic, so a failed write cannot corrupt a level. Imported external models are copied into `models/` under the asset root, so scenes in any folder share them.

## Asset Explorer

The Asset Explorer sits beside Logs. Select a folder in the tree, double-click a folder to enter it, and double-click an asset or press Enter to open it.

- A glTF or GLB file opens as a read-only **model preview**, unless it is registered as a scene.
- A registered scene opens in the editor in place of the current one. If the current scene has unsaved changes, you choose **Save and open**, **Discard and open** or **Cancel**.
- The explorer refreshes when the window regains focus. **File > Refresh assets** (`Ctrl+F5`) refreshes it on demand.

## The asset scope

`kebab-project.json` decides what the explorer shows:

```json
{
  "version": 1,
  "assetRoot": ".",
  "assetFolders": ["assets", "models", "scenes"],
  "scenes": ["scenes/level.gltf"],
  "scenesFolder": "scenes"
}
```

Use **Project > Asset folders...** to change the asset root, the folders, the scenes folder and the registered scenes. The root can be any folder that contains the open scene, for example a `Project/` folder above `Scenes/` and `Models/`. Moving the root moves the config file and reloads the scene.

Paths must stay inside the project, and symbolic links are left out of the browser. A project without a config uses the scene's folder as its root.
