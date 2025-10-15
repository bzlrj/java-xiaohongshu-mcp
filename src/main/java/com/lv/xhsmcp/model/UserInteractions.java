package com.lv.xhsmcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserInteractions implements Serializable {
    private String type;   // follows / fans / interaction
    private String name;   // 关注 / 粉丝 / 获赞与收藏
    private String count;  // 数量
}
