package com.lv.xhsmcp.model;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class Cover implements Serializable {
    private Integer width;
    private Integer height;
    private String url;

    @JsonProperty("fileId")
    @JsonAlias({"fileId","file_id"})
    private String fileId;
    @JsonProperty("urlPre")
    @JsonAlias({"urlPre","url_pre"})
    private String urlPre;
    @JsonProperty("urlDefault")
    @JsonAlias({"urlDefault","url_default"})
    private String urlDefault;
    @JsonProperty("infoList")
    @JsonAlias({"infoList","info_list"})
    private List<ImageInfo> infoList;
}
