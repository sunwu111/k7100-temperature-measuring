package com.zhjinrui.bean;

import java.io.Serializable;

public class CommonResponseEntity implements Serializable {
    public int type;
    public String content;
    public byte[] picByte;
    public int res;

    public CommonResponseEntity() {
    }

    public CommonResponseEntity(int type, String content, byte[] picByte) {
        this.type = type;
        this.content = content;
        this.picByte = picByte;
    }

}
