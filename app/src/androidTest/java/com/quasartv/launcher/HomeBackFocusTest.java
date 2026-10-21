package com.quasartv.launcher;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Platform-only regression runner can exercise the optimized release APK. */
public class HomeBackFocusTest extends Instrumentation {
    private Activity activity;
    private final List<View> transitions = new ArrayList<>();

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() { new Thread(this::checkFocus).start(); }

    private void checkFocus() {
        SharedPreferences preferences = getTargetContext().getSharedPreferences("quasartv_launcher", 0);
        Map<String, String> stored = new HashMap<>();
        for (String key : new String[]{"last_focused_package", "last_focused_source", "last_destination"}) {
            stored.put(key, preferences.getString(key, null));
        }
        int result = Activity.RESULT_OK;
        String message = "PASS: Home Back focus regression\n";
        try {
            activity = startActivitySync(homeIntent().addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
            long deadline = SystemClock.uptimeMillis() + 10000;
            while (SystemClock.uptimeMillis() < deadline) {
                ViewGroup row = (ViewGroup) view("favorites_row");
                if (row != null && row.getChildCount() > 1) break;
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                settle();
            }
            settle();
            ViewGroup favorites = (ViewGroup) view("favorites_row");
            require(favorites != null && favorites.getChildCount() > 0, "Favorites must load");
            View info = view("launcher_settings");
            View gear = view("tv_settings");
            ViewTreeObserver.OnGlobalFocusChangeListener listener = (old, next) -> transitions.add(next);
            runOnMainSync(() -> activity.getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(listener));
            checkStable(favorites.getChildAt(0), "First favorite");
            if (favorites.getChildCount() > 2) checkStable(favorites.getChildAt(1), "Second favorite");
            View input = source(view("home_inputs"));
            if (input != null) checkStable(input, "Input");
            View selected = activity.getCurrentFocus();
            for (View button : new View[]{info, gear}) {
                runOnMainSync(() -> button.requestFocus());
                settle();
                clear();
                backPair();
                require(activity.getCurrentFocus() == selected && !transitions.contains(info) && !transitions.contains(gear),
                        "Back from header must return to content once");
            }
            report("Both header buttons: Back returns to content once");
            runOnMainSync(() -> info.requestFocus());
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            settle();
            clear();
            backPair();
            require(view("favorites_row") != null && !transitions.contains(info) && !transitions.contains(gear),
                    "Back from Settings briefly focused the header");
            report("Settings: double Back returns Home without transient header focus");
            View currentShelf = view("favorites_row");
            View currentFocus = activity.getCurrentFocus();
            clear();
            for (int i = 0; i < 3; i++) { getTargetContext().startActivity(homeIntent()); settle(); }
            require(view("favorites_row") == currentShelf && activity.getCurrentFocus() == currentFocus && transitions.isEmpty(),
                    "Repeated HOME rebuilt the screen or moved focus");
            report("Repeated HOME preserves existing views and focus");
            runOnMainSync(() -> activity.getWindow().getDecorView().getViewTreeObserver().removeOnGlobalFocusChangeListener(listener));
            File file = new File(getTargetContext().getExternalFilesDir(null), "visual-smoke/home-back-stable-release.png");
            file.getParentFile().mkdirs();
            Bitmap bitmap = getUiAutomation().takeScreenshot();
            try (FileOutputStream output = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
            bitmap.recycle();
        } catch (Throwable error) {
            result = Activity.RESULT_CANCELED;
            message = "FAIL: " + Log.getStackTraceString(error) + "\n";
        }
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, String> entry : stored.entrySet()) {
            if (entry.getValue() == null) editor.remove(entry.getKey()); else editor.putString(entry.getKey(), entry.getValue());
        }
        require(editor.commit(), "Original focus preferences must restore");
        Bundle resultBundle = new Bundle();
        resultBundle.putString("stream", message);
        finish(result, resultBundle);
    }

    private View view(String name) {
        // Resource IDs differ between the debug test variant and shrunk release.
        return activity.findViewById(activity.getResources().getIdentifier(name, "id", getTargetContext().getPackageName()));
    }
    private void settle() { waitForIdleSync(); SystemClock.sleep(200); }
    private void clear() { runOnMainSync(transitions::clear); }
    private void backPair() { sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); settle(); }
    private void checkStable(View target, String label) {
        runOnMainSync(() -> target.requestFocus());
        settle();
        clear();
        for (int i = 0; i < 5; i++) {
            backPair();
            require(activity.getCurrentFocus() == target && transitions.isEmpty(), label + " Back pair changed focus");
        }
        report(label + ": five rapid Back pairs keep the same focus");
    }
    private View source(View root) {
        if (root == null) return null;
        if (root.isFocusable() && root.getContentDescription() != null) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = source(((ViewGroup) root).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
    private Intent homeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClassName(getTargetContext().getPackageName(), getTargetContext().getPackageName() + ".MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    private void report(String text) { Bundle data = new Bundle(); data.putString("stream", text + "\n"); sendStatus(0, data); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
