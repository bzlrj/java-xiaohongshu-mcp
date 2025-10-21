package com.lv.xhsmcp.service;

import com.lv.xhsmcp.browser.BrowserManager;
import com.lv.xhsmcp.xhs.BizErrorCode;
import com.lv.xhsmcp.xhs.Result;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PublishService {
    /* ===================== 常量（避免魔法数） ===================== */
    private static final String URL_PUBLISH = "https://creator.xiaohongshu.com/publish/publish?source=official";

    private static final String SEL_UPLOAD_CONTENT  = "div.upload-content";
    private static final String SEL_CREATOR_TAB     = "div.creator-tab";
    private static final String SEL_FILE_INPUT      = ".upload-input";
    private static final String SEL_PREVIEW_ITEM    = ".img-preview-area .pr";
    private static final String SEL_TITLE_INPUT     = "div.d-input input";
    private static final String SEL_SUBMIT_BTN      = "div.submit div.d-button-content";
    private static final String SEL_QUILL_EDITOR    = "div.ql-editor";
    private static final String SEL_TOPIC_DROPDOWN  = "#creator-editor-topic-container";
    private static final String SEL_TOPIC_ITEM      = ".item";

    private static final int PAGE_DEFAULT_TIMEOUT_MS  = 60_000;
    private static final int NAV_TIMEOUT_MS           = 60_000;
    private static final int STABLE_SLEEP_SHORT_MS    = 1_000;
    private static final int STABLE_SLEEP_LONG_MS     = 3_000;
    private static final int CLICK_TIMEOUT_MS         = 2_000;
    private static final int UPLOAD_POLL_INTERVAL_MS  = 500;
    private static final Duration UPLOAD_MAX_WAIT     = Duration.ofSeconds(60);

    private static final Path LOCAL_IMAGE_DIR         = Paths.get("images");
    private static final Pattern TAG_PATTERN          = Pattern.compile("#([\\p{L}\\p{N}_]+)");

    @Resource
    private BrowserManager browserManager;

    /**
     * 发布图文
     * - 参数错误 -> IllegalArgumentException
     * - 业务可预期错误 -> Result.fail(...)
     * - 系统异常 -> RuntimeException
     */
    public Result<Void> publish(String title, String content, List<String> imagePaths) {
        // 1) 参数校验（契约问题直接抛异常）
        if (StringUtils.isBlank(title)) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (StringUtils.isBlank(content)) {
            throw new IllegalArgumentException("正文内容不能为空");
        }
        if (imagePaths == null || imagePaths.isEmpty()) {
            throw new IllegalArgumentException("图片地址不能为空");
        }

        // 2) 远程图片下载 & 路径校验（业务可预期错误 -> Result.fail）
        Result<ImagesReady> readyRet = checkAndDownload(imagePaths);
        if (!readyRet.isSuccess()) {
            return Result.fail(readyRet.getCode(), readyRet.getMessage());
        }
        List<String> localImages = readyRet.getData().getLocalPaths();

        // 3) 浏览器自动化
        try (Page page = browserManager.context().newPage()) {
            page.setDefaultTimeout(PAGE_DEFAULT_TIMEOUT_MS);

            // 3.1 进入发布页
            log.info("Navigate to publish page. url={}", URL_PUBLISH);
            page.navigate(URL_PUBLISH, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(NAV_TIMEOUT_MS));

            // 3.2 登录/人机检测（业务可预期错误）
            if (isCaptchaOrLogin(page)) {
                log.warn("Captcha or login required. url={}", page.url());
                return Result.fail(BizErrorCode.AUTH_REQUIRED, "需要登录或通过人机验证");
            }

            // 3.3 等上传区域可见
            Locator uploadContent = page.locator(SEL_UPLOAD_CONTENT);
            uploadContent.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            page.waitForTimeout(STABLE_SLEEP_SHORT_MS);

            removePopCover(page);

            // 3.4 点击“上传图文”Tab
            clickVisibleTabByExactText(page, SEL_CREATOR_TAB, "上传图文");
            page.waitForTimeout(STABLE_SLEEP_SHORT_MS);

            // 3.5 上传图片
            Result<Void> uploadRet = uploadImages(page, localImages);
            if (!uploadRet.isSuccess()) {
                return uploadRet; // 携带明确的业务错误码
            }

            // 3.6 等待上传完成
            waitForUploadComplete(page, localImages.size(), UPLOAD_MAX_WAIT);

            // 3.7 填标题
            Locator titleInput = page.locator(SEL_TITLE_INPUT).first();
            if (titleInput.count() == 0) {
                log.warn("Title input not found.");
                return Result.fail(BizErrorCode.ELEMENT_NOT_FOUND, "未找到标题输入框");
            }
            titleInput.fill(title);
            page.waitForTimeout(STABLE_SLEEP_SHORT_MS);

            // 3.8 填正文
            Locator contentBox = resolveContentEditor(page);
            if (contentBox == null) {
                log.warn("Content editor not found.");
                return Result.fail(BizErrorCode.ELEMENT_NOT_FOUND, "未找到内容输入框");
            }
            contentBox.fill("");
            contentBox.type(content);

            // 3.9 输入标签（从正文提取）
            inputTags(page, contentBox, extractTags(content));
            page.waitForTimeout(STABLE_SLEEP_SHORT_MS);

            // 3.10 发布
            Locator publishBtn = page.locator(SEL_SUBMIT_BTN).first();
            if (publishBtn.count() == 0) {
                log.warn("Publish button not found.");
                return Result.fail(BizErrorCode.ELEMENT_NOT_FOUND, "未找到发布按钮");
            }
            publishBtn.click(new Locator.ClickOptions().setTimeout(CLICK_TIMEOUT_MS));
            page.waitForTimeout(STABLE_SLEEP_LONG_MS);

            browserManager.persistCookies();
            log.info("Publish success.");
            return Result.ok("发布成功");

        } catch (PlaywrightException e) {
            log.error("Publish system error. err={}", e.getMessage(), e);
            throw new RuntimeException("发布图文发生系统异常", e);
        }
    }

    /* ===================== 资源准备：下载/校验图片 ===================== */

    public Result<ImagesReady> checkAndDownload(List<String> imagePaths) {
        if (imagePaths == null || imagePaths.isEmpty()) {
            throw new IllegalArgumentException("imagePaths must not be empty");
        }
        try {
            if (!Files.exists(LOCAL_IMAGE_DIR)) {
                Files.createDirectories(LOCAL_IMAGE_DIR);
            }
            List<String> newPaths = new ArrayList<>(imagePaths.size());
            for (String p : imagePaths) {
                if (StringUtils.isBlank(p)) {
                    throw new IllegalArgumentException("image path must not be blank");
                }
                if (p.startsWith("http://") || p.startsWith("https://")) {
                    String saved = downloadImageToLocal(p);
                    if (saved == null) {
                        return Result.fail(BizErrorCode.DOWNLOAD_FAILED, "下载远程图片失败: " + p);
                    }
                    newPaths.add(saved);
                } else {
                    Path path = Paths.get(p);
                    if (!Files.exists(path)) {
                        return Result.fail(BizErrorCode.FILE_NOT_FOUND, "图片文件不存在: " + p);
                    }
                    newPaths.add(path.toAbsolutePath().toString());
                }
            }
            return Result.ok(new ImagesReady(Collections.unmodifiableList(newPaths)));
        } catch (IOException ioe) {
            log.warn("Process images io error. err={}", ioe.getMessage());
            return Result.fail(BizErrorCode.IO_ERROR, "处理图片出错: " + ioe.getMessage());
        }
    }

    private String downloadImageToLocal(String url) {
        try (InputStream in = new URL(url).openStream()) {
            String fileName = UUID.randomUUID() + ".jpg"; // 简单命名，真实场景可按 Content-Type/扩展名优化
            Path localFile = LOCAL_IMAGE_DIR.resolve(fileName);
            Files.copy(in, localFile, StandardCopyOption.REPLACE_EXISTING);
            return localFile.toAbsolutePath().toString();
        } catch (IOException e) {
            log.warn("Download image failed. url={}, err={}", url, e.getMessage());
            return null;
        }
    }

    /* ===================== 页面操作小工具 ===================== */

    private boolean isCaptchaOrLogin(Page page) {
        String url = page.url();
        if (StringUtils.isBlank(url)) {
            return false;
        }
        String u = url.toLowerCase(Locale.ROOT);
        if (u.contains("/login") || u.contains("captcha") || u.contains("passport")) {
            return true;
        }
        Locator cap = page.locator("text=/人机验证|验证码|我不是机器人/");
        return cap.count() > 0;
    }

    /** 过滤可见元素，点击文本精确为 targetText 的项，若无则尝试包含匹配 */
    private void clickVisibleTabByExactText(Page page, String selector, String targetText) {
        Locator tabs = page.locator(selector);
        int n = tabs.count();
        List<Integer> visibleIdx = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Locator el = tabs.nth(i);
            if (isElementVisible(el)) {
                visibleIdx.add(i);
            }
        }
        if (visibleIdx.isEmpty()) {
            throw new PlaywrightException("未找到可见的 Tab 区域：" + selector);
        }
        for (Integer i : visibleIdx) {
            Locator el = tabs.nth(i);
            String txt = safeInnerText(el);
            if (targetText.equals(txt)) {
                el.click();
                return;
            }
        }
        for (Integer i : visibleIdx) {
            Locator el = tabs.nth(i);
            String txt = safeInnerText(el);
            if (txt != null && txt.contains(targetText)) {
                el.click();
                return;
            }
        }
        throw new PlaywrightException("未定位到“" + targetText + "”按钮");
    }

    private Result<Void> uploadImages(Page page, List<String> localImages) {
        Locator fileInput = page.locator(SEL_FILE_INPUT).first();
        if (fileInput.count() == 0) {
            log.warn("File input not found.");
            return Result.fail(BizErrorCode.ELEMENT_NOT_FOUND, "未找到图片上传输入框");
        }
        try {
            List<FilePayload> payloads = new ArrayList<>(localImages.size());
            for (String s : localImages) {
                Path path = Paths.get(s);
                byte[] bytes = Files.readAllBytes(path);
                String mime = Files.probeContentType(path);
                if (mime == null) {
                    mime = "image/jpeg";
                }
                payloads.add(new FilePayload(path.getFileName().toString(), mime, bytes));
            }
            fileInput.setInputFiles(payloads.toArray(FilePayload[]::new));
            return Result.ok();
        } catch (IOException e) {
            log.warn("Read image bytes failed. err={}", e.getMessage());
            return Result.fail(BizErrorCode.IO_ERROR, "读取图片失败：" + e.getMessage());
        }
    }

    private void waitForUploadComplete(Page page, int expectedCount, Duration maxWait) {
        long deadline = System.nanoTime() + maxWait.toNanos();
        while (System.nanoTime() < deadline) {
            Locator imgs = page.locator(SEL_PREVIEW_ITEM);
            if (imgs.count() >= expectedCount) {
                return;
            }
            page.waitForTimeout(UPLOAD_POLL_INTERVAL_MS);
        }
        throw new PlaywrightException("上传超时，请检查网络与图片大小");
    }

    /** 优先 Quill 编辑器；否则通过 placeholder=输入正文描述 的 p 向上查找 role=textbox */
    private Locator resolveContentEditor(Page page) {
        Locator ql = page.locator(SEL_QUILL_EDITOR).first();
        if (ql.count() > 0 && isElementVisible(ql)) {
            return ql;
        }
        Locator ps = page.locator("p");
        int n = ps.count();
        for (int i = 0; i < n; i++) {
            Locator p = ps.nth(i);
            String ph = p.getAttribute("data-placeholder");
            if (ph != null && ph.contains("输入正文描述")) {
                Locator parent = p;
                for (int up = 0; up < 5; up++) {
                    parent = parent.locator("xpath=..");
                    if (parent == null || parent.count() == 0) {
                        break;
                    }
                    String role = parent.getAttribute("role");
                    if ("textbox".equals(role)) {
                        return parent;
                    }
                }
            }
        }
        return null;
    }

    private void inputTags(Page page, Locator contentBox, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        // 让光标到合适位置（与原逻辑对齐）
        for (int i = 0; i < 20; i++) {
            contentBox.press("ArrowDown");
            page.waitForTimeout(10);
        }
        contentBox.press("Enter");
        contentBox.press("Enter");
        page.waitForTimeout(STABLE_SLEEP_SHORT_MS);

        for (String raw : tags) {
            if (StringUtils.isBlank(raw)) {
                continue;
            }
            String tag = raw.startsWith("#") ? raw.substring(1) : raw;

            contentBox.type("#");
            page.waitForTimeout(200);

            for (char c : tag.toCharArray()) {
                contentBox.type(String.valueOf(c));
                page.waitForTimeout(50);
            }

            page.waitForTimeout(STABLE_SLEEP_SHORT_MS);

            Locator dropdown = page.locator(SEL_TOPIC_DROPDOWN);
            if (dropdown.count() > 0 && isElementVisible(dropdown)) {
                Locator firstItem = dropdown.locator(SEL_TOPIC_ITEM).first();
                if (firstItem.count() > 0) {
                    firstItem.click();
                    page.waitForTimeout(200);
                    continue;
                }
            }
            // 无联想则空格结束
            contentBox.type(" ");
            page.waitForTimeout(500);
        }
    }

    private boolean isElementVisible(Locator el) {
        try {
            if (el.count() == 0) {
                return false;
            }
            String style = el.getAttribute("style");
            if (style != null) {
                String s = style.replace(" ", "");
                if (s.contains("left:-9999px") || s.contains("top:-9999px")
                        || s.contains("display:none") || s.contains("visibility:hidden")) {
                    return false;
                }
            }
            return el.isVisible();
        } catch (Exception e) {
            // 获取可见性失败，保持容错
            return true;
        }
    }

    private String safeInnerText(Locator el) {
        try {
            return el.innerText();
        } catch (Exception ignore) {
            return null;
        }
    }

    private List<String> extractTags(String text) {
        if (text == null) {
            return List.of();
        }
        var m = TAG_PATTERN.matcher(text);
        List<String> tags = new ArrayList<>();
        while (m.find()) {
            tags.add(m.group(1));
        }
        return tags;
    }

    /* ===================== 领域模型 & 返回体 ===================== */

    public static final class ImagesReady {
        private final List<String> localPaths;
        public ImagesReady(List<String> localPaths) { this.localPaths = localPaths; }
        public List<String> getLocalPaths() { return localPaths; }
        @Override public String toString() { return "ImagesReady{localPaths=" + localPaths + '}'; }
    }

    /** 移除弹窗封面，并兜底点击空白位置 */
    public static void removePopCover(Page page) {
        try {
            Locator popovers = page.locator("div.d-popover");
            int count = popovers.count();
            if (count > 0) {
                // 方式一：逐个移除
                List<Locator> all = popovers.all();
                for (Locator l : all) {
                    try {
                        l.evaluate("el => el.remove()");
                    } catch (Exception ignore) {
                        // 万一某个节点已经被移除了/失效，忽略即可
                    }
                }
                // 或者用方式二：一次性在页面上移除（任选其一）
                // page.evaluate("() => { document.querySelectorAll('div.d-popover').forEach(e => e.remove()); }");
            }
        } catch (Exception ignore) {
            // 保持与原代码一致：失败也不抛出，继续兜底点击
        }

        // 兜底：点击一下空位置
        clickEmptyPosition(page);
    }

    /** 在页面左上区域随机点击一个相对安全的空白坐标 */
    public static void clickEmptyPosition(Page page) {
        int[] vp = getViewportSize(page); // [width, height]
        // 原 rod 代码随机范围：x=380~479, y=20~79
        // 同时确保不超出当前视口
        int x = clamp(380 + randInt(0, 100), 0, Math.max(0, vp[0] - 1));
        int y = clamp(20  + randInt(0, 60),  0, Math.max(0, vp[1] - 1));

        try {
            page.mouse().move(x, y);
            page.mouse().click(x, y, new Mouse.ClickOptions().setButton(MouseButton.LEFT));
        } catch (Exception ignore) {
            // 与原逻辑一致：失败忽略
        }
    }

    private static int[] getViewportSize(Page page) {
        // Playwright 里如果没手动设置 viewport，可能返回 null；给个兜底默认值
        ViewportSize size = page.viewportSize();
        if (size == null) {
            return new int[]{1280, 720};
        }
        return new int[]{size.width, size.height};
    }

    private static int randInt(int startInclusive, int endExclusive) {
        return ThreadLocalRandom.current().nextInt(startInclusive, endExclusive);
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
