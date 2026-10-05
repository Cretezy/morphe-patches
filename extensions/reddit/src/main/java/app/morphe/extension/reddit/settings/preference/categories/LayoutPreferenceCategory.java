/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.settings.preference.categories;

import static app.morphe.extension.shared.StringRef.str;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.reddit.patches.DisableModernHomePatch;
import app.morphe.extension.reddit.patches.DisableScreenshotPopupPatch;
import app.morphe.extension.reddit.patches.CustomFontPatch;
import app.morphe.extension.reddit.patches.FeedMediaMaxHeightPatch;
import app.morphe.extension.reddit.patches.ForceSystemFontPatch;
import app.morphe.extension.reddit.patches.FullWidthFeedMediaPatch;
import app.morphe.extension.reddit.patches.HideAskButtonPatch;
import app.morphe.extension.reddit.patches.HideCommunitiesShelf;
import app.morphe.extension.reddit.patches.HideJoinConversationButtonPatch;
import app.morphe.extension.reddit.patches.HideMediaViewerOverlayPatch;
import app.morphe.extension.reddit.patches.HideTrendingShelvesPatch;
import app.morphe.extension.reddit.patches.KeepFeedPositionPatch;
import app.morphe.extension.reddit.patches.MediaViewerBlackBackgroundPatch;
import app.morphe.extension.reddit.patches.MediaViewerFadePatch;
import app.morphe.extension.reddit.patches.RemoveSubRedditDialogPatch;
import app.morphe.extension.reddit.patches.ShowViewCountPatch;
import app.morphe.extension.reddit.settings.Settings;
import app.morphe.extension.reddit.settings.preference.BooleanSettingPreference;
import app.morphe.extension.reddit.settings.preference.CustomFontFilePreference;
import app.morphe.extension.reddit.settings.preference.CustomFontTogglePreference;
import app.morphe.extension.reddit.settings.preference.ForceSystemFontPreference;
import app.morphe.extension.reddit.settings.preference.IntegerSettingPreference;

@SuppressWarnings("deprecation")
public class LayoutPreferenceCategory extends ConditionalPreferenceCategory {
    public LayoutPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle(str("morphe_screen_layout_title"));
    }

    @Override
    public boolean getSettingsStatus() {
        return DisableModernHomePatch.isPatchIncluded() ||
                DisableScreenshotPopupPatch.isPatchIncluded() ||
                CustomFontPatch.isPatchIncluded() ||
                ForceSystemFontPatch.isPatchIncluded() ||
                HideAskButtonPatch.isPatchIncluded() ||
                HideCommunitiesShelf.isPatchIncluded() ||
                HideTrendingShelvesPatch.isPatchIncluded() ||
                FullWidthFeedMediaPatch.isPatchIncluded() ||
                MediaViewerFadePatch.isPatchIncluded() ||
                KeepFeedPositionPatch.isPatchIncluded() ||
                FeedMediaMaxHeightPatch.isPatchIncluded() ||
                HideMediaViewerOverlayPatch.isPatchIncluded() ||
                HideJoinConversationButtonPatch.isPatchIncluded() ||
                MediaViewerBlackBackgroundPatch.isPatchIncluded() ||
                RemoveSubRedditDialogPatch.isPatchIncluded();
    }

    @Override
    public void addPreferences(Context context) {
        if (CustomFontPatch.isPatchIncluded()) {
            addPreference(new CustomFontTogglePreference(context));
            addPreference(new CustomFontFilePreference(
                    context,
                    Settings.CUSTOM_FONT_FILE_PATH
            ));
        }

        if (DisableModernHomePatch.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    Settings.DISABLE_MODERN_HOME
            ));
        }

        if (DisableScreenshotPopupPatch.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    Settings.DISABLE_SCREENSHOT_POPUP
            ));
        }

        if (ForceSystemFontPatch.isPatchIncluded()) {
            addPreference(new ForceSystemFontPreference(context));
        }

        if (HideAskButtonPatch.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    Settings.HIDE_ASK_BUTTON
            ));
        }

        if (HideCommunitiesShelf.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    Settings.HIDE_COMMUNITIES_SHELF
            ));
        }

        if (HideTrendingShelvesPatch.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    Settings.HIDE_TRENDING_SHELVES
            ));
        }

        if (RemoveSubRedditDialogPatch.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    Settings.REMOVE_NSFW_DIALOG
            ));
            addPreference(new BooleanSettingPreference(
                    context,
                    Settings.REMOVE_NOTIFICATION_DIALOG
            ));
        }

        if (ShowViewCountPatch.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    Settings.SHOW_VIEW_COUNT
            ));
        }

        if (FullWidthFeedMediaPatch.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    FullWidthFeedMediaPatch.FULL_WIDTH_FEED_MEDIA
            ));
        }

        if (MediaViewerFadePatch.isPatchIncluded()) {
            addPreference(new IntegerSettingPreference(
                    context,
                    MediaViewerFadePatch.MEDIA_VIEWER_FADE
            ));
        }

        if (KeepFeedPositionPatch.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    KeepFeedPositionPatch.KEEP_FEED_POSITION
            ));
        }

        if (FeedMediaMaxHeightPatch.isPatchIncluded()) {
            addPreference(new IntegerSettingPreference(
                    context,
                    FeedMediaMaxHeightPatch.FEED_MEDIA_MAX_HEIGHT
            ));
        }

        if (HideMediaViewerOverlayPatch.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    HideMediaViewerOverlayPatch.HIDE_MEDIA_VIEWER_OVERLAY
            ));
        }

        if (HideJoinConversationButtonPatch.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    HideJoinConversationButtonPatch.HIDE_JOIN_CONVERSATION_BUTTON
            ));
        }

        if (MediaViewerBlackBackgroundPatch.isPatchIncluded()) {
            addPreference(new BooleanSettingPreference(
                    context,
                    MediaViewerBlackBackgroundPatch.MEDIA_VIEWER_BLACK_BACKGROUND
            ));
        }
    }
}
