package com.lv;

import com.lv.xhsmcp.browser.BrowserManager;
import com.lv.xhsmcp.service.XhsService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpApplication {

    public static void main(String[] args) {

        SpringApplication.run(McpApplication.class, args);
    }
    @Bean
    public ToolCallbackProvider tools(XhsService xhsService) {
        return MethodToolCallbackProvider.builder().toolObjects(xhsService).build();
    }
}
