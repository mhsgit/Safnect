package com.populstay.common.log;

import android.content.Context;
import android.view.Gravity;
import android.widget.Toast;

import java.util.Arrays;

public class CustomUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    private Context context;

    public CustomUncaughtExceptionHandler(Context context){
        this.context = context;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        // 在这里处理未捕获的异常
        PeachLogger.e("CustomExceptionHandler", "Unhandled exception: " + throwable);
        // 可以在这里编写自定义的异常处理逻辑，例如日志记录、错误报告等
        // 请注意，如果不调用默认的异常处理程序，应用程序可能会被终止
        PeachLogger.e("CustomExceptionHandler", "getMessage= " + throwable.getMessage());
        PeachLogger.e("CustomExceptionHandler", "getCause= " + throwable.getCause());
        PeachLogger.e("CustomExceptionHandler", "getStackTrace= " + Arrays.toString(throwable.getStackTrace()));

        Toast toast = Toast.makeText(context, "程序出错，即将退出", Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();

        //Sleep一会后结束程序
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            PeachLogger.e("CustomExceptionHandler", "InterruptedException exception: " + e);
        }
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);

        // 调用默认的异常处理程序
        //Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, throwable);
    }
}

