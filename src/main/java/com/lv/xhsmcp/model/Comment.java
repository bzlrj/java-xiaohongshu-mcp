package com.lv.xhsmcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Comment implements Serializable {
    private String id;

    @JsonProperty("noteId")
    private String noteId;

    private String content;
    private String likeCount;
    private Long createTime;     // epoch 秒
    private String ipLocation;
    private Boolean liked;

    // 字段名与结构稍异：原为 userInfo: User
    @JsonProperty("userInfo")
    private User userInfo;

    private String subCommentCount;
    private List<Comment> subComments;
    private List<String> showTags;
}
