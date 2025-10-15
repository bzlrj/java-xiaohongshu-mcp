package com.lv.xhsmcp.xhs;

public enum BizErrorCode {
        OK(0, "OK"),
        AUTH_REQUIRED(1001001, "需要登录或通过人机验证"),
        STATUS_CHECK_FAILED(1001002, "检查登录状态失败"),
        DATA_NOT_FOUND(1001003, "数据不存在"),
        DATA_PARSE_ERROR(1001004, "初始数据解析失败"),
        LIST_FEEDS_FAILED(1001005, "获取Feeds列表失败"),
        GET_FEED_DETAIL_FAILED(1001006, "获取Feed详情失败"),
        SEARCH_FEEDS_FAILED(1001007, "搜索Feeds失败"),
        GET_USER_PROFILE_FAILED(1003007, "获取用户主页失败"),
        POST_COMMENT_FAILED(1002010, "发表评论失败"),
        ELEMENT_NOT_FOUND(1002001, "页面元素未找到"),
        FILE_NOT_FOUND(1002002, "文件不存在"),
        DOWNLOAD_FAILED(1002003, "下载失败"),
        PUBLISH_FAILED(1002003, "发布失败"),
        IO_ERROR(1002004, "IO错误");

        private final int code;
        private final String defaultMessage;

        BizErrorCode(int code, String defaultMessage) {
            this.code = code;
            this.defaultMessage = defaultMessage;
        }
        public int getCode() { return code; }
        public String getDefaultMessage() { return defaultMessage; }
    }