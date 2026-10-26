package com.teriteri.backend.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoUploadInfoDTO {
    // TODO 根据数据库表字段类型为 tinyint(4)，type、auth、status应设计为 Byte，对吗
    private Integer uid;
    private String hash;
    private String title;
    private Integer type;
    // 作者声明  0：没有声明 1：未经允许禁止转载
    private Integer auth;
    private Double duration;
    private String mcId;
    private String scId;
    private String tags;
    private String descr;
    private String coverUrl;
}
