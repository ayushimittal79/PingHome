package com.pinghome.app.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Every repository call runs Retrofit's synchronous execute() on this background executor and
 * hops back to the main thread to deliver the result - keeps ViewModels callback-based without
 * pulling in RxJava/coroutines for a Java codebase. */
public class AppExecutors {

    private static final AppExecutors INSTANCE = new AppExecutors();

    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppExecutors() {}

    public static AppExecutors getInstance() {
        return INSTANCE;
    }

    public void runBackground(Runnable runnable) {
        backgroundExecutor.execute(runnable);
    }

    public void runOnMain(Runnable runnable) {
        mainHandler.post(runnable);
    }
}
