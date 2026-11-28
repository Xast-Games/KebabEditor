package org.xast.kebab.embed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
    void aMissingFileIsAnError() {
        assertThrows(ModelLoadException.class, () -> ModelPreview.load(directory.resolve("missing.glb")));
    }

    private static String triangle() {
        ByteBuffer buffer = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[] { 0, 0, 0, 1, 0, 0, 0, 1, 0 }) {
            buffer.putFloat(value);
        }
        String data = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(buffer.array());

        return "{\"asset\":{\"version\":\"2.0\"},"
            + "\"scene\":0,\"scenes\":[{\"nodes\":[0]}],\"nodes\":[{\"mesh\":0}],"
            + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}}]}],"
            + "\"buffers\":[{\"uri\":\"" + data + "\",\"byteLength\":36}],"
            + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36}],"
            + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"}]}";
    }
}
