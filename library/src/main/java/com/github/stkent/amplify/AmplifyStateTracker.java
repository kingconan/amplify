package com.github.stkent.amplify;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import static android.content.pm.PackageManager.GET_ACTIVITIES;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class AmplifyStateTracker {

    public enum ActionType {
        USER_GAVE_RATING,
        USER_DECLINED_RATING,
        USER_GAVE_FEEDBACK,
        USER_DECLINED_FEEDBACK,
        APP_CRASHED
    }

    private static final long RATING_PROMPT_COOLDOWN_TIME_MS = DAYS.toMillis(7);
    private static final long DEFAULT_LAST_ACTION_TIME_MS = 0;
    private static final int DEFAULT_RATED_VERSION_CODE = -1;
    private static final int DEFAULT_LAST_FEEDBACK_VERSION_CODE = -1;

    private static AmplifyStateTracker instance;

    private AmplifyStateTracker() {
    }

    public static AmplifyStateTracker getInstance() {
        synchronized (AmplifyStateTracker.class) {
            if (instance == null) {
                instance = new AmplifyStateTracker();
            }
        }

        return instance;
    }

    public boolean shouldAskForRating() {
        if (userHasRatedApp()) {
            Log.d(Constants.LOG_TAG, "User has already rated the app. Should not ask for rating/feedback.");
            return false;
        }

        if (!isGooglePlayInstalled(AppProvider.getAppContext())) {
            Log.d(Constants.LOG_TAG, "Play Store is not installed. Should not ask for rating/feedback.");
            return false;
        }

        if (userHasGivenFeedbackForCurrentVersion()) {
            Log.d(Constants.LOG_TAG, "User has already given feedback for this app version. Should not ask for rating/feedback.");
            return false;
        }

        if (isInCooldownMode()) {
            Log.d(Constants.LOG_TAG, "Last negative action (crash, rating declined, feedback declined) was less than "
                    + MILLISECONDS.toDays(RATING_PROMPT_COOLDOWN_TIME_MS) + " days ago. Should not ask for rating/feedback.");
            return false;
        }

        return true;
    }

    public void notify(final ActionType actionType) {
        final SharedPreferences.Editor editor = Settings.getEditor();

        switch (actionType) {
            case USER_GAVE_RATING:
                editor.putInt(Constants.RATED_VERSION_CODE, BuildConfig.VERSION_CODE);
                break;
            case USER_GAVE_FEEDBACK:
                editor.putInt(Constants.LAST_FEEDBACK_VERSION_CODE, BuildConfig.VERSION_CODE);
                break;
            case USER_DECLINED_RATING:
            case USER_DECLINED_FEEDBACK:
            case APP_CRASHED:
                editor.putLong(Constants.LAST_NEGATIVE_ACTION_TIME_MS, System.currentTimeMillis());
                break;
            default:
                break;
        }

        editor.apply();
    }

    public void reset() {
        final SharedPreferences.Editor editor = Settings.getEditor();

        Log.d(Constants.LOG_TAG, "Reset rating tracker state.");
        editor.remove(Constants.RATED_VERSION_CODE);
        editor.remove(Constants.LAST_FEEDBACK_VERSION_CODE);
        editor.remove(Constants.LAST_NEGATIVE_ACTION_TIME_MS);
        editor.apply();
    }

    public void initRatingExceptionHandler() {
        final Thread.UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();

        if (!(currentHandler instanceof AmplifyExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new AmplifyExceptionHandler(currentHandler));
        }
    }

    private boolean userHasRatedApp() {
        final int ratedVersionCode = Settings.getSharedPreferences().getInt(Constants.RATED_VERSION_CODE, DEFAULT_RATED_VERSION_CODE);
        return ratedVersionCode != DEFAULT_RATED_VERSION_CODE;
    }

    private boolean isGooglePlayInstalled(final Context context) {
        final PackageManager pm = context.getPackageManager();
        boolean playServicesInstalled;

        try {
            final PackageInfo info = pm.getPackageInfo("com.android.vending", GET_ACTIVITIES);
            final String label = (String) info.applicationInfo.loadLabel(pm);
            playServicesInstalled = label != null && !label.equals("Market");
        } catch (PackageManager.NameNotFoundException e) {
            playServicesInstalled = false;
        }

        return playServicesInstalled;
    }

    private boolean userHasGivenFeedbackForCurrentVersion() {
        final int lastFeedbackVersionCode = Settings.getSharedPreferences().getInt(Constants.LAST_FEEDBACK_VERSION_CODE,
                DEFAULT_LAST_FEEDBACK_VERSION_CODE);
        return lastFeedbackVersionCode < BuildConfig.VERSION_CODE;
    }

    private boolean isInCooldownMode() {
        final long timeSinceLastNegativeAction = System.currentTimeMillis() - Settings.getSharedPreferences().getLong(
                Constants.LAST_NEGATIVE_ACTION_TIME_MS, DEFAULT_LAST_ACTION_TIME_MS);
        return timeSinceLastNegativeAction < RATING_PROMPT_COOLDOWN_TIME_MS;
    }

}