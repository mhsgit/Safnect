package com.populstay.common.base;

import android.app.Application;
import android.os.Handler;
import android.os.HandlerThread;

import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.BuildConfig;
import com.orhanobut.logger.CsvFormatStrategy;
import com.orhanobut.logger.DiskLogAdapter;
import com.orhanobut.logger.FormatStrategy;
import com.orhanobut.logger.LogStrategy;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;
import com.populstay.common.GlobalConstant;
import com.populstay.common.log.CrashHandler;
import com.populstay.common.log.CustomUncaughtExceptionHandler;
import com.populstay.common.log.LogToFile;
import com.populstay.common.log.MyDiskLogStrategy;

import java.io.File;

public class BaseApp extends Application {


    @Override
    public void onCreate() {
        super.onCreate();
        // 测试环境异常收集
        //initCollectCrashTool();
        Thread.setDefaultUncaughtExceptionHandler(new CustomUncaughtExceptionHandler(getApplicationContext()));
        // Logger 日志
        initLogger(true);
    }

    private void initCollectCrashTool() {
        LogToFile.init(this);
        CrashHandler.getInstance().init(getApplicationContext());
    }




    /**
     * 初始化 logger 日志工具
     *
     * @param isDebug 是否开发（调试）模式
     *                开发模式：true
     *                发布模式：false
     */
    private void initLogger(boolean isDebug) {
        if (isDebug) {
            // 本地log打印
            FormatStrategy formatStrategy1 = PrettyFormatStrategy.newBuilder()
                    .tag(GlobalConstant.APP_TAG)
                    .build();

            // 本地log记录
            String folder = getExternalCacheDir().getAbsolutePath() + File.separator + "logs";
            HandlerThread ht = new HandlerThread("AndroidFileLogger." + folder);
            ht.start();
            Handler handler = new MyDiskLogStrategy.WriteHandler(ht.getLooper(), folder, 500 * 1024);
            LogStrategy logStrategy = new MyDiskLogStrategy(handler);

            FormatStrategy formatStrategy = CsvFormatStrategy.newBuilder()
                    .logStrategy(logStrategy)
                    .tag(GlobalConstant.APP_TAG)
                    .build();

            Logger.addLogAdapter(new DiskLogAdapter(formatStrategy));
            Logger.addLogAdapter(new AndroidLogAdapter(formatStrategy1));
        } else {
            // 禁用 logger
            Logger.addLogAdapter(new AndroidLogAdapter() {
                @Override
                public boolean isLoggable(int priority, String tag) {
                    return BuildConfig.DEBUG;
                }
            });
        }

    }
}
