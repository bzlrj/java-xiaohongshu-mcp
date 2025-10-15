package com.lv.xhsmcp.service;

import com.lv.xhsmcp.browser.BrowserManager;
import com.lv.xhsmcp.xhs.BizErrorCode;
import com.lv.xhsmcp.xhs.Result;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
public class PostCommentService {

    /* ===================== 选择器常量（避免魔法值） ===================== */
    private static final String SEL_TRIGGER_SPAN = "div.input-box div.content-edit span";
    private static final String SEL_INPUT_P      = "div.input-box div.content-edit p.content-input";
    private static final String SEL_INPUT_CE     = "div.input-box div.content-edit [contenteditable='true']";
    private static final String SEL_SUBMIT_BTN   = "div.bottom button.submit";

    /* ===================== 超时常量（毫秒） ===================== */
    private static final int NAV_TIMEOUT_MS        = 60_000;
    private static final int PAGE_DEFAULT_TIMEOUT  = 60_000;
    private static final int SLEEP_AFTER_NAV_MS    = 3_000;
    private static final int CLICK_TIMEOUT_MS      = 2_000;
    private static final int SLEEP_AFTER_INPUT_MS  = 1_000;
    private static final int SUBMIT_TIMEOUT_MS     = 5_000;
    private static final int SLEEP_AFTER_SUBMIT_MS = 1_000;

    private final BrowserManager browserManager;

    public PostCommentService(BrowserManager bm) {
        this.browserManager = bm;
    }

    /**
     * 发布评论
     */
    public Result<Void> postComment(String feedId, String xsecToken, String content) {
        // 1) 参数校验 —— 手册建议：前置校验，用成熟类库；契约问题抛异常
        if (StringUtils.isBlank(feedId)) {
            throw new IllegalArgumentException("发表评论失败: 缺少feed_id参数");
        }
        if (StringUtils.isBlank(xsecToken)) {
            throw new IllegalArgumentException("发表评论失败: 缺少xsec_token参数");
        }
        if (StringUtils.isBlank(content)) {
            throw new IllegalArgumentException("发表评论失败: 缺少content参数");
        }

        // 2) 资源使用 —— try-with-resources，确保 Page 被关闭；不在 finally 中 return
        try (Page page = browserManager.context().newPage()) {
            page.setDefaultTimeout(PAGE_DEFAULT_TIMEOUT);

            // 2.1 导航（手册：日志用占位符，不拼接）
            final String url = makeFeedDetailURL(feedId, xsecToken);
            log.info("Navigate to detail page. feedId={}, url={}", feedId, url);
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(NAV_TIMEOUT_MS));

            // 2.2 登录/人机检测 —— 业务可预期错误：Result.fail
            final String currentUrl = page.url();
            if (isLoginOrCaptcha(currentUrl)) {
                log.warn("Redirected to login/captcha. url={}", currentUrl);
                return Result.fail(BizErrorCode.AUTH_REQUIRED, "跳转至登录或人机验证页");
            }

            page.waitForTimeout(SLEEP_AFTER_NAV_MS);

            // 2.3 触发输入框可编辑（存在则点击）
            Locator trigger = page.locator(SEL_TRIGGER_SPAN).first();
            if (trigger.count() > 0) {
                trigger.scrollIntoViewIfNeeded();
                trigger.click();
            }

            // 2.4 定位输入框 —— 业务可预期错误：Result.fail
            Locator input = resolveCommentInput(page);
            if (isNotFound(input)) {
                log.warn("Comment input not found. feedId={}", feedId);
                return Result.fail(BizErrorCode.ELEMENT_NOT_FOUND, "未找到评论输入框");
            }

            // 2.5 输入内容
            input.scrollIntoViewIfNeeded();
            input.click(new Locator.ClickOptions().setTimeout(CLICK_TIMEOUT_MS)); // 部分富文本需先点击
            input.fill(""); // 清空后输入
            input.type(content);
            page.waitForTimeout(SLEEP_AFTER_INPUT_MS);

            // 2.6 提交 —— 业务可预期错误：Result.fail
            Locator submitBtn = page.locator(SEL_SUBMIT_BTN).first();
            if (isNotFound(submitBtn)) {
                log.warn("Submit button not found. feedId={}", feedId);
                return Result.fail(BizErrorCode.ELEMENT_NOT_FOUND, "未找到提交按钮");
            }
            submitBtn.scrollIntoViewIfNeeded();
            submitBtn.click(new Locator.ClickOptions().setTimeout(SUBMIT_TIMEOUT_MS));
            page.waitForTimeout(SLEEP_AFTER_SUBMIT_MS);

            // 2.7 刷新会话（例如持久化 Cookie）
            browserManager.persistCookies();
            log.info("Post comment success. feedId={}", feedId);
            return Result.ok();

        } catch (PlaywrightException e) {
            // 3) 系统异常 —— 抛出运行时异常；日志记录异常栈；不吞异常
            log.error("Post comment system error. feedId={}, err={}", feedId, e.getMessage(), e);
            throw new RuntimeException("发表评论系统异常", e);
        }
    }

    private boolean isLoginOrCaptcha(String currentUrl) {
        if (currentUrl == null) {
            return false;
        }
        String u = currentUrl.toLowerCase();
        return u.contains("/login") || u.contains("captcha");
    }

    private static Locator resolveCommentInput(Page page) {
        Locator p = page.locator(SEL_INPUT_P).first();
        if (p.count() > 0) {
            return p;
        }
        return page.locator(SEL_INPUT_CE).first();
    }

    private boolean isNotFound(Locator locator) {
        return locator == null || locator.count() == 0;
    }

    /* 与 feedDetail 共用的 URL 构造 */
    private String makeFeedDetailURL(String feedId, String xsecToken) {
        String base = "https://www.xiaohongshu.com/explore/" + feedId;
        if (xsecToken == null || xsecToken.isBlank()) return base;
        String token = URLEncoder.encode(xsecToken, StandardCharsets.UTF_8);
        return base + "?xsec_token=" + token + "&xsec_source=pc_feed";
    }
}
