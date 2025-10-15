package com.lv.xhsmcp.model;

public class LoginQrcodeResponse {
    public final String timeout;   // e.g. "0s" or "4m0s"
    public final boolean isLoggedIn;
    public final ImageObject imageObject;       // qrcode img src (可能为 base64 或 URL)

    public LoginQrcodeResponse(String timeout, boolean isLoggedIn, ImageObject imageObject) {
      this.timeout = timeout;
      this.isLoggedIn = isLoggedIn;
      this.imageObject = imageObject;
    }
  }