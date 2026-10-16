package com.jinan.codeaiagent.ai.tools;

import com.jinan.codeaiagent.constant.AppConstant;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileDirReadToolTest {

    @Test
    void shouldTreatSlashAsProjectRootAndSkipGeneratedDependencies() throws Exception {
        long appId = System.nanoTime();
        Path projectRoot = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId);
        try {
            Files.createDirectories(projectRoot.resolve("src/pages"));
            Files.createDirectories(projectRoot.resolve("node_modules/example"));
            Files.createDirectories(projectRoot.resolve("dist/assets"));
            Files.writeString(projectRoot.resolve("src/pages/Home.vue"), "<template>home</template>");
            Files.writeString(projectRoot.resolve("node_modules/example/index.js"), "dependency");
            Files.writeString(projectRoot.resolve("dist/assets/index.js"), "bundle");

            String result = new FileDirReadTool().readDir("/", appId);

            assertTrue(result.contains("src/"));
            assertTrue(result.contains("src/pages/Home.vue"));
            assertFalse(result.contains("node_modules"));
            assertFalse(result.contains("dist"));
        } finally {
            deleteRecursively(projectRoot);
        }
    }

    @Test
    void shouldRejectPathsOutsideProject() {
        String result = new FileDirReadTool().readDir("/etc", System.nanoTime());

        assertTrue(result.contains("只能读取当前项目内"));
    }

    private void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path item : paths.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }
}
