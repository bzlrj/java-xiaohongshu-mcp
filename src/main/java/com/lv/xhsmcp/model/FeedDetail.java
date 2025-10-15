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
public class FeedDetail implements Serializable {
    @JsonProperty("noteId")
    private String noteId;

    private String xsecToken;
    private String title;
    private String desc;
    private String type;
    private Long time;          // epoch 秒
    private String ipLocation;
    private User user;
    private InteractInfo interactInfo;
    private List<DetailImageInfo> imageList;
}
