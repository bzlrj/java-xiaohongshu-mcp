package com.lv.xhsmcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageObject implements Serializable {
    private String type;
    private String data;
    private String mimeType;

    public static ImageObject parseFromBase64(String base64){
        ImageObject imageObject = null;
        Pattern pattern = Pattern.compile("^data:([\\w/+.-]+);base64,(.*)$");
        Matcher matcher = pattern.matcher(base64);
        if (matcher.find()) {
            imageObject = new ImageObject();
            String mimeType = matcher.group(1); // 提取 image/png
            String base64Data = matcher.group(2); // 提取纯 Base64 内容
            imageObject.setMimeType(mimeType);
            imageObject.setData(base64Data);
        }
        return imageObject;
    }
}
