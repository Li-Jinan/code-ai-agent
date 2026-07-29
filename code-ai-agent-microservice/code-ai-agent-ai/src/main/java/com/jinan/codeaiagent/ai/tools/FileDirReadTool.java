package com.jinan.codeaiagent.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.jinan.codeaiagent.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Set;

/**
 * 文件目录读取工具
 * 使用 Hutool 简化文件操作
 */
@Slf4j
@Component
public class FileDirReadTool extends BaseTool {

    private static final int MAX_DEPTH = 8;

    private static final int MAX_ENTRIES = 300;

    private static final int MAX_OUTPUT_CHARS = 16_000;

    /**
     * 需要忽略的文件和目录
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules", ".git", "dist", "build", ".ds_store",
            ".env", "target", ".mvn", ".idea", ".vscode", "coverage"
    );

    /**
     * 需要忽略的文件扩展名
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log", ".tmp", ".cache", ".lock"
    );

    @Tool("读取目录结构，获取指定目录下的所有文件和子目录信息")
    public String readDir(
            @P("目录的相对路径，为空则读取整个项目结构")
            String relativeDirPath,
            @ToolMemoryId Long appId
    ) {
        try {
            String projectDirName = "vue_project_" + appId;
            Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName)
                    .toAbsolutePath()
                    .normalize();
            Path targetDir = resolveTargetDirectory(projectRoot, relativeDirPath);
            if (targetDir == null) {
                return "错误：只能读取当前项目内的相对目录 - " + relativeDirPath;
            }
            if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
                return "错误：目录不存在或不是目录 - " + relativeDirPath;
            }
            StringBuilder structure = new StringBuilder();
            structure.append("项目目录结构:\n");
            int[] entryCount = {0};
            boolean[] truncated = {false};
            Files.walkFileTree(targetDir, Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(targetDir) && shouldIgnore(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!dir.equals(targetDir)
                            && !appendEntry(structure, targetDir, dir, true, entryCount)) {
                        truncated[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (shouldIgnore(file.getFileName().toString())) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!appendEntry(structure, targetDir, file, false, entryCount)) {
                        truncated[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (truncated[0]) {
                structure.append("... 目录内容过多，已省略其余条目\n");
            }
            return structure.toString();
        } catch (Exception e) {
            String errorMessage = "读取目录结构失败: " + relativeDirPath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    private Path resolveTargetDirectory(Path projectRoot, String relativeDirPath) {
        if (StrUtil.isBlank(relativeDirPath)
                || ".".equals(relativeDirPath)
                || "/".equals(relativeDirPath)) {
            return projectRoot;
        }
        Path requestedPath = Paths.get(relativeDirPath);
        if (requestedPath.isAbsolute()) {
            return null;
        }
        Path targetDir = projectRoot.resolve(requestedPath).normalize();
        return targetDir.startsWith(projectRoot) ? targetDir : null;
    }

    private boolean appendEntry(StringBuilder structure, Path root, Path entry, boolean directory,
                                int[] entryCount) {
        if (entryCount[0] >= MAX_ENTRIES) {
            return false;
        }
        Path relativePath = root.relativize(entry);
        int depth = Math.max(0, relativePath.getNameCount() - 1);
        String displayPath = relativePath.toString().replace(entry.getFileSystem().getSeparator(), "/");
        String line = "  ".repeat(depth) + displayPath + (directory ? "/" : "") + "\n";
        if (structure.length() + line.length() > MAX_OUTPUT_CHARS) {
            return false;
        }
        structure.append(line);
        entryCount[0]++;
        return true;
    }

    /**
     * 判断是否应该忽略该文件或目录
     */
    private boolean shouldIgnore(String fileName) {
        String normalizedName = fileName.toLowerCase(Locale.ROOT);
        // 检查是否在忽略名称列表中
        if (IGNORED_NAMES.contains(normalizedName)) {
            return true;
        }

        // 检查文件扩展名
        return IGNORED_EXTENSIONS.stream().anyMatch(normalizedName::endsWith);
    }

    @Override
    public String getToolName() {
        return "readDir";
    }

    @Override
    public String getDisplayName() {
        return "读取目录";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeDirPath = arguments.getStr("relativeDirPath");
        if (StrUtil.isEmpty(relativeDirPath)) {
            relativeDirPath = "根目录";
        }
        return String.format("[工具调用] %s %s", getDisplayName(), relativeDirPath);
    }
}
