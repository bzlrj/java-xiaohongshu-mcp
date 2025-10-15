package com.lv.xhsmcp.service;

import com.lv.xhsmcp.browser.BrowserManager;
import com.lv.xhsmcp.model.Feed;
import com.lv.xhsmcp.model.FeedResponse;
import com.lv.xhsmcp.util.Json;
import com.lv.xhsmcp.xhs.BizErrorCode;
import com.lv.xhsmcp.xhs.Result;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class FeedsService {

    /* ===================== 常量 ===================== */
    private static final String URL_HOME = "https://www.xiaohongshu.com";
    private static final int PAGE_DEFAULT_TIMEOUT_MS = 60_000;
    private static final int NAV_TIMEOUT_MS = 60_000;
    private static final int STABLE_SLEEP_MS = 1_000;
    private static final int DEFAULT_LIMIT = 10;

    /**
     * 读取初始 state 的 JS 片段（优先 __INITIAL_STATE__，兜底 __XHS_DATA__）
     */
    private static final String JS_READ_STATE = """
            () => {
              try {
                if (window.__INITIAL_STATE__) return JSON.stringify(window.__INITIAL_STATE__);
                if (window.__XHS_DATA__)     return JSON.stringify(window.__XHS_DATA__);
              } catch (e) { /* ignore */ }
              return "";
            }
            """;

    private final BrowserManager browserManager;

    public FeedsService(BrowserManager browserManager) {
        this.browserManager = browserManager;
    }

    /**
     * 获取首页信息流（最多 limit 条；limit==0 采用默认值）
     * <p>
     * 错误处理：
     * - 参数错误：limit < 0 -> IllegalArgumentException
     * - 业务可预期：跳登录、人机验证；初始数据缺失；解析失败 -> Result.fail(...)
     * - 系统异常（Playwright 等）：抛 RuntimeException
     */
    public Result<List<Feed>> listFeeds(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0");
        }
        int max = (limit == 0 ? DEFAULT_LIMIT : limit);

        try (Page page = browserManager.context().newPage()) {
            page.setDefaultTimeout(PAGE_DEFAULT_TIMEOUT_MS);

            // 1) 进入首页并轻量等待（避免网络空闲卡死）
            log.info("Navigate to home. url={}", URL_HOME);
            page.navigate(URL_HOME, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(NAV_TIMEOUT_MS));

            // 2) 登录/人机检测（业务可预期）
            String currentUrl = page.url();
            if (isLoginOrCaptchaUrl(currentUrl)) {
                log.warn("Redirected to login/captcha. url={}", currentUrl);
                return Result.fail(BizErrorCode.AUTH_REQUIRED, "跳转至登录或人机验证页");
            }

            page.waitForTimeout(STABLE_SLEEP_MS);

            // 3) 读取初始状态
            String json = (String) page.evaluate(JS_READ_STATE);
            if (StringUtils.isBlank(json)) {
                log.warn("Initial state not found on page.");
                return Result.fail(BizErrorCode.DATA_NOT_FOUND, "__INITIAL_STATE__ 数据不存在");
            }

            // 4) 解析并提取 feeds
            FeedResponse state;
            try {
                state = Json.M.readValue(json, FeedResponse.class);
            } catch (Exception parseEx) {
                log.warn("Parse initial state failed. err={}", parseEx.getMessage());
                return Result.fail(BizErrorCode.DATA_PARSE_ERROR, "初始数据解析失败");
            }

            if (state.getFeed() == null
                    || state.getFeed().getFeeds() == null
                    || state.getFeed().getFeeds().getValue() == null) {
                log.warn("Feeds value missing in state.");
                return Result.fail(BizErrorCode.DATA_NOT_FOUND, "feeds 值在初始数据中缺失");
            }

            List<Feed> all = state.getFeed().getFeeds().getValue();
            int n = Math.min(max, all.size());
            List<Feed> top = all.stream().limit(n).toList();

            log.info("List feeds success. requested={}, returned={}", max, top.size());
            return Result.ok(top);

        } catch (PlaywrightException e) {
            log.error("List feeds system error. err={}", e.getMessage(), e);
            throw new RuntimeException("拉取信息流发生系统异常", e);
        }
    }

    /* ===================== 私有工具 ===================== */

    private static boolean isLoginOrCaptchaUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        String u = url.toLowerCase();
        return u.contains("/login") || u.contains("captcha") || u.contains("passport");
    }
}

