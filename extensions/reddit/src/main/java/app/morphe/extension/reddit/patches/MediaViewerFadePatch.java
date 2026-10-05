/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.IntegerSetting;

@SuppressWarnings("unused")
public final class MediaViewerFadePatch {

    /**
     * Strength of the dark fade behind the caption and buttons in the media viewer, in percent.
     * 0 removes it, 100 is Reddit's default.
     * <p>
     * Declared here instead of in the shared Settings class, so the patch still works when
     * combined with another patch bundle whose copy of Settings is used instead of this one.
     */
    public static final IntegerSetting MEDIA_VIEWER_FADE = new IntegerSetting("morphe_media_viewer_fade", 100);

    /**
     * @return If this patch was included during patching.
     */
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }

    private static float getStrength() {
        int percent = MEDIA_VIEWER_FADE.get();
        if (percent <= 0) return 0f;
        if (percent >= 100) return 1f;
        return percent / 100f;
    }

    /**
     * Injection point.
     * <p>
     * Scales one color stop alpha of the fade gradient.
     */
    public static float scaleFadeAlpha(float alpha) {
        return alpha * getStrength();
    }

    /**
     * Injection point.
     * <p>
     * Reddit can replace the fade with a darker variant. Only allow that when
     * the fade is left at Reddit's default strength.
     */
    public static boolean useAlternateFade() {
        return MEDIA_VIEWER_FADE.get() >= 100;
    }

    /**
     * Injection point.
     * <p>
     * Newer versions choose between several fade styles. Only the legacy gradient can be
     * lowered, so use it when the fade is changed from Reddit's default.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Enum<?> getScrimStyle(Enum<?> style) {
        if (style == null || MEDIA_VIEWER_FADE.get() >= 100) return style;

        try {
            return Enum.valueOf((Class) style.getDeclaringClass(), "LEGACY_GRADIENT");
        } catch (Exception ex) {
            Logger.printException(() -> "getScrimStyle failure", ex);
            return style;
        }
    }
}
