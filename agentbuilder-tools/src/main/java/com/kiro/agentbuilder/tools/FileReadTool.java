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
import java.nio.file.Path;
import java.util.Map;

public class FileReadTool implements Tool {

    private final Path baseDirectory;

    public FileReadTool(Path baseDirectory) {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    @Override
    public String getName() {
        return "file_read";
    }

    @Override
    public String getDescription() {
        return "Read a file from the configured workspace root";
    }

    @Override
    public JsonSchema getParameterSchema() {
        return new JsonSchema(Map.of(
                "type", "object",
                "required", java.util.List.of("path"),
                "properties", Map.of(
                        "path", Map.of("type", "string"),
                        "charset", Map.of("type", "string"))));
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }

    @Override
    public boolean isIdempotent() {
        return true;
    }

    @Override
    public Mono<ToolResult> execute(ToolCall call, ExecutionContext context) {
        return Mono.fromCallable(() -> {
                    Path target = resolvePath(call);
                    String charset = String.valueOf(call.arguments().getOrDefault("charset", Charset.defaultCharset().name()));
                    String content = Files.readString(target, Charset.forName(charset));
                    return ToolResult.success(call.callId(), content);
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
