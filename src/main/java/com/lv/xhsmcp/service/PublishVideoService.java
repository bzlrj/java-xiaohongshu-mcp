package com.lv.xhsmcp.service;

import com.lv.xhsmcp.browser.BrowserManager;
import com.lv.xhsmcp.xhs.Result;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.*;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class PublishVideoService {
    /* ===================== 常量（避免魔法数） ===================== */
    private static final String URL_PUBLISH = "https://creator.xiaohongshu.com/publish/publish?source=official";


    private static final int PAGE_DEFAULT_TIMEOUT_MS  = 60_000;
    private static final int NAV_TIMEOUT_MS           = 60_000;
    private static final int STABLE_SLEEP_SHORT_MS    = 1_000;
    private static final int STABLE_SLEEP_LONG_MS     = 3_000;

    private final BrowserManager browserManager;

    public PublishVideoService(BrowserManager browserManager) {
        this.browserManager = browserManager;
    }

    /**
     * 发布图文
     * - 参数错误 -> IllegalArgumentException
     * - 业务可预期错误 -> Result.fail(...)
     * - 系统异常 -> RuntimeException
     */
    public Result<Void> publish(String title, String content, String videoPath) {
        // 1) 参数校验（契约问题直接抛异常）
        if (StringUtils.isBlank(title)) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (StringUtils.isBlank(content)) {
            throw new IllegalArgumentException("正文内容不能为空");
        }
        if (videoPath == null) {
            throw new IllegalArgumentException("视频不能为空");
        }
        // 3) 浏览器自动化
        try (Page page = browserManager.context().newPage()) {
            page.setDefaultTimeout(PAGE_DEFAULT_TIMEOUT_MS);

            // 3.1 进入发布页
            log.info("Navigate to publish page. url={}", URL_PUBLISH);
            page.navigate(URL_PUBLISH, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(NAV_TIMEOUT_MS));
            page.waitForTimeout(STABLE_SLEEP_SHORT_MS);
            removePopCover(page);
            this.switchToVideoTab(page);
            uploadVideo(page,videoPath);
            submitPublishVideo(page,title,content);
            page.waitForTimeout(STABLE_SLEEP_LONG_MS);
            browserManager.persistCookies();
            log.info("Publish success.");
            return Result.ok("发布成功");
        }
    }

    /** 进入发布页并点击“上传视频”Tab*/
    public Page switchToVideoTab(Page page) {
        // 方案1：基于文本
        Locator videoTab = page.getByText("上传视频", new Page.GetByTextOptions().setExact(false));
        if (videoTab.count() == 0) {
            // 方案2：CSS 定位，例如 .publish-tabs .tab[data-type='video']
            videoTab = page.locator(".publish-tabs .tab[data-type='video']");
        }
        videoTab.first().click(new Locator.ClickOptions().setTimeout(10_000));
        page.waitForTimeout(800);

        return page;
    }

    private void uploadVideo(Page page,String videoPath) {
        // 上传过程较久：可适当调大默认等待
        page.setDefaultTimeout(5 * 60 * 1000);

        // 1) 找上传 input（优先你原来的 .upload-input；失败退回 input[type=file]）
        Locator fileInput = page.locator(".upload-input").first();
        if (fileInput.count() == 0) {
            fileInput = page.locator("input[type='file']").first();
        }
        if (fileInput.count() == 0) {
            throw new RuntimeException("未找到视频上传输入框");
        }

        // 2) 选择文件（Playwright 可直接对隐藏的 input 设置文件）
        fileInput.setInputFiles(Path.of(videoPath));

        // 3) 等待“发布”按钮可点击，表示处理完成
        Locator publishBtn = waitForPublishButtonClickable(page,Duration.ofMinutes(10));
        System.out.println("视频上传/处理完成，发布按钮可点击: " + publishBtn);
    }

    /** 等待发布按钮可点击（等价 waitForPublishButtonClickable） */
    private Locator waitForPublishButtonClickable(Page page,Duration maxWait) {
        long deadline = System.nanoTime() + maxWait.toNanos();
        // TODO 选择器：根据你页面实际按钮 class 调整
        Locator btn = page.locator("button.publishBtn").first();

        // 如果按钮是动态挂载，先等它出现
        btn.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(maxWait.toMillis()));

        while (System.nanoTime() < deadline) {
            boolean visible = safe(() -> btn.isVisible(), false);
            boolean enabled = safe(() -> btn.isEnabled(), false);
            String cls = safe(() -> Objects.toString(btn.getAttribute("class"), ""), "");
            String disabledAttr = safe(() -> btn.getAttribute("disabled"), null);

            if (visible && enabled && disabledAttr == null && (cls == null || !cls.contains("disabled"))) {
                return btn;
            }
            page.waitForTimeout(1000);
        }
        throw new RuntimeException("等待发布按钮可点击超时");
    }


    /** ===== 填写标题、正文、标签并点击发布（等价 submitPublishVideo） ===== */
    private void submitPublishVideo(Page page,String title, String content) {
        // 标题（TODO 选择器：按你的 DOM 调整）
        Locator titleInput = page.locator("div.d-input input").first();
        if (titleInput.count() == 0) {
            // 备选：可能是 textarea 或别的输入
            titleInput = page.locator("input[placeholder*='标题'], textarea[placeholder*='标题']").first();
        }
        titleInput.fill(title);
        page.waitForTimeout(300);

        // 正文（内容区可能是 contenteditable 或 textarea）
        Locator contentBox = getContentEditableOrTextarea(page);
        if (contentBox == null) {
            throw new RuntimeException("没有找到内容输入框");
        }
        // 对 contenteditable，用 pressSequential 确保输入；对 textarea 直接 fill
        if (isContentEditable(contentBox)) {
            contentBox.click();
            contentBox.fill(""); // 清空
            contentBox.type(content, new Locator.TypeOptions().setDelay(10)); // 模拟人类输入
        } else {
            contentBox.fill(content);
        }
        page.waitForTimeout(300);

        // 标签：按你页面的标签输入交互来（常见方案：在正文末尾用 #tag 触发 或 单独的 tag 输入框）
//        inputTags(page,tags);

        // 等按钮可点，再提交
        Locator publishBtn = waitForPublishButtonClickable(page,Duration.ofMinutes(10));
        publishBtn.click();
        page.waitForTimeout(3000);
    }

    /** 获取内容输入框：contenteditable 或 textarea（二选一） */
    private Locator getContentEditableOrTextarea(Page page) {
        // TODO 选择器：根据你页面实际结构调整优先级
        Locator editable = page.locator("[contenteditable='true']").first();
        if (editable.count() > 0) return editable;

        Locator textarea = page.locator("textarea").first();
        if (textarea.count() > 0) return textarea;

        // 可能是自定义编辑器容器
        Locator rich = page.locator(".editor, .ql-editor, .d-input .content").first();
        return (rich.count() > 0) ? rich : null;
    }

    private boolean isContentEditable(Locator el) {
        try {
            String ce = el.getAttribute("contenteditable");
            return "true".equalsIgnoreCase(ce);
        } catch (PlaywrightException e) {
            return false;
        }
    }

    /** 标签输入：根据站点交互自行调整 */
    private void inputTags(Page page,List<String> tags) {
        if (tags == null || tags.isEmpty()) return;

        // 情况A：正文内用 #xxx 触发话题
        Locator editable = page.locator("[contenteditable='true']").first();
        if (editable.count() > 0) {
            editable.click();
            for (String t : tags) {
                editable.type(" #" + t + " ", new Locator.TypeOptions().setDelay(10));
                page.waitForTimeout(200);
            }
            return;
        }

        // 情况B：独立的标签输入框
        Locator tagInput = page.locator("input[placeholder*='标签'], .tag-input input").first(); // TODO 选择器
        if (tagInput.count() > 0) {
            for (String t : tags) {
                tagInput.fill(t);
                tagInput.press("Enter");
                page.waitForTimeout(150);
            }
        }
    }
    /** 小工具：安全执行，异常时返回默认值 */
    private static <T> T safe(SupplierE<T> s, T def) {
        try { return s.get(); } catch (Throwable t) { return def; }
    }
    @FunctionalInterface private interface SupplierE<T> { T get() throws Throwable; }

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
