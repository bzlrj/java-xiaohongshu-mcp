package com.lv.xhsmcp.service;

import com.lv.xhsmcp.browser.BrowserManager;
import com.lv.xhsmcp.model.*;
import com.lv.xhsmcp.xhs.*;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;

@Slf4j
public class XhsService {
    private final BrowserManager bm;
    private final PublishService publishService;
    private final PublishVideoService publishVideoService;
    private final FeedsService feedsService;
    private final FeedDetailService feedDetailService;
    private final SearchService searchService;
    private final PostCommentService postCommentService;
    private final UserProfileService userProfileService;
    private final LoginService loginService;

    public XhsService(BrowserManager bm) {
        this.bm = bm;
        this.publishService = new PublishService(bm);
        this.feedsService = new FeedsService(bm);
        this.feedDetailService = new FeedDetailService(bm);
        this.searchService = new SearchService(bm);
        this.postCommentService = new PostCommentService(bm);
        this.userProfileService = new UserProfileService(bm);
        this.loginService = new LoginService(bm);
        this.publishVideoService = new PublishVideoService(bm);
    }

    @Tool(description = "检查小红书登录状态")
    public Result<LoginCheck> checkLogin() {
        try {
            return this.loginService.checkLogin();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Result.fail(BizErrorCode.STATUS_CHECK_FAILED, "检查登录状态失败");
        }
    }

    @McpTool(description = "获取小红书登录二维码")
    public McpSchema.CallToolResult getLoginQrcode() {
        Result<LoginQrcodeResponse> loginQrcodeResponseResult = this.loginService.getLoginQrcode();
        var text = new McpSchema.TextContent(loginQrcodeResponseResult.getMessage());
        McpSchema.ImageContent image = null;
        if(loginQrcodeResponseResult.getData()!=null){
            image = new McpSchema.ImageContent(null, loginQrcodeResponseResult.getData().imageObject.getData(), "image/png");
            return McpSchema.CallToolResult.builder().content(List.of(text,image)).build();
        }
        return McpSchema.CallToolResult.builder().content(List.of(text)).build();
    }

    @Tool(description = "发布小红书图文内容")
    public Result<Void> publish(@ToolParam(description = "内容标题（小红书限制：最多20个中文字或英文单词）") String title, @ToolParam(description = "正文内容，不包含以#开头的标签内容，所有话题标签都用tags参数来生成和提供即可") String content, List<String> images) {
        try {
            return publishService.publish(title, content, images);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Result.fail(BizErrorCode.PUBLISH_FAILED, "发布失败");
        }
    }

    @Tool(description = "发布小红书视频内容")
    public Result<Void> publishVideo(@ToolParam(description = "内容标题（小红书限制：最多20个中文字或英文单词）") String title, @ToolParam(description = "正文内容，不包含以#开头的标签内容，所有话题标签都用tags参数来生成和提供即可") String content, String videoPath) {
        try {
            return publishVideoService.publish(title, content, videoPath);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Result.fail(BizErrorCode.PUBLISH_FAILED, "发布失败");
        }
    }

    @Tool(description = "获取用户发布的内容列表")
    public Result<List<Feed>> listFeeds(@ToolParam(description = "需要返回的数量") int limit) {
        try {
            return this.feedsService.listFeeds(limit); // 默认 10 条，可加参数
        } catch (Exception e) {
            return Result.fail(BizErrorCode.LIST_FEEDS_FAILED, "获取Feeds列表失败");
        }
    }

    @Tool(description = "搜索小红书内容（需要已登录）")
    public Result<SearchFeedResponse> search(@ToolParam(description = "搜索关键词") String keyword, @ToolParam(description = "需要返回的数量") int limit) {
        try {
            return this.searchService.search(keyword, limit); // 支持滚动加载的那版
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Result.fail(BizErrorCode.SEARCH_FEEDS_FAILED, "搜索Feeds失败");
        }
    }

    @Tool(description = "获取小红书笔记详情，返回笔记内容、图片、作者信息、互动数据（点赞/收藏/分享数）及评论列表")
    public Result<FeedDetailResponse> feedDetail(@ToolParam(description = "feed_id") String feedId, @ToolParam(description = "xsec_token") String xsecToken) {
        try {
            return this.feedDetailService.feedDetail(feedId, xsecToken);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Result.fail(BizErrorCode.GET_FEED_DETAIL_FAILED, "获取Feed详情失败");
        }
    }

    @Tool(description = "发表评论到小红书笔记")
    public Result<Void> postComment(@ToolParam(description = "feed_id") String feedId, @ToolParam(description = "xsec_token") String xsecToken, @ToolParam(description = "content") String content) {
        try {
            return this.postCommentService.postComment(feedId, xsecToken, content);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Result.fail(BizErrorCode.POST_COMMENT_FAILED, "发表评论失败");
        }
    }

    @Tool(description = "获取小红书用户主页，返回用户基本信息，关注、粉丝、获赞量及其笔记内容")
    public Result<UserProfileResponse> userProfile(@ToolParam(description = "user_id") String userId, @ToolParam(description = "xsec_token") String xsecToken) {
        try {
            return this.userProfileService.userProfile(userId, xsecToken);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Result.fail(BizErrorCode.GET_USER_PROFILE_FAILED, "获取用户主页失败");
        }
    }
}