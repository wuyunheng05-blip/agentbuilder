package com.kiro.agentbuilder.tools;

import com.kiro.agentbuilder.api.spi.ToolProvider;
import com.kiro.agentbuilder.api.tool.Tool;

import java.nio.file.Path;
import java.util.List;

public class BuiltinToolProvider implements ToolProvider {

    @Override
    public List<Tool> getTools() {
        Path workspaceRoot = Path.of(System.getProperty("user.dir"));
        return List.of(
                new HttpTool(),
                new FileReadTool(workspaceRoot),
                new FileWriteTool(workspaceRoot));
    }
}
