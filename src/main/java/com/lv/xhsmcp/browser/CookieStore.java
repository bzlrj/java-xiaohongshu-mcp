package com.lv.xhsmcp.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.*;
import java.util.*;

public class CookieStore {
  private static final ObjectMapper M = new ObjectMapper();
  private final Path path;
  public CookieStore(){
    String env = System.getenv("XHS_COOKIES_PATH");
    if(env==null||env.isBlank()) env = System.getProperty("user.home")+"/.xhs/cookies.json";
    this.path = Paths.get(env).toAbsolutePath();
  }
  public List<Map<String,Object>> read(){
    try{
      if(!Files.exists(path)) return List.of();
      return M.readValue(Files.readString(path), List.class);
    }catch(Exception e){ return List.of(); }
  }
  public void write(List<Map<String,Object>> cookies){
    try{
      Files.createDirectories(path.getParent());
      M.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), cookies);
    }catch(Exception e){ throw new RuntimeException(e); }
  }
  public Path path(){ return path; }
}