package com.lv.xhsmcp.xhs;// McpContent.java（最小实现，按需扩展）
import java.util.Map;

public interface McpContent {
    Map<String, Object> toMap();

    static McpContent text(String text) {
        return () -> Map.of("type", "text", "text", text == null ? "" : text);
    }

    /** 注意：data 必须是纯 Base64（不要带 data:image/png;base64, 前缀） */
    static McpContent image(String base64, String mimeType) {
        return () -> Map.of("type", "image", "data", base64, "mimeType", mimeType);
    }

    static McpContent json(Object obj) {
        return () -> Map.of("type", "json", "json", obj);
    }

    static McpContent resource(String uri, String mimeType) {
        return () -> Map.of("type", "resource", "uri", uri, "mimeType", mimeType);
    }
}
