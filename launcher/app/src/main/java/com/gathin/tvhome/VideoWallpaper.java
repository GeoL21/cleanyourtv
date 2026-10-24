package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A muted, looping video behind Home, from the theme folder's {@code videos/} (pushed from the computer).
 *
 * <p>It only plays while Home is in front: {@link #stop()} (onPause) releases the player, so an app that opens
 * gets the hardware decoder back and nothing decodes behind it. {@link #start()} (onResume) prepares it again,
 * and the view fades in on the first rendered frame instead of flashing black. With {@code videos/} empty or
 * missing it adds nothing to Home.
 */
final class VideoWallpaper implements TextureView.SurfaceTextureListener {
    private static final String TAG = "TvHome.Video";
    private static final String PREFS = "video_wallpaper";

    private final FrameLayout root;
    private final TextureView view;
    private final View shade;
    private final List<File> videos;
    private final long interval;
    private final SharedPreferences preferences;
    private Surface surface;
    private MediaPlayer player;
    private boolean active;
    private boolean closed;

    /** The theme folder's videos, sorted by name: .mp4, .mkv and .webm up to 200 MB each, at most 50. */
    static List<File> find(File themeDir) {
        List<File> result = new ArrayList<>();
        File[] entries = themeDir == null ? null : new File(themeDir, "videos").listFiles();
        if (entries == null) return result;
        Arrays.sort(entries, Comparator.comparing(File::getName));
        for (File entry : entries) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (entry.isFile() && entry.length() > 0 && entry.length() <= 200L * 1024 * 1024
                    && (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm"))) {
                result.add(entry);
                if (result.size() == 50) break;
            }
        }
        return result;
    }

    /** Null when the theme has no videos. */
    static VideoWallpaper create(Context context, FrameLayout root, int rotationMinutes, int dimPercent) {
        List<File> videos = find(Config.dir(context));
        return videos.isEmpty() ? null : new VideoWallpaper(context, root, videos, rotationMinutes, dimPercent);
    }

    private VideoWallpaper(Context context, FrameLayout root, List<File> videos, int rotationMinutes,
                           int dimPercent) {
        this.root = root;
        this.videos = videos;
        interval = Math.max(0, rotationMinutes) * 60_000L;
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        view = new TextureView(context);
        view.setOpaque(true);
        view.setAlpha(0f);
        view.setFocusable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setSurfaceTextureListener(this);
        FrameLayout.LayoutParams fill = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(view, 0, fill);
        int dim = Math.max(0, Math.min(90, dimPercent));
        if (dim > 0) {
            shade = new View(context);
            shade.setBackgroundColor((dim * 255 / 100) << 24);
            shade.setFocusable(false);
            shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(shade, 1, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            shade = null;
        }
    }

    /** Name of the video on screen (or about to be), for a caption or the settings page. */
    String currentName() {
        return current().getName();
    }

    void start() {
        if (closed) return;
        active = true;
        rotateIfDue();
        play();
    }

    void stop() {
        active = false;
        release();
    }

    void close() {
        closed = true;
        active = false;
        release();
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    /** Skip to the next video now (a remote button or the control centre). */
    void next() {
        int index = (index() + 1) % videos.size();
        preferences.edit().putInt("index", index).putLong("changed", System.currentTimeMillis()).apply();
        if (active) {
            release();
            play();
        }
    }

    private int index() {
        int i = preferences.getInt("index", 0);
        return i >= 0 && i < videos.size() ? i : 0;
    }

    private File current() {
        return videos.get(index());
    }

    private void rotateIfDue() {
        long changed = preferences.getLong("changed", 0);
        long now = System.currentTimeMillis();
        if (changed == 0) {
            preferences.edit().putLong("changed", now).apply();
        } else if (interval > 0 && videos.size() > 1 && now - changed >= interval) {
            preferences.edit().putInt("index", (index() + 1) % videos.size()).putLong("changed", now).apply();
        }
    }

    private void play() {
        if (!active || closed || surface == null || player != null) return;
        File file = current();
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setSurface(surface);
            mp.setDataSource(file.getPath());
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mp.setOnPreparedListener(prepared -> {
                if (player != prepared) return;
                fit(prepared.getVideoWidth(), prepared.getVideoHeight());
                prepared.start();
            });
            mp.setOnInfoListener((p, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && p == player) {
                    root.setBackground(null);   // the video covers the screen
                    view.animate().alpha(1f).setDuration(350).start();
                }
                return false;
            });
            mp.setOnErrorListener((p, what, extra) -> {
                Log.w(TAG, "Cannot play " + file.getName() + " (" + what + "/" + extra + ")");
                if (p == player) {
                    release();
                    if (videos.size() > 1) {
                        preferences.edit().putInt("index", (index() + 1) % videos.size()).apply();
                    }
                }
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, "Cannot open " + file.getName(), e);
            release();
        }
    }

    private void release() {
        view.animate().cancel();
        view.setAlpha(0f);
        MediaPlayer mp = player;
        player = null;
        if (mp != null) {
            try {
                mp.release();
            } catch (Exception ignored) {
                // already gone
            }
        }
    }

    /** Centre-crop: the video fills the screen and its edges are cut, never stretched. */
    private void fit(int videoWidth, int videoHeight) {
        int w = view.getWidth(), h = view.getHeight();
        if (videoWidth <= 0 || videoHeight <= 0 || w <= 0 || h <= 0) return;
        float scale = Math.max((float) w / videoWidth, (float) h / videoHeight);
        float sx = videoWidth * scale / w, sy = videoHeight * scale / h;
        Matrix matrix = new Matrix();
        matrix.setScale(sx, sy, w / 2f, h / 2f);
        view.setTransform(matrix);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        surface = new Surface(texture);
        play();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        MediaPlayer mp = player;
        if (mp != null) {
            try {
                fit(mp.getVideoWidth(), mp.getVideoHeight());
            } catch (Exception ignored) {
                // not prepared yet; onPrepared fits it
            }
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        release();
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        // nothing per frame
    }
}
