package org.xast.kebab.scene.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.xast.kebab.archetype.ArchetypeCatalog;
import org.xast.kebab.archetype.definition.ArchetypeRegistry;
import org.xast.kebab.math.Vec3;
import org.xast.kebab.scene.io.GltfSceneReader;
import org.xast.kebab.scene.io.GltfSceneWriter;

import com.fasterxml.jackson.databind.ObjectMapper;

class GreyboxTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void roundTripsThroughNodeExtras() {
        SceneNode node = new SceneNode("platform");
        Greybox written = new Greybox(GreyboxShape.BOX, new Vec3(4, 0.5f, 2), GreyboxColor.PURPLE, 11);

        written.writeTo(node);
        Greybox read = Greybox.of(node).unwrap();

        assertEquals(GreyboxShape.BOX, read.shape());
        assertTrue(new Vec3(4, 0.5f, 2).approxEquals(read.size(), 1e-6f));
        assertEquals(GreyboxColor.PURPLE, read.color());
        assertEquals(11, read.pattern());
    }

    @Test
    void survivesSaveAndLoad() throws Exception {
        Scene scene = new Scene();
        SceneNode node = new SceneNode("ball");
        Greybox.unit(GreyboxShape.SPHERE).writeTo(node);
        scene.add(node);

        String json = MAPPER.writeValueAsString(new GltfSceneWriter().write(scene));
        Scene loaded = new GltfSceneReader().read(MAPPER.readTree(json), new ArchetypeCatalog(new ArchetypeRegistry()));

        assertEquals(GreyboxShape.SPHERE, Greybox.of(loaded.getRootNodes().getFirst()).unwrap().shape());
    }

    @Test
    void nodesWithoutGreyboxOrWithMalformedDataHaveNone() throws Exception {
        assertTrue(Greybox.of(new SceneNode("plain")).isNone());

        SceneNode unknownShape = node("{ \"extras\": { \"kebab\": { \"greybox\": { \"shape\": \"cone\" } } } }");
        SceneNode badSize = node("{ \"extras\": { \"kebab\": { \"greybox\": { \"shape\": \"box\", \"size\": [1, 2] } } } }");

        assertTrue(Greybox.of(unknownShape).isNone());
        assertTrue(Greybox.of(badSize).isNone());
        for (String bad : List.of("\"pattern\": 0", "\"pattern\": 14", "\"pattern\": 1.5", "\"pattern\": \"1\"",
            "\"color\": \"pink\"", "\"color\": [1, 0]", "\"color\": 3")) {
            assertTrue(Greybox.of(node(greybox(bad))).isNone(), bad);
        }
    }

    @Test
    void greyboxesSavedWithAFreeColourGetTheClosestTextureColour() throws Exception {
        assertEquals(GreyboxColor.LIGHT, Greybox.of(node(greybox("\"color\": [0.66, 0.67, 0.69, 1]"))).unwrap().color());
        assertEquals(GreyboxColor.RED, Greybox.of(node(greybox("\"color\": [0.9, 0.1, 0.2, 1]"))).unwrap().color());
        assertEquals(GreyboxColor.DARK, Greybox.of(node(greybox("\"color\": [0.1, 0.1, 0.1, 1]"))).unwrap().color());
        assertEquals(Greybox.DEFAULT_PATTERN, Greybox.of(node(greybox("\"color\": [0.1, 0.1, 0.1, 1]"))).unwrap().pattern());
    }

    @Test
    void everyPatternExistsInEveryColour() {
        for (GreyboxColor color : GreyboxColor.values()) {
            for (int pattern = 1; pattern <= Greybox.PATTERNS; pattern++) {
                String path = "/" + Greybox.textureResource(color, pattern);
                assertNotNull(Greybox.class.getResource(path), path);
            }
        }
        assertEquals("textures/prototype/orange/pattern_01.png", Greybox.unit(GreyboxShape.BOX).textureResource());
    }

    @Test
    void aPatternOutsideTheSetIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Greybox.unit(GreyboxShape.BOX).withPattern(0));
        assertThrows(IllegalArgumentException.class, () -> Greybox.unit(GreyboxShape.BOX).withPattern(14));
    }

    @Test
    void missingSizeAndColorFallBackToDefaults() throws Exception {
        Greybox greybox = Greybox.of(node("{ \"extras\": { \"kebab\": { \"greybox\": { \"shape\": \"box\" } } } }")).unwrap();

        assertTrue(Vec3.one().approxEquals(greybox.size(), 0f));
        assertEquals(GreyboxColor.ORANGE, greybox.color());
        assertEquals(1, greybox.pattern());
    }

    private static String greybox(String field) {
        return "{ \"extras\": { \"kebab\": { \"greybox\": { \"shape\": \"box\", " + field + " } } } }";
    }

    private static SceneNode node(String rawJson) throws Exception {
        SceneNode node = new SceneNode("n");
        node.setRawJson(MAPPER.readTree(rawJson));
        return node;
    }
}
