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
public class NoteCard implements Serializable {
    private String type;
    @JsonProperty("displayTitle")
    @JsonAlias({"displayTitle","display_title"})
    private String displayTitle;
    private User user;
    @JsonProperty("interactInfo")
    @JsonAlias({"interactInfo","interact_info"})
    private InteractInfo interactInfo;
    private Cover cover;
    private Video video; // 可为空
}
