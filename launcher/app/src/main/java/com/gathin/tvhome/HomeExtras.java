package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * This fork's own theme keys, read from the same config.json as {@link Config} (the copy Config.load keeps in
 * the app's files folder, so it is there even before external storage is mounted):
 *
 * <ul>
 *   <li>{@code greeting_text}: a fixed greeting ("Hola") instead of the time-of-day one, in normal case;</li>
 *   <li>{@code video_wallpaper}: play the theme's {@code videos/} behind Home (true by default when there are
 *       videos);</li>
 *   <li>{@code video_dim}: 0 to 90, a black shade over the video for the clock's contrast (0 by default).</li>
 * </ul>
 */
final class HomeExtras {
    final String greetingText;
    final boolean videoWallpaper;
    final int videoDim;

    private HomeExtras(String greetingText, boolean videoWallpaper, int videoDim) {
        this.greetingText = greetingText;
        this.videoWallpaper = videoWallpaper;
        this.videoDim = videoDim;
    }

    /** Call after Config.load, which refreshes the copy this reads. */
    static HomeExtras load(Context context) {
        JSONObject json = read(new File(context.getFilesDir(), Config.FILE_NAME));
        if (json == null) return new HomeExtras("", true, 0);
        String greeting = json.optString("greeting_text", "").trim();
        boolean video = json.optBoolean("video_wallpaper", true);
        int dim = Math.max(0, Math.min(90, json.optInt("video_dim", 0)));
        return new HomeExtras(greeting, video, dim);
    }

    private static JSONObject read(File file) {
        if (!file.isFile() || file.length() > 1024 * 1024) return null;
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int off = 0;
            while (off < bytes.length) {
                int n = in.read(bytes, off, bytes.length - off);
                if (n < 0) break;
                off += n;
            }
            return new JSONObject(new String(bytes, 0, off, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }
}
