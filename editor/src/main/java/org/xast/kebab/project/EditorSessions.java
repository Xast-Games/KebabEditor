package org.xast.kebab.project;

import java.util.List;

import org.xast.kebab.editor.Editor;
import org.xast.kebab.scene.io.GltfSceneReader;
import org.xast.kebab.scene.io.GltfSceneWriter;
import org.xast.kebab.scene.resolve.CompositeModelExtractor;
import org.xast.kebab.scene.resolve.GltfMeshExtractor;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class EditorSessions {

    private EditorSessions() {}

    public static Editor create() {
        return create(new NoRecentProjects());
    }

    public static Editor create(RecentProjects recent) {
        ObjectMapper mapper = new ObjectMapper();
        ProjectManager manager = new ProjectManager(
            recent,
            new FileProjectWriter(new GltfSceneWriter(mapper), mapper),
            new FileProjectLoader(
                new GltfSceneReader(mapper),
                mapper,
                new CompositeModelExtractor(List.of(new GltfMeshExtractor()))
            )
        );
        return new Editor(manager);
    }
}
