package org.xast.kebab.editor;

import java.util.Set;

import org.xast.kebab.archetype.ParamSpec;
import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.archetype.definition.ArchetypeDefinition;
import org.xast.kebab.scene.model.Greybox;
import org.xast.kebab.scene.model.SceneNode;

public final class InitialValues {

    private static final Set<String> BOX_ARCHETYPES = Set.of(BuiltinArchetypes.COLLIDER_BOX, BuiltinArchetypes.TRIGGER);

    private InitialValues() {}

    // A box volume added to a greybox starts out as the greybox's own box, since both use the node's matrix
    public static Object forParam(SceneNode node, ArchetypeDefinition definition, String name, ParamSpec spec) {
        if (name.equals("size") && BOX_ARCHETYPES.contains(definition.getId())) {
            return Greybox.of(node).<Object>map(Greybox::size).unwrapOr(spec.defaultValue());
        }
        return spec.defaultValue();
    }
}
