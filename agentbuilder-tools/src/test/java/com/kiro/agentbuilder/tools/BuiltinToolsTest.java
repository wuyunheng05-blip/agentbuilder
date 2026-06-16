package com.kiro.agentbuilder.tools;

import com.sun.net.httpserver.HttpServer;
import com.kiro.agentbuilder.api.tool.ToolCall;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinToolsTest {

    @Test
    void shouldReadAndWriteWithinWorkspaceRoot() throws Exception {
        Path tempDir = Files.createTempDirectory("agentbuilder-tools");
        FileWriteTool writeTool = new FileWriteTool(tempDir);
        FileReadTool readTool = new FileReadTool(tempDir);

        var writeResult = writeTool.execute(new ToolCall("file_write", Map.of("path", "notes/output.txt", "content", "hello"), "write-1"), null).block();
        var readResult = readTool.execute(new ToolCall("file_read", Map.of("path", "notes/output.txt"), "read-1"), null).block();

        assertFalse(writeResult.error());
        assertEquals("hello", readResult.output());
    }

    @Test
    void shouldRejectPathTraversal() {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        FileReadTool readTool = new FileReadTool(tempDir);
        var result = readTool.execute(new ToolCall("file_read", Map.of("path", "../outside.txt"), "read-2"), null).block();
        assertTrue(result.error());
    }

    @Test
    void shouldExecuteHttpRequest() throws Exception {
        HttpServer server = createServer();
        try {
            HttpTool tool = new HttpTool();
            int port = server.getAddress().getPort();
            var result = tool.execute(new ToolCall("http_request", Map.of("url", "http://127.0.0.1:" + port + "/ping"), "http-1"), null).block();
            assertFalse(result.error());
            Map<?, ?> payload = (Map<?, ?>) result.output();
            assertEquals(200, payload.get("statusCode"));
            assertEquals("pong", payload.get("body"));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer createServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ping", exchange -> {
            byte[] bytes = "pong".getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        });
        server.start();
        return server;
    }
}
