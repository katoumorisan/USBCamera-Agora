package com.katoumori.usbcamera_agora;

import android.app.Application;

import androidx.multidex.MultiDex;

public class MainApplication extends Application {

    private GlobalSettings globalSettings;

    @Override
    public void onCreate() {
        super.onCreate();
//        initExamples();
        MultiDex.install(this);
    }


    public GlobalSettings getGlobalSettings() {
        if(globalSettings == null){
            globalSettings = new GlobalSettings();
        }
        return globalSettings;
    }
}
