package com.lv.xhsmcp.model;

public final class LoginCheck {
        private final boolean loggedIn;
        private final String reason; // 未登录原因（可选）

        private LoginCheck(boolean loggedIn, String reason) {
            this.loggedIn = loggedIn;
            this.reason = reason;
        }
        public static LoginCheck loggedIn() { return new LoginCheck(true, null); }
        public static LoginCheck notLoggedIn(String reason) { return new LoginCheck(false, reason); }

        public boolean isLoggedIn() { return loggedIn; }
        public String getReason() { return reason; }
        @Override public String toString() {
            return "LoginCheck{loggedIn=" + loggedIn + ", reason='" + reason + "'}";
        }
    }