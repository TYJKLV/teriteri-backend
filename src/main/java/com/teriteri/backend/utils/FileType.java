package com.teriteri.backend.utils;

/**
 * @author tk
 * FileType：目前只有三个，cover、avatar、video
 */
public enum FileType {
    COVER("cover","封面"),
    AVATAR("avatar","头像"),
    VIDEO("video","视频");

    private final String type;

    private final String desc;

    FileType(String type,String desc){
        this.type = type;
        this.desc = desc;
    }

    public String getType(){
        return type;
    }
}
