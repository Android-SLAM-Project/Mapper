package com.mapper.imuslam;

import android.graphics.Bitmap;

public class MyMapItem {
    private Bitmap thumbnail;   // can be null
    private String title;
    private String subtitle;
    public long timestamp;

    public MyMapItem(Bitmap thumbnail, String title, String subtitle,long timestamp) {
        this.thumbnail = thumbnail;
        this.title = title;
        this.subtitle = subtitle;
        this.timestamp=timestamp;
    }

    public Bitmap getThumbnail() { return thumbnail; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
}
