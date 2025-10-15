package com.lv.xhsmcp.service;

import com.lv.xhsmcp.browser.BrowserManager;
import com.lv.xhsmcp.model.Feed;
import com.lv.xhsmcp.model.SearchFeedResponse;
import com.lv.xhsmcp.util.Json;
import com.lv.xhsmcp.xhs.BizErrorCode;
import com.lv.xhsmcp.xhs.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class SearchService {
    /* ===================== 常量（避免魔法数） ===================== */
    private static final String URL_SEARCH_BASE = "https://www.xiaohongshu.com/search_result";
    private static final String API_SEARCH_NOTES = "/api/sns/web/v1/search/notes";

    private static final int PAGE_DEFAULT_TIMEOUT_MS = 60_000;
    private static final int NAV_TIMEOUT_MS = 60_000;
    private static final int DEFAULT_LIMIT = 10;

    private static final Duration TOTAL_BUDGET = Duration.ofSeconds(25); // 总时限
    private static final int MAX_SCROLLS = 80;
    private static final int WHEEL_STEP_1 = 1200;
    private static final int WHEEL_STEP_2 = 1600;
    private static final int WAIT_AFTER_SCROLL_MS_1 = 200;
    private static final int WAIT_AFTER_SCROLL_MS_2 = 300;
    private static final int WAIT_AFTER_BOTTOM_MS = 900;
    private static final int STABLE_WAIT_AFTER_NAV_MS = 3000;
    private static final long NO_GROWTH_QUIT_NS = 3_000_000_000L; // 3s

    private final BrowserManager browserManager;

    public SearchService(BrowserManager browserManager) {
        this.browserManager = browserManager;
    }

    /**
     * 搜索笔记
     *
     * @param keyword  关键词（允许为空）
     * @param limit    返回条数；0=默认值；&lt;0 抛异常
     */
    public Result<SearchFeedResponse> search(String keyword, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0");
        }
        final int want = (limit == 0 ? DEFAULT_LIMIT : limit);

        final long deadlineNs = System.nanoTime() + TOTAL_BUDGET.toNanos();
        final String searchURL = makeSearchURL(keyword);

        final Map<String, Feed> byId = new LinkedHashMap<>();
        final AtomicBoolean hasMore = new AtomicBoolean(false);

        try (Page page = browserManager.context().newPage()) {
            page.setDefaultTimeout(PAGE_DEFAULT_TIMEOUT_MS);

            // 监听搜索接口响应，增量收集 items
            Consumer<Response> handler = resp -> {
                try {
                    String url = resp.url();
                    if (url.contains(API_SEARCH_NOTES)) {
                        String body = resp.text();
                        JsonNode root = Json.M.readTree(body);
                        JsonNode data = root.path("data");
                        JsonNode items = data.path("items");
                        if (items != null && items.isArray()) {
                            SearchFeedResponse tmp = Json.M.convertValue(data, new TypeReference<>() {});
                            hasMore.set(Boolean.TRUE.equals(tmp.getHasMore()));
                            if (tmp.getItems() != null) {
                                for (Feed f : tmp.getItems()) {
                                    String id = (f == null) ? null : f.getId();
                                    if (StringUtils.isNotBlank(id) && !byId.containsKey(id)) {
                                        byId.put(id, f);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    // 可预期的解析异常：记录告警级别日志，避免中断整体流程
                    log.warn("Parse search response failed. err={}", ex.getMessage());
                }
            };
            page.onResponse(handler);

            // 1) 导航到搜索页（轻等待，避免 NETWORKIDLE）
            log.info("Navigate to search page. url={}", searchURL);
            page.navigate(searchURL, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(NAV_TIMEOUT_MS));
            page.waitForTimeout(STABLE_WAIT_AFTER_NAV_MS);

            // 2) 滚动加载，直到满足条件
            int prevCount = -1;
            long lastGrowTs = System.nanoTime();
            for (int i = 0; i < MAX_SCROLLS; i++) {
                if (byId.size() >= want) {
                    break;
                }
                if (!hasMore.get() && !byId.isEmpty()) {
                    break;
                }
                if (System.nanoTime() > deadlineNs) {
                    log.warn("Search loop hit total budget. collected={}", byId.size());
                    break;
                }

                // 模拟人类滚动：小滚两次 + 滚到底
                page.mouse().wheel(0, WHEEL_STEP_1);
                page.waitForTimeout(WAIT_AFTER_SCROLL_MS_1);
                page.mouse().wheel(0, WHEEL_STEP_2);
                page.waitForTimeout(WAIT_AFTER_SCROLL_MS_2);
                page.evaluate("() => window.scrollTo(0, document.body.scrollHeight)");
                page.waitForTimeout(WAIT_AFTER_BOTTOM_MS);

                int cur = byId.size();
                if (cur > prevCount) {
                    prevCount = cur;
                    lastGrowTs = System.nanoTime();
                } else if (System.nanoTime() - lastGrowTs > NO_GROWTH_QUIT_NS) {
                    log.info("No growth for 3s, stop scrolling. collected={}", cur);
                    break;
                }
            }

            // 3) 结果汇总
            page.offResponse(handler);

            if (byId.isEmpty()) {
                log.warn("No feeds collected from search API.");
                return Result.fail(BizErrorCode.DATA_NOT_FOUND, "未获取到搜索结果");
            }

            List<Feed> feeds = new ArrayList<>(byId.values());
            if (feeds.size() > want) {
                feeds = feeds.subList(0, want);
            }

            log.info("Search success. keyword='{}', requested={}, returned={}, hasMore={}",
                    keyword, want, feeds.size(), hasMore.get());

            return Result.ok(new SearchFeedResponse(feeds,hasMore.get()));

        } catch (PlaywrightException e) {
            log.error("Search system error. keyword='{}', err={}", keyword, e.getMessage(), e);
            throw new RuntimeException("搜索发生系统异常", e);
        }
    }

    /* =============== 辅助方法 =============== */
    private String makeSearchURL(String keyword) {
        String k = keyword == null ? "" : keyword;
        String qs = "keyword=" + URLEncoder.encode(k, StandardCharsets.UTF_8)
                + "&source=web_explore_feed";
        return "https://www.xiaohongshu.com/search_result?" + qs;
    }
}
