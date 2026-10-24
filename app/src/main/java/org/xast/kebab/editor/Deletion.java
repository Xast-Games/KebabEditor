package org.xast.kebab.editor;

import java.util.List;

import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.scene.model.SceneNode;

public record Deletion(List<SceneNode> roots, int nodeCount, List<NodeReference> references) {

    public record NodeReference(SceneNode owner, ArchetypeInstance instance, String param) {}

    public int childCount() {
        return nodeCount - roots.size();
    }

    public boolean needsConfirmation() {
        return childCount() > 0 || !references.isEmpty();
    }
}
