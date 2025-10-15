package com.lv.xhsmcp.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InteractInfo implements Serializable {
    private Boolean liked;
    @JsonProperty("likedCount")
    @JsonAlias({"likedCount","liked_count"})
    private String likedCount;

    @JsonProperty("sharedCount")
    @JsonAlias({"sharedCount","shared_count"})
    private String sharedCount;

    @JsonProperty("commentCount")
    @JsonAlias({"commentCount","comment_count"})
    private String commentCount;

    @JsonProperty("collectedCount")
    @JsonAlias({"collectedCount","collected_count"})
    private String collectedCount;
    private Boolean collected;
}
