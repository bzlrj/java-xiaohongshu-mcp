package com.lv.xhsmcp.service;

import com.lv.xhsmcp.browser.BrowserManager;
import com.lv.xhsmcp.model.*;
import com.lv.xhsmcp.xhs.Result;
import com.lv.xhsmcp.xhs.XhsSelectors;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class LoginService {
    /* ===================== 常量区 ===================== */
    /** 首页地址（与工程中保持一致即可） */
    private static final String HOME_URL = XhsSelectors.HOME; // 若你项目中已有常量，直接复用
    /** 登录状态元素选择器 */
    private static final String SEL_LOGIN_STATUS = ".main-container .user .link-wrapper .channel";
    final String LOGIN_OK_SELECTOR = ".main-container .user .link-wrapper .channel";
    final String QR_IMG_SELECTOR   = ".login-container .qrcode-img";
    /** 导航与等待时长（毫秒） */
    private static final int PAGE_DEFAULT_TIMEOUT_MS = 60_000;
    private static final int POST_NAV_SLEEP_MS       = 1_000;

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    @Resource
    private BrowserManager bm;

    public Result<LoginCheck> checkLogin() {
        try (Page page = bm.context().newPage()) {
            page.setDefaultTimeout(PAGE_DEFAULT_TIMEOUT_MS);

            // 1) 打开首页并等待 DOM 内容加载
            log.info("Navigate to home page. url={}", HOME_URL);
            page.navigate(HOME_URL);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // 2) 若被重定向到登录页，属于业务正常分支（未登录）
            final String currentUrl = page.url();
            if (isLoginUrl(currentUrl)) {
                log.info("Redirected to login page. url={}", currentUrl);
                return Result.ok(LoginCheck.notLoggedIn("redirected_to_login"));
            }

            // 3) 稍等一会，确保首页关键块渲染完成
            page.waitForTimeout(POST_NAV_SLEEP_MS);

            // 4) 检查登录态元素是否存在（用 count() 避免 isVisible 误判）
            Locator statusEl = page.locator(SEL_LOGIN_STATUS);
            boolean exists = statusEl.count() > 0;
            if (!exists) {
                log.warn("Login status element not found. selector={}", SEL_LOGIN_STATUS);
                return Result.ok(LoginCheck.notLoggedIn("登录状态元素不存在"));
            }

            // 5) 一切正常，认为已登录
            log.info("Login status checked: logged in = true");
            return Result.ok(LoginCheck.loggedIn(),"已登录");

        } catch (PlaywrightException e) {
            log.error("Check login system error. err={}", e.getMessage(), e);
            throw new RuntimeException("检查登录状态发生系统异常", e);
        }
    }
    /* ===================== 私有方法 ===================== */

    private static boolean isLoginUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        String u = url.toLowerCase();
        return u.contains("/login") || u.contains("passport") || u.contains("captcha");
    }

    public Result<LoginQrcodeResponse> getLoginQrcode() {
        final Duration TIMEOUT = Duration.ofMinutes(4);
        try {
            Page page = bm.context().newPage();
            page.navigate(HOME_URL, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.LOAD)
                    .setTimeout(60_000));
            page.waitForTimeout(2_000); // 稍等页面完全渲染（与 Go 版一致）

            boolean loggedIn = page.locator(LOGIN_OK_SELECTOR).count() > 0;
            if (loggedIn) {
                // 已登录：立即返回，超时为 0s，并释放资源
                return Result.ok(null,"已登录");
            }
            // 未登录：尝试获取二维码 <img> 的 src
            Locator qrImg = page.locator(QR_IMG_SELECTOR).first();
            String imgSrc = qrImg.getAttribute("src");
            if (imgSrc == null || imgSrc.isBlank()) {
                throw new RuntimeException("二维码获取为空");
            }

            // 后台轮询（同一 executor 线程执行 Playwright 调用，避免多线程竞争）
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            final AtomicBoolean done = new AtomicBoolean(false);
            ScheduledFuture<?> task = exec.scheduleAtFixedRate(() -> {
                if (done.get()) return;
                try {
                    if (page.isClosed()) { done.set(true); return; }
                    boolean ok = page.locator(LOGIN_OK_SELECTOR).count() > 0;
                    if (ok) {
                        try { bm.persistCookies(); } catch (Throwable ignore) {}
                        done.set(true);
                    }
                    if (System.nanoTime() >= deadline) done.set(true);
                } catch (PlaywrightException e) {
                    // 包含 TargetClosedError 在内的调用异常，直接结束任务
                    done.set(true);
                } catch (Throwable t) {
                    done.set(true);
                }
            }, 0, 500, TimeUnit.MILLISECONDS);

            // 收尾：在后台标记完成后，关闭资源
            exec.schedule(() -> {
                if (done.get()) {
                    task.cancel(true);
                    safeClose(page);
                }
            }, TIMEOUT.toMillis() + 2_000, TimeUnit.MILLISECONDS);

            LoginQrcodeResponse loginQrcodeResponse = new LoginQrcodeResponse(formatDuration(TIMEOUT),loggedIn,ImageObject.parseFromBase64(imgSrc));
            return Result.ok(loginQrcodeResponse,"扫码进行登录");
        } catch (Throwable e) {
//            bm.close();
            throw e;
        }
    }
    private static void safeClose(AutoCloseable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignore) {}
    }

    private static String formatDuration(Duration d) {
        long seconds = d.getSeconds();
        if (seconds == 0) return "0s";
        long m = seconds / 60;
        long s = seconds % 60;
        if (m > 0) return s == 0 ? (m + "m0s") : (m + "m" + s + "s");
        return s + "s";
    }
}
