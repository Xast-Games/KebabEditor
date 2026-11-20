package org.xast.kebab.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.xast.kebab.archetype.ArchetypeCatalog;
import org.xast.kebab.archetype.ParamType;
import org.xast.kebab.archetype.error.InvalidParamType;
import org.xast.kebab.archetype.error.UnknownParamException;
import org.xast.kebab.archetype.instance.ArchetypeInstance;
import org.xast.kebab.archetype.instance.NodeArchetype;
import org.xast.kebab.archetype.instance.UnresolvedArchetype;
import org.xast.kebab.math.Transform;
import org.xast.kebab.scene.model.NodeId;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.Maybe;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Detached copies of objects and their children, taken when they are copied, so
 * later edits to the originals do not change what is pasted. Every {@link #instantiate}
 * makes new objects with new ids; references between the copied objects are pointed
 * at the new copies, and references to anything else are kept.
 */
final class NodeTemplate {

    /**
     * @param parent    index of the parent entry, or -1 for a top-level object of the copy
     * @param world     the world transform at copy time, used to place top-level objects
     * @param oldParent the original's parent, which a duplicate or paste goes back under
     */
    record Entry(
        String id,
        String name,
        Transform local,
        Transform world,
        JsonNode rawJson,
        List<NodeArchetype> archetypes,
        int parent,
        Maybe<NodeId> oldParent
    ) {}

    /** A new object, and the entry it was made from. */
    record Made(SceneNode node, Entry entry) {}

    private final List<Entry> entries;

    private NodeTemplate(List<Entry> entries) {
        this.entries = entries;
    }

    /** @param roots top-level objects of the copy, none inside another; in the order they should keep */
    static NodeTemplate of(List<SceneNode> roots) {
        List<Entry> entries = new ArrayList<>();
        Map<SceneNode, Integer> indices = new IdentityHashMap<>();
        for (SceneNode root : roots) {
            collect(root, -1, root.getParent().map(SceneNode::getId), entries, indices);
        }
        return new NodeTemplate(List.copyOf(entries));
    }

    private static void collect(
        SceneNode node,
        int parent,
        Maybe<NodeId> oldParent,
        List<Entry> into,
        Map<SceneNode, Integer> indices
    ) {
        indices.put(node, into.size());
        into.add(new Entry(
            node.getId().value().toString(),
            node.getName(),
            node.getTransform().copy(),
            worldTransform(node),
            node.getRawJson().deepCopy(),
            node.getArchetypes().stream().map(archetype -> copy(archetype, Maybe.none())).toList(),
            parent,
            oldParent
        ));
        int self = indices.get(node);
        node.getChildren().forEach(child -> collect(child, self, Maybe.none(), into, indices));
    }

    int size() {
        return entries.size();
    }

    /**
     * New objects in the template's order, so parents come before their children.
     * They are not in any scene yet.
     *
     * @param catalog the open project's archetypes, which win over the copied definitions
     */
    List<Made> instantiate(Maybe<ArchetypeCatalog> catalog) {
        List<Made> made = new ArrayList<>();
        Map<String, String> newIds = new HashMap<>();
        for (Entry entry : entries) {
            SceneNode node = new SceneNode(entry.name());
            node.setTransform(entry.local().copy());
            node.setRawJson(entry.rawJson().deepCopy());
            newIds.put(entry.id(), node.getId().value().toString());
            made.add(new Made(node, entry));
        }
        for (Made item : made) {
            for (NodeArchetype archetype : item.entry().archetypes()) {
                NodeArchetype copied = copy(archetype, catalog);
                if (copied instanceof ArchetypeInstance instance) {
                    remapReferences(instance, newIds);
                }
                item.node().addArchetype(copied);
            }
        }
        return made;
    }

    static Transform worldTransform(SceneNode node) {
        Transform local = node.getTransform().copy();
        return node.getParent().mapOr(local, parent -> worldTransform(parent).mul(local));
    }

    private static NodeArchetype copy(NodeArchetype archetype, Maybe<ArchetypeCatalog> catalog) {
        return switch (archetype) {
            case UnresolvedArchetype unresolved ->
                new UnresolvedArchetype(unresolved.id(), unresolved.rawJson().deepCopy());
            case ArchetypeInstance instance -> {
                var definition = catalog
                    .andThen(c -> c.find(instance.archetypeId()))
                    .unwrapOr(instance.getDefinition());
                ArchetypeInstance copied = new ArchetypeInstance(definition);
                copied.restoreValues(instance.copyValues());
                yield copied;
            }
        };
    }

    private static void remapReferences(ArchetypeInstance instance, Map<String, String> newIds) {
        for (var param : instance.getDefinition().getParams().entrySet()) {
            if (param.getValue().type() != ParamType.NODE_REF) {
                continue;
            }
            try {
                if (instance.get(param.getKey()) instanceof Maybe<?> target
                    && target.isSome()
                    && newIds.containsKey(String.valueOf(target.unwrap()))) {
                    instance.set(param.getKey(), Maybe.some(newIds.get(String.valueOf(target.unwrap()))));
                }
            } catch (UnknownParamException | InvalidParamType e) {
                throw new IllegalStateException("Param vanished from its own definition", e);
            }
        }
    }
}
