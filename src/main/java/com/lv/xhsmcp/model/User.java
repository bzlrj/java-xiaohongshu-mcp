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
public class User implements Serializable {
    @JsonProperty("userId")
    @JsonAlias({"userId","user_id"})
    private String userId;

    private String nickname;

    // 后端可能同时返回 nickName 和 nickname，两者都保留
    @JsonProperty("nickName")
    @JsonAlias({"nickName","nick_name"})
    private String nickName;

    private String avatar;

    @JsonProperty("xsecToken")
    @JsonAlias({"xsecToken","xsec_token"})
    private String xsecToken;



}
