/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3516
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import app.morphe.extension.shared.settings.IntegerSetting;

@SuppressWarnings("unused")
public final class FeedMediaMaxHeightPatch {

    /**
     * Reddit's default, 4:3 of the media width.
     */
    private static final int DEFAULT_PERCENT = 133;

    /**
     * Maximum media height, in percent of the media width.
     * <p>
     * Declared here instead of in the shared Settings class, so the patch still works when
     * combined with another patch bundle whose copy of Settings is used instead of this one.
     */
    public static final IntegerSetting FEED_MEDIA_MAX_HEIGHT = new IntegerSetting("morphe_feed_media_max_height", DEFAULT_PERCENT);

    /**
     * @return If this patch was included during patching.
     */
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }

    /**
     * Injection point.
     *
     * @return Maximum height to width ratio of media.
     */
    public static float getMaxHeightRatio() {
        int percent = FEED_MEDIA_MAX_HEIGHT.get();
        // Keep Reddit's exact ratio, as 133% is slightly smaller.
        if (percent == DEFAULT_PERCENT) return 4f / 3f;
        return Math.max(25, Math.min(percent, 500)) / 100f;
    }

    /**
     * Injection point.
     *
     * @return Maximum height of media, in pixels.
     */
    public static int getMaxHeight(int width) {
        if (FEED_MEDIA_MAX_HEIGHT.get() == DEFAULT_PERCENT) return width * 4 / 3;
        return (int) (width * getMaxHeightRatio());
    }
}
