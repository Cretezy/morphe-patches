/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3516
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import app.morphe.extension.shared.settings.BooleanSetting;

@SuppressWarnings("unused")
public final class HideJoinConversationButtonPatch {

    /**
     * Declared here instead of in the shared Settings class, so the patch still works when
     * combined with another patch bundle whose copy of Settings is used instead of this one.
     */
    public static final BooleanSetting HIDE_JOIN_CONVERSATION_BUTTON = new BooleanSetting("morphe_hide_join_conversation_button", false);

    /**
     * @return If this patch was included during patching.
     */
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }

    /**
     * Injection point.
     *
     * @return If the media viewer shows the dock with the "See the conversation" button below the media.
     */
    public static boolean showDock(boolean original) {
        return original && !HIDE_JOIN_CONVERSATION_BUTTON.get();
    }

    /**
     * Injection point.
     *
     * @return If a media viewer page skips the status bar padding. Images and videos skip it,
     *         but without the dock they would then be centered too high.
     */
    public static boolean skipStatusBarPadding(boolean original) {
        return original && !HIDE_JOIN_CONVERSATION_BUTTON.get();
    }
}
