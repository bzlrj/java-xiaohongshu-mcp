package com.lv.xhsmcp.browser;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import java.util.*;

public class BrowserManager implements AutoCloseable {
  private final CookieStore store = new CookieStore();
  private final boolean headless;
  private Playwright pw;
  private Browser browser;
  private BrowserContext ctx;
  public BrowserManager() { this(true); }               // 默认无头
  public BrowserManager(boolean headless) { this.headless = headless; }

  public synchronized BrowserContext context(){
    if(ctx!=null) return ctx;
    pw = Playwright.create();
    browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
    ctx = browser.newContext();
    ctx = browser.newContext(new Browser.NewContextOptions()
            .setViewportSize(1280, 800)
            .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"));
    // 降低默认等待，避免卡死
    ctx.setDefaultTimeout(6000);
    ctx.setDefaultNavigationTimeout(8000);
    // restore cookies
    var cookies = store.read();
    if(!cookies.isEmpty()) ctx.addCookies(cookies.stream().map(this::toCookie).toList());
    return ctx;
  }

  private Cookie toCookie(Map<String,Object> m){
    Cookie c = new Cookie(String.valueOf(m.get("name")),String.valueOf(m.get("value")));
    c.setDomain(String.valueOf(m.get("domain")));
    c.setPath(String.valueOf(m.getOrDefault("path","/")));
    Object exp = m.get("expires");
    if(exp instanceof Number n) c.setExpires(n.longValue());
    c.setHttpOnly(Boolean.TRUE.equals(m.get("httpOnly")));
    c.setSecure(Boolean.TRUE.equals(m.get("secure")));
    return c;
  }

  public synchronized void persistCookies(){
    try{
      var cookies = context().cookies();
      List<Map<String,Object>> list = new ArrayList<>();
      for(var c: cookies){
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("name", c.name); m.put("value", c.value);
        m.put("domain", c.domain); m.put("path", c.path);
        m.put("expires", c.expires); m.put("httpOnly", c.httpOnly);
        m.put("secure", c.secure);
        list.add(m);
      }
      store.write(list);
    }catch(Exception e){ throw new RuntimeException(e); }
  }

  @Override public void close(){
    try{ if(ctx!=null) ctx.close(); } catch(Exception ignore){} try{ if(browser!=null) browser.close(); } catch(Exception ignore){} try{ if(pw!=null) pw.close(); } catch(Exception ignore){} }
}