package com.zhjinrui.bean;

import java.io.Serializable;

public class CommonRequestEntity implements Serializable {
    private String id;
    private int type;
    private int channel;
    private String content;


    public String getContent() {
        return content;
    }

    public CommonRequestEntity(String id, int type, String content) {
        this.id = id;
        this.type = type;
        this.content = content;
    }

    public String getId() {
        return id;
    }

    public int getType() {
        return type;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    @Override
    public String toString() {
        return "CommonRequestEntity{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", channel=" + channel +
                ", content='" + content + '\'' +
                '}';
    }
}
