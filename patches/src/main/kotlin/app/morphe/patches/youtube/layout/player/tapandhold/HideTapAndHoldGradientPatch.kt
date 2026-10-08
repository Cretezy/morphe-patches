/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3586
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.layout.player.tapandhold

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.speed.custom.TapAndHoldSpeedFingerprint
import app.morphe.util.addInstructionsAtControlFlowLabel

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/HideTapAndHoldGradientPatch;"

@Suppress("unused")
val hideTapAndHoldGradientPatch = bytecodePatch(
    name = "Hide tap and hold gradient",
    description = "Adds an option to stop the player controls gradient from appearing " +
            "when sliding your finger up while holding to speed up a fullscreen video.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        PreferenceScreen.PLAYER.addPreferences(
            SwitchPreference("morphe_hide_tap_and_hold_gradient")
        )

        // Track when tap and hold starts. Other patches change this method too,
        // so match it again before and after changing it.
        val speedControllerType: String
        TapAndHoldSpeedFingerprint.let {
            it.clearMatch()
            // The class that overrides the speed, and restores it when tap and hold ends.
            speedControllerType = it.instructionMatches[6].getFieldAccessed().type

            // Right after the "already speeding up" check.
            it.method.addInstructionsAtControlFlowLabel(
                it.instructionMatches[5].index + 1,
                "invoke-static { }, $EXTENSION_CLASS->onTapAndHoldStart()V"
            )
            it.clearMatch()
        }

        // Track when tap and hold ends.
        tapAndHoldSpeedRestoreFingerprint(speedControllerType).method.addInstruction(
            0,
            "invoke-static { }, $EXTENSION_CLASS->onTapAndHoldEnd()V"
        )

        // Sliding up in fullscreen drags the "More videos" panel, which also shows the
        // player controls. While holding, the controls stay hidden but their top and
        // bottom gradients still fade in.
        ShowControlsOnRelatedPanelDragFingerprint.method.addInstructionsWithLabels(
            0,
            """
                invoke-static { }, $EXTENSION_CLASS->hideControlsOnDrag()Z
                move-result v0
                if-eqz v0, :show
                return-void
                :show
                nop
            """
        )
    }
}
