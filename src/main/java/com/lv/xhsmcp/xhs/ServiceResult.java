package com.lv.xhsmcp.xhs;

// dto/ServiceResult.java
import java.io.Serializable;

public record ServiceResult<T>(
        String code,      // "0" = 成功，其它 = 错误码
        String message,   // 用户友好提示
        T data,           // 返回的数据
        Object details    // 额外调试信息
) implements Serializable {

  public static <T> ServiceResult<T> ok(T data, String message) {
    return new ServiceResult<>("0", message, data, null);
  }

  public static <T> ServiceResult<T> err(String code, String message) {
    return new ServiceResult<>(code, message, null, null);
  }

  public static <T> ServiceResult<T> err(String code, String message, Object details) {
    return new ServiceResult<>(code, message, null, details);
  }

  // 快速判断是否成功
  public boolean isOk() {
    return "0".equals(this.code);
  }
}

