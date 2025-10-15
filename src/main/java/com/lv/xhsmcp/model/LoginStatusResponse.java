package com.lv.xhsmcp.model;

public class LoginStatusResponse {
    public final boolean isLoggedIn;
    public final String username;

    public LoginStatusResponse(boolean isLoggedIn, String username) {
        this.isLoggedIn = isLoggedIn;
        this.username = username;
    }
}
