package com.lv.xhsmcp.service;

import com.lv.xhsmcp.browser.BrowserManager;
import com.lv.xhsmcp.model.*;
import com.lv.xhsmcp.util.Json;
import com.lv.xhsmcp.xhs.BizErrorCode;
import com.lv.xhsmcp.xhs.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
public class UserProfileService {
    /* ===================== 常量 ===================== */
    private static final int PAGE_DEFAULT_TIMEOUT_MS = 60_000;
    private static final int NAV_TIMEOUT_MS          = 60_000;
    private static final int STATE_WAIT_TIMEOUT_MS   = 10_000;

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

    /** 等待 State 注入的判断脚本 */
    private static final String JS_WAIT_STATE = """
        () => window.__INITIAL_STATE__ !== undefined || window.__XHS_DATA__ !== undefined
        """;

    @Resource
    private BrowserManager bm;

    public Result<UserProfileResponse> userProfile(String userId, String xsecToken) {
        // 参数校验（契约问题）
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("userId must not be blank");
        }

        try (Page page = bm.context().newPage()) {
            page.setDefaultTimeout(PAGE_DEFAULT_TIMEOUT_MS);

            // 1) 组 URL 并导航
            String url = makeUserProfileURL(userId, xsecToken);
            log.info("Navigate to user profile. userId={}, url={}", userId, url);
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(NAV_TIMEOUT_MS));

            // 2) 登录/人机检测（业务可预期错误）
            if (isLoginOrCaptcha(page.url())) {
                log.warn("Redirected to login/captcha. url={}", page.url());
                return Result.fail(BizErrorCode.AUTH_REQUIRED, "跳转至登录或人机验证页");
            }

            // 3) 等待 State 注入
            page.waitForFunction(JS_WAIT_STATE, null,
                    new Page.WaitForFunctionOptions().setTimeout(STATE_WAIT_TIMEOUT_MS));

            // 4) 读取初始状态
            String json = (String) page.evaluate(JS_READ_STATE);
            if (StringUtils.isBlank(json)) {
                log.warn("Initial state not found.");
                return Result.fail(BizErrorCode.DATA_NOT_FOUND, "__INITIAL_STATE__ 数据不存在");
            }

            JsonNode root;
            try {
                root = Json.M.readTree(json);
            } catch (Exception parseEx) {
                log.warn("Parse initial state failed. err={}", parseEx.getMessage());
                return Result.fail(BizErrorCode.DATA_PARSE_ERROR, "初始数据解析失败");
            }

            // 5) 解析 basicInfo 与 interactions
            JsonNode userPageData = root.path("user").path("userPageData").path("_rawValue");
            if (userPageData.isMissingNode() || userPageData.isNull()) {
                log.warn("userPageData._rawValue missing.");
                return Result.fail(BizErrorCode.DATA_NOT_FOUND, "userPageData 数据缺失");
            }

            UserBasicInfo basicInfo = Json.M.convertValue(
                    userPageData.path("basicInfo"),
                    new TypeReference<UserBasicInfo>() {}
            );

            List<InteractionItem> interactionItems = Collections.emptyList();
            JsonNode interactionsNode = userPageData.path("interactions");
            if (!interactionsNode.isMissingNode() && !interactionsNode.isNull()) {
                // 兼容数组或对象两种形态
                if (interactionsNode.isArray()) {
                    interactionItems = Json.M.convertValue(
                            interactionsNode, new TypeReference<List<InteractionItem>>() {});
                } else if (interactionsNode.isObject()) {
                    interactionItems = Json.M.convertValue(
                            interactionsNode.path("_rawValue"),
                            new TypeReference<List<InteractionItem>>() {});
                }
            }
            InteractInfo interactInfo = mapToInfo(interactionItems);

            // 6) 解析 feeds：user.notes._rawValue 二维数组 -> 扁平列表
            List<Feed> feeds = new ArrayList<>();
            JsonNode notesRaw = root.path("user").path("notes").path("_rawValue");
            if (notesRaw.isArray()) {
                for (JsonNode row : notesRaw) {
                    if (row != null && row.isArray()) {
                        List<Feed> oneRow = Json.M.convertValue(
                                row, new TypeReference<List<Feed>>() {});
                        if (oneRow != null && !oneRow.isEmpty()) {
                            feeds.addAll(oneRow);
                        }
                    }
                }
            }

//            ProfileDTO profile = new ProfileDTO(userId, basicInfo, interactInfo, Collections.unmodifiableList(feeds));
            UserProfileResponse userProfileResponse = new UserProfileResponse(basicInfo,interactInfo,feeds);
            log.info("User profile parsed. userId={}, hasBasic={}, interactions={}, feeds={}",
                    userId, basicInfo!=null, interactionItems.size(), feeds.size());
            return Result.ok(userProfileResponse);
        } catch (PlaywrightException e) {
            log.error("User profile system error. userId={}, err={}", userId, e.getMessage(), e);
            throw new RuntimeException("获取用户主页发生系统异常", e);
        }
    }

    public static InteractInfo mapToInfo(List<InteractionItem> items) {
        InteractInfo info = new InteractInfo();
        for (InteractionItem it : items) {
            switch (it.getType()) {
                case "follows" -> info.setSharedCount(it.getCount());     // 按你需求映射
                case "fans" -> info.setCommentCount(it.getCount());       // 这里随意举例
                case "interaction" -> info.setCollectedCount(it.getCount());
                default -> {}
            }
        }
        return info;
    }
    private static boolean isLoginOrCaptcha(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        String u = url.toLowerCase();
        return u.contains("/login") || u.contains("captcha") || u.contains("passport");
    }
    private static String makeUserProfileURL(String userId, String xsecToken) {
        String base = "https://www.xiaohongshu.com/user/profile/" + userId;
        if (StringUtils.isBlank(xsecToken)) {
            return base;
        }
        String token = URLEncoder.encode(xsecToken, StandardCharsets.UTF_8);
        return base + "?xsec_token=" + token + "&xsec_source=pc_note";
    }
}
