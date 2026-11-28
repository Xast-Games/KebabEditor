#version 330 core

in vec3 vNormal;
in vec2 vUv;
in vec3 vGridPos;
in vec3 vLocalNormal;

uniform vec4 uColor;
uniform bool uLit;
uniform bool uWorldGrid;
uniform bool uTextured;
uniform sampler2D uTexture;
uniform vec3 uLightDir;

out vec4 fragColor;

// 1 on unit grid lines, 0 elsewhere. Projected along the dominant axis of the
// object-space normal, which is stable per face even when the object is rotated.
float worldGridLine() {
    vec3 a = abs(vLocalNormal);
    vec2 p = (a.x > a.y && a.x > a.z) ? vGridPos.yz
           : (a.y > a.z)              ? vGridPos.xz
           :                            vGridPos.xy;
    vec2 distanceToLine = abs(fract(p - 0.5) - 0.5) / fwidth(p);
    return 1.0 - min(min(distanceToLine.x, distanceToLine.y), 1.0);
}

// Texture coordinates in grid space, one repeat per world unit, projected along the face's
// dominant axis. Image rows run top-first, so v grows downwards; each face's u runs to its
// right as seen from outside, so labels on the texture read the right way round.
vec2 worldUv() {
    vec3 a = abs(vLocalNormal);
    vec3 n = sign(vLocalNormal);
    if (a.x > a.y && a.x > a.z) {
        return vec2(-n.x * vGridPos.z, -vGridPos.y);
    }
    if (a.y > a.z) {
        return vec2(vGridPos.x, n.y * vGridPos.z);
    }
    return vec2(n.z * vGridPos.x, -vGridPos.y);
}

void main() {
    vec4 base = uColor;
    if (uTextured) {
        base *= texture(uTexture, uWorldGrid ? worldUv() : vUv);
    }

    if (!uLit) {
        fragColor = base;
        return;
    }

    vec3 n = normalize(vNormal);
    if (!gl_FrontFacing) {
        n = -n;
    }

    // A texture of its own draws the grid already
    if (uWorldGrid && !uTextured) {
        base.rgb *= 1.0 - 0.2 * worldGridLine();
    }

    // Hemisphere ambient (sky above, ground bounce below) plus one directional light.
    vec3 ambient = mix(vec3(0.22, 0.21, 0.20), vec3(0.42, 0.45, 0.50), n.y * 0.5 + 0.5);
    float diffuse = max(dot(n, -uLightDir), 0.0);
    vec3 light = ambient + diffuse * vec3(0.78, 0.76, 0.72);

    fragColor = vec4(base.rgb * light, base.a);
}
