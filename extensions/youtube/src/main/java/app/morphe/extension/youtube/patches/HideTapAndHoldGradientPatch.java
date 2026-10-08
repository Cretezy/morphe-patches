/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3586
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches;

import app.morphe.extension.youtube.settings.Settings;

@SuppressWarnings("unused")
public final class HideTapAndHoldGradientPatch {

    /**
     * Touch and playback speed callbacks all run on the main thread.
     */
    private static boolean tapAndHoldActive;

    /**
     * Injection point.
     */
    public static void onTapAndHoldStart() {
        tapAndHoldActive = true;
    }

    /**
     * Injection point.
     */
    public static void onTapAndHoldEnd() {
        tapAndHoldActive = false;
    }

    /**
     * Injection point.
     *
     * @return If the player controls should not be shown by a vertical drag.
     */
    public static boolean hideControlsOnDrag() {
        return tapAndHoldActive && Settings.HIDE_TAP_AND_HOLD_GRADIENT.get();
    }
}
