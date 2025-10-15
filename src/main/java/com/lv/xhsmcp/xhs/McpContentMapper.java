package com.lv.xhsmcp.xhs;

import java.util.List;
import java.util.Map;

public final class McpContentMapper {
    // 简易 Data URL 解析：返回 [mimeType, base64]；否则 null
    public static String[] tryParseDataUrl(String s) {
        if (s == null) return null;
        // data:image/png;base64,xxxx
        int comma = s.indexOf(',');
        if (s.startsWith("data:") && comma > 0) {
            String header = s.substring(5, comma); // image/png;base64
            int semi = header.indexOf(';');
            String mime = semi >= 0 ? header.substring(0, semi) : header;
            String base64 = s.substring(comma + 1);
            return new String[]{mime, base64};
        }
        return null;
    }

    public static List<McpContent> fromResult(Result<?> r) {
        // 业务错误：优先给出文本 + json 明细
        if (!r.isSuccess()) {
            return List.of(
                McpContent.text("❌ " + r.getCode() + " - " + r.getMessage()),
                McpContent.json(Map.of(
                    "success", r.isSuccess(),
                    "code", String.valueOf(r.getCode()),
                    "message", r.getMessage(),
                    "data", r.getData()
                ))
            );
        }

        Object data = r.getData();
        if (data == null) {
            return List.of(McpContent.text("✅ OK"));
        }

        // 1) String：可能是文本、DataURL、URL
        if (data instanceof String s) {
            // Data URL → image
            String[] p = tryParseDataUrl(s);
            if (p != null) return List.of(McpContent.image(p[1], p[0]));

            // URL 猜测为资源（可按需细化检测）
            if (s.startsWith("http://") || s.startsWith("https://")) {
                // 若需要更精确，可通过后缀/HEAD 判定 mimeType
                return List.of(McpContent.resource(s, "image/*"));
            }

            // 其它字符串当作文本
            return List.of(McpContent.text(s));
        }

        // 2) byte[]：当作图片（你也可以额外传入 mimeType）
        if (data instanceof byte[] bytes) {
            String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
            return List.of(McpContent.image(base64, "image/png"));
        }

        // 3) 集合 / POJO：交给客户端以 json 渲染（更通用）
        return List.of(McpContent.json(data));
    }
}