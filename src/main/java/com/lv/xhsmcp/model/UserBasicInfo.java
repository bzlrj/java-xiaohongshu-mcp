package com.lv.xhsmcp.model;

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
public class UserBasicInfo implements Serializable {
    private Integer gender;

    @JsonProperty("ipLocation")
    private String ipLocation;

    private String desc;
    private String imageb;
    private String nickname;
    private String images;
    private String redId;
}
