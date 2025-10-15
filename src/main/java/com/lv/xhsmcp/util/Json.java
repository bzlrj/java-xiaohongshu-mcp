package com.lv.xhsmcp.util;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Json {
  public static final ObjectMapper M = new ObjectMapper();
  static {
    // 关键：忽略 JSON 中未在 POJO 声明的字段
//    M.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }
}