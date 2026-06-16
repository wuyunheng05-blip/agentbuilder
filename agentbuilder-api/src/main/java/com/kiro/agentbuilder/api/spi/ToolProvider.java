package com.kiro.agentbuilder.api.spi;

import com.kiro.agentbuilder.api.tool.Tool;

import java.util.List;

public interface ToolProvider {

    List<Tool> getTools();
}
