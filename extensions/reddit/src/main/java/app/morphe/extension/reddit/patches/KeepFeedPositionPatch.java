/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3516
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import app.morphe.extension.shared.settings.BooleanSetting;

@SuppressWarnings("unused")
public final class KeepFeedPositionPatch {

    /**
     * Declared here instead of in the shared Settings class, so the patch still works when
     * combined with another patch bundle whose copy of Settings is used instead of this one.
     */
    public static final BooleanSetting KEEP_FEED_POSITION = new BooleanSetting("morphe_keep_feed_position", true);

    /**
     * @return If this patch was included during patching.
     */
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }

    /**
     * Injection point.
     * <p>
     * Called when a post, image or video is opened from a feed. Reddit remembers the post and,
     * when the feed is shown again, re-creates the feed scroll state with that post at the top.
     *
     * @return True to not remember the opened post, so the feed keeps its scroll position.
     */
    public static boolean skipRememberOpenedPost() {
        return KEEP_FEED_POSITION.get();
    }
}
