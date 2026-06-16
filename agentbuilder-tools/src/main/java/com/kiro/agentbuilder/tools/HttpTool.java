package com.kiro.agentbuilder.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiro.agentbuilder.api.model.ExecutionContext;
import com.kiro.agentbuilder.api.tool.JsonSchema;
import com.kiro.agentbuilder.api.tool.RiskLevel;
import com.kiro.agentbuilder.api.tool.Tool;
import com.kiro.agentbuilder.api.tool.ToolCall;
import com.kiro.agentbuilder.api.tool.ToolResult;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpTool implements Tool {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpTool() {
        this(new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(20))
                .build());
    }

    public HttpTool(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public String getName() {
        return "http_request";
    }

    @Override
    public String getDescription() {
        return "Execute outbound HTTP GET or POST requests";
    }

    @Override
    public JsonSchema getParameterSchema() {
        return new JsonSchema(Map.of(
                "type", "object",
                "required", java.util.List.of("url"),
                "properties", Map.of(
                        "url", Map.of("type", "string"),
                        "method", Map.of("type", "string"),
                        "headers", Map.of("type", "object"),
                        "body", Map.of("type", "string"),
                        "timeoutMs", Map.of("type", "integer"))));
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.MEDIUM;
    }

    @Override
    public boolean isIdempotent() {
        return false;
    }

    @Override
    public Mono<ToolResult> execute(ToolCall call, ExecutionContext context) {
        return Mono.fromCallable(() -> executeRequest(call))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> Mono.just(ToolResult.error(call.callId(), error.getMessage())));
    }

    private ToolResult executeRequest(ToolCall call) throws IOException {
        String url = String.valueOf(call.arguments().get("url"));
        String method = String.valueOf(call.arguments().getOrDefault("method", "GET")).toUpperCase();
        Request.Builder builder = new Request.Builder().url(url);

        Object headers = call.arguments().get("headers");
        if (headers instanceof Map<?, ?> headerMap) {
            for (Map.Entry<?, ?> entry : headerMap.entrySet()) {
                builder.addHeader(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }

        if ("POST".equals(method)) {
            String body = String.valueOf(call.arguments().getOrDefault("body", ""));
            builder.post(RequestBody.create(body, JSON));
        } else {
            builder.get();
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("statusCode", response.code());
            output.put("successful", response.isSuccessful());
            output.put("body", response.body() == null ? "" : response.body().string());
            output.put("headers", response.headers().toMultimap());
            return new ToolResult(call.callId(), output, !response.isSuccessful(), Map.of(
                    "raw", objectMapper.writeValueAsString(output)));
        }
    }
}
