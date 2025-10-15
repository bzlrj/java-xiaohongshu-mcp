package com.lv.xhsmcp.service;

import com.lv.xhsmcp.browser.BrowserManager;
import com.lv.xhsmcp.model.CommentList;
import com.lv.xhsmcp.model.FeedDetail;
import com.lv.xhsmcp.model.FeedDetailResponse;
import com.lv.xhsmcp.util.Json;
import com.lv.xhsmcp.xhs.BizErrorCode;
import com.lv.xhsmcp.xhs.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

@Slf4j
public class FeedDetailService {
    /* ===================== 常量 ===================== */
    private static final int PAGE_DEFAULT_TIMEOUT_MS = 60_000;
    private static final int NAV_TIMEOUT_MS          = 60_000;
    private static final int STABLE_SLEEP_MS         = 1_000;

    /** 读取初始状态（优先 __INITIAL_STATE__，兜底 __XHS_DATA__） */
    private static final String JS_READ_STATE = """
        () => {
          try {
            if (window.__INITIAL_STATE__) return JSON.stringify(window.__INITIAL_STATE__);
            if (window.__XHS_DATA__)     return JSON.stringify(window.__XHS_DATA__);
          } catch (e) { /* ignore */ }
          return "";
        }
        """;

    private final BrowserManager bm;
    public FeedDetailService(BrowserManager bm){ this.bm = bm; }
    /**
     * 获取笔记详情与评论摘要
     *
     * @param feedId    必填
     * @param xsecToken 可选
     */
    public Result<FeedDetailResponse> feedDetail(String feedId, String xsecToken) {
        // 参数校验（契约问题 -> IllegalArgumentException）
        if (StringUtils.isBlank(feedId)) {
            throw new IllegalArgumentException("feedId must not be blank");
        }

        try (Page page = bm.context().newPage()) {
            page.setDefaultTimeout(PAGE_DEFAULT_TIMEOUT_MS);

            // 1) 构建并导航
            String url = makeFeedDetailURL(feedId, xsecToken);
            log.info("Navigate to feed detail. feedId={}, url={}", feedId, url);
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(NAV_TIMEOUT_MS));
            page.waitForTimeout(STABLE_SLEEP_MS);

            // 2) 登录/人机检测（业务可预期错误）
            if (isLoginOrCaptcha(page.url())) {
                log.warn("Redirected to login/captcha. url={}", page.url());
                return Result.fail(BizErrorCode.AUTH_REQUIRED, "跳转至登录或人机验证页");
            }

            // 3) 读取初始状态 JSON
            String json = (String) page.evaluate(JS_READ_STATE);
            if (StringUtils.isBlank(json)) {
                log.warn("Initial state not found.");
                return Result.fail(BizErrorCode.DATA_NOT_FOUND, "__INITIAL_STATE__ 数据不存在");
            }

            // 4) 解析并定位 noteDetailMap[feedId]
            JsonNode root;
            try {
                root = Json.M.readTree(json);
            } catch (Exception parseEx) {
                log.warn("Parse initial state failed. err={}", parseEx.getMessage());
                return Result.fail(BizErrorCode.DATA_PARSE_ERROR, "初始数据解析失败");
            }

            JsonNode detailMap = root.path("note").path("noteDetailMap");
            if (detailMap.isMissingNode() || !detailMap.isObject()) {
                log.warn("noteDetailMap not found or not object.");
                return Result.fail(BizErrorCode.DATA_NOT_FOUND, "noteDetailMap 不存在或类型异常");
            }

            JsonNode entry = detailMap.path(feedId);
            if (entry.isMissingNode() || entry.isNull() || entry.isEmpty()) {
                // 兜底：有些页面 key 不是 feedId，取第一个
                Iterator<String> it = detailMap.fieldNames();
                if (it.hasNext()) {
                    String firstKey = it.next();
                    log.info("feedId key not found, fallback to first key: {}", firstKey);
                    entry = detailMap.path(firstKey);
                }
            }
            if (entry.isMissingNode() || entry.isNull() || entry.isEmpty()) {
                log.warn("Feed entry not found in noteDetailMap. feedId={}", feedId);
                return Result.fail(BizErrorCode.DATA_NOT_FOUND, "未在 noteDetailMap 中找到目标笔记");
            }

            // 5) 提取 note / comments
            // 若你已有强类型 FeedDetailResponse，可换成 mapper.convertValue(entry, FeedDetailResponse.class)
            FeedDetailResponse raw = Json.M.convertValue(entry, new TypeReference<>() {});
            FeedDetail feedDetail     = raw.getNote();
            CommentList comments = raw.getComments();
            log.info("Feed detail parsed. feedId={}, hasNote={}, hasComments={}",
                    feedId, feedDetail != null , comments != null);
            return Result.ok(raw);

        } catch (PlaywrightException e) {
            log.error("Feed detail system error. feedId={}, err={}", feedId, e.getMessage(), e);
            throw new RuntimeException("获取笔记详情发生系统异常", e);
        }
    }

    /* ================= 辅助方法 ================= */

    private static boolean isLoginOrCaptcha(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        String u = url.toLowerCase();
        return u.contains("/login") || u.contains("captcha") || u.contains("passport");
    }

    /** URL 构造：允许无 token 导航（部分页面可直接打开） */
    private static String makeFeedDetailURL(String feedId, String xsecToken) {
        String base = "https://www.xiaohongshu.com/explore/" + feedId;
        if (StringUtils.isBlank(xsecToken)) {
            return base;
        }
        String token = URLEncoder.encode(xsecToken, StandardCharsets.UTF_8);
        return base + "?xsec_token=" + token + "&xsec_source=pc_feed";
    }
}

