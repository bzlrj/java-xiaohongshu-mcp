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
public class DetailImageInfo implements Serializable {
    private Integer width;
    private Integer height;
    private String urlDefault;
    private String urlPre;
    private Boolean livePhoto; // 可为空
}
