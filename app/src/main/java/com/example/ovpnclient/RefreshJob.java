package com.example.ovpnclient;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Refreshes the server list once an hour, also after reboot. */
public class RefreshJob extends JobService {
    private static final int JOB_ID = 4201;

    public static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, RefreshJob.class))
            .setPeriodic(60L * 60L * 1000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build());
    }

    @Override public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            boolean failed = false;
            try { ServerRepo.refresh(getApplicationContext()); } catch (Exception e) { failed = true; }
            jobFinished(params, failed);
        }).start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
