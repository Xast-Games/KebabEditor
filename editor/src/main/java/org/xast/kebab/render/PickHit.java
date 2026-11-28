package org.xast.kebab.render;

import org.xast.kebab.math.Vec3;
import org.xast.kebab.scene.model.SceneNode;
import org.xast.kebab.utils.Maybe;

public record PickHit(
    Maybe<SceneNode> node, 
    float distance, 
    Vec3 point, 
    Vec3 normal
) {}
