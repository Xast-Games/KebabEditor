package org.xast.kebab.embed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xast.kebab.model.TextureSlot;
import org.xast.kebab.scene.error.ModelLoadException;
import org.xast.kebab.scene.resolve.ModelResult.ResolvedModel;

class ModelPreviewTest {

    @TempDir
    Path directory;

    @Test
    void aModelFileLoadsWithoutAProject() throws Exception {
        Path file = Files.writeString(directory.resolve("triangle.gltf"), triangle());

        ResolvedModel model = ModelPreview.load(file);

        assertEquals(1, model.parts().size());
        assertEquals(file, model.path());
    }

    @Test
    void texturesAnywhereInTheProjectAreFound() throws Exception {
        Files.writeString(directory.resolve("kebab-project.json"),
            "{\"version\":1,\"assetRoot\":\".\",\"assetFolders\":[],\"scenes\":[]}");
        Path textures = Files.createDirectories(directory.resolve("textures"));
        Files.write(textures.resolve("wood.png"), new byte[] { 1 });
        Path models = Files.createDirectories(directory.resolve("models/bed"));
        Path file = Files.writeString(models.resolve("bed.gltf"), triangle("../../textures/wood.png"));

        ResolvedModel model = ModelPreview.load(file);

        assertInstanceOf(TextureSlot.Ref.class, model.parts().get(0).material().baseTexture());
    }

    @Test
    void withoutAProjectTexturesStayNextToTheModel() throws Exception {
        Path models = Files.createDirectories(directory.resolve("models"));
        Files.write(directory.resolve("wood.png"), new byte[] { 1 });
        Path file = Files.writeString(models.resolve("bed.gltf"), triangle("../wood.png"));

        ResolvedModel model = ModelPreview.load(file);

        assertInstanceOf(TextureSlot.Missing.class, model.parts().get(0).material().baseTexture());
    }

    @Test
    void aMissingFileIsAnError() {
        assertThrows(ModelLoadException.class, () -> ModelPreview.load(directory.resolve("missing.glb")));
    }

    private static String triangle() {
        return triangle(null);
    }

    private static String triangle(String texture) {
        ByteBuffer buffer = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[] { 0, 0, 0, 1, 0, 0, 0, 1, 0 }) {
            buffer.putFloat(value);
        }
        String data = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(buffer.array());

        return "{\"asset\":{\"version\":\"2.0\"},"
            + "\"scene\":0,\"scenes\":[{\"nodes\":[0]}],\"nodes\":[{\"mesh\":0}],"
            + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}" + (texture == null ? "" : ",\"material\":0") + "}]}],"
            + (texture == null ? "" : "\"materials\":[{\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":0}}}],"
                + "\"textures\":[{\"source\":0}],\"images\":[{\"uri\":\"" + texture + "\"}],")
            + "\"buffers\":[{\"uri\":\"" + data + "\",\"byteLength\":36}],"
            + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36}],"
            + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"}]}";
    }
}
