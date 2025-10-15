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
public class Feed implements Serializable {
    private String id;
    @JsonProperty("xsecToken")
    @JsonAlias({"xsecToken","xsec_token"})
    private String xsecToken;
    @JsonProperty("modelType")
    @JsonAlias({"modelType","model_type"})
    private String modelType;
    @JsonProperty("noteCard")
    @JsonAlias({"noteCard","note_card"})
    private NoteCard noteCard;
    @JsonProperty("index")
    private Integer index;
}
