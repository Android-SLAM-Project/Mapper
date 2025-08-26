package com.mapper.imuslam;

// MyApp.java
import android.app.Application;

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LogManager.initialize(this); // Initialize logging
    }
}
