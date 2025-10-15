package com.lv.xhsmcp.login;

import com.lv.xhsmcp.browser.BrowserManager;

public class LoginCli {
  public static void main(String[] args){
    boolean headful = false; // 登录时默认可视
    for(String a: args){ if("--headless".equals(a)) headful=false; }

    try(var bm = new BrowserManager(headful)){
      var page = bm.context().newPage();
      page.navigate("https://www.xiaohongshu.com/login");
      System.out.println("请在弹出的浏览器中完成登录，完成后按 Enter 继续...");
      try{ System.in.read(); } catch(Exception ignore){}
      bm.persistCookies();
      page.close();
      System.out.println("登录 Cookie 已保存。");
    }
  }
}