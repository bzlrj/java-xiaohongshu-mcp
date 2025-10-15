package com.lv.xhsmcp.xhs;// Rpc.java
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public final class Rpc {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        // 不要在写 JSON 时意外关闭底层流
        MAPPER.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
    }

    private final Writer writer;
    private final ReentrantLock lock = new ReentrantLock();

    /** 使用 stdout 作为传输（最常见的 MCP stdio 模式） */
    public static Rpc stdio() {
        return new Rpc(new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8)));
    }

    /** 也可以自己传 OutputStream，比如 socket.getOutputStream() */
    public static Rpc from(OutputStream out) {
        return new Rpc(new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
    }

    private Rpc(Writer writer) {
        this.writer = writer;
    }

    /** 关键实现：发送 JSON-RPC 2.0 的 yield（流式内容块） */
    public void sendYield(Object id, List<McpContent> contents) {
        Map<String, Object> msg = Map.of(
            "jsonrpc", "2.0",
            "method", "yield",
            "params", Map.of(
                "id", id,
                "content", contents.stream().map(McpContent::toMap).toList()
            )
        );
        writeJsonLine(msg); // NDJSON：每条一行，便于读取边界
    }

    /** 便捷方法：发送一条文本 yield */
    public void sendYieldText(Object id, String text) {
        sendYield(id, List.of(McpContent.text(text)));
    }

    /** 便捷方法：发送一张图片（纯Base64 + mimeType） */
    public void sendYieldImage(Object id, String base64, String mimeType) {
        sendYield(id, List.of(McpContent.image(base64, mimeType)));
    }

    /** 发送最终 JSON-RPC result（一次性结果） */
    public void sendResult(Object id, Object result) {
        writeJsonLine(rpcResult(id, result));
    }

    /** 构造 JSON-RPC result 对象（若你想先构造返回给上层再写出） */
    public Map<String, Object> rpcResult(Object id, Object result) {
        return Map.of("jsonrpc", "2.0", "id", id, "result", result);
    }

    /** 写入一行 JSON，并 flush；用锁保证多线程下消息不交错 */
    private void writeJsonLine(Object message) {
        lock.lock();
        try {
            MAPPER.writeValue(writer, message);
            writer.write('\n');
            writer.flush(); // MCP 客户端通常期望尽快收到
        } catch (IOException e) {
            // 这里按你的需要处理：日志/上报/关闭
            throw new UncheckedIOException(e);
        } finally {
            lock.unlock();
        }
    }
}
