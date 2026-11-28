package org.xast.kebab.behavior.instance;

import static java.util.Map.entry;

import java.util.Map;

import org.xast.kebab.archetype.builtin.BuiltinArchetypes;
import org.xast.kebab.behavior.ArchetypeBehavior;
import org.xast.kebab.utils.Maybe;

public final class BuiltinBehaviors {

    private static final Map<String, ArchetypeBehavior> ALL = Map.ofEntries(
        entry(BuiltinArchetypes.PREFAB_REF,       new PrefabRefBehavior()),
        entry(BuiltinArchetypes.MODEL_REF,        new ModelRefBehavior()),
        entry(BuiltinArchetypes.COLLIDER_BOX,     new ColliderBoxBehavior()),
        entry(BuiltinArchetypes.COLLIDER_SPHERE,  new ColliderSphereBehavior()),
        entry(BuiltinArchetypes.TRIGGER,          new TriggerBehavior()),
        entry(BuiltinArchetypes.NO_VOLUME,        new NoVolumeBehavior()),
        entry(BuiltinArchetypes.POINT_LIGHT,      new PointLightBehavior()),
        entry(BuiltinArchetypes.DIR_LIGHT,        new DirLightBehavior()),
        entry(BuiltinArchetypes.SPOT_LIGHT,       new SpotLightBehavior()),
        entry(BuiltinArchetypes.SNAP_DISTANCE,    new SnapDistanceBehavior()),
        entry(BuiltinArchetypes.PLAYER_START,     new PlayerStartBehavior())
    );

    private BuiltinBehaviors() {}

    public static Maybe<ArchetypeBehavior> behaviorOf(String archetypeId) {
        return Maybe.ofNullable(ALL.get(archetypeId));
    }
}
