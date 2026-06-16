package com.kiro.agentbuilder.tools;

import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.tool.JsonSchema;
import com.kiro.agentbuilder.api.tool.RiskLevel;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolCall;
import com.kiro.agentbuilder.api.tool.ToolResult;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public class FileWriteTool implements Tool {

    private final Path baseDirectory;

    public FileWriteTool(Path baseDirectory) {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    @Override
    public String getName() {
        return "file_write";
    }

    @Override
    public String getDescription() {
        return "Write content to a file under the configured workspace root";
    }

    @Override
    public JsonSchema getParameterSchema() {
        return new JsonSchema(Map.of(
                "type", "object",
                "required", java.util.List.of("path", "content"),
                "properties", Map.of(
                        "path", Map.of("type", "string"),
                        "content", Map.of("type", "string"),
                        "charset", Map.of("type", "string"),
                        "append", Map.of("type", "boolean"))));
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.HIGH;
    }

    @Override
    public boolean isIdempotent() {
        return false;
    }

    @Override
    public Mono<ToolResult> execute(ToolCall call, ExecutionContext context) {
        return Mono.fromCallable(() -> {
                    Path target = resolvePath(call);
                    Files.createDirectories(target.getParent());
                    String charset = String.valueOf(call.arguments().getOrDefault("charset", Charset.defaultCharset().name()));
                    boolean append = Boolean.parseBoolean(String.valueOf(call.arguments().getOrDefault("append", false)));
                    OpenOption[] options = append
                            ? new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                            : new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
                    Files.writeString(target, String.valueOf(call.arguments().get("content")), Charset.forName(charset), options);
                    return ToolResult.success(call.callId(), Map.of("path", target.toString(), "bytesWritten", Files.size(target)));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> Mono.just(ToolResult.error(call.callId(), error.getMessage())));
    }

    private Path resolvePath(ToolCall call) throws IOException {
        Path target = baseDirectory.resolve(String.valueOf(call.arguments().get("path"))).normalize();
        if (!target.startsWith(baseDirectory)) {
            throw new IOException("Path escapes base directory");
        }
        return target;
    }
}
