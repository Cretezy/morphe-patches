/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3516
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import app.morphe.extension.shared.settings.BooleanSetting;

@SuppressWarnings("unused")
public final class HideMediaViewerOverlayPatch {

    /**
     * Declared here instead of in the shared Settings class, so the patch still works when
     * combined with another patch bundle whose copy of Settings is used instead of this one.
     */
    public static final BooleanSetting HIDE_MEDIA_VIEWER_OVERLAY = new BooleanSetting("morphe_hide_media_viewer_overlay", false);

    /**
     * @return If this patch was included during patching.
     */
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }

    /**
     * Injection point.
     * <p>
     * Called when the media viewer shows a post.
     *
     * @return True to start with the title, buttons and video controls hidden.
     */
    public static boolean hideOverlay() {
        return HIDE_MEDIA_VIEWER_OVERLAY.get();
    }
}
