/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.layout.player.tapandhold

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.speed.custom.TapAndHoldSpeedFingerprint
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

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
            it.method.apply {
                // Right after the "already speeding up" check.
                val startIndex = it.instructionMatches[5].index + 1

                // The class that overrides the speed, and restores it when tap and hold ends.
                speedControllerType = getInstruction(
                    indexOfFirstInstructionOrThrow(startIndex, Opcode.IGET_OBJECT)
                ).getReference<FieldReference>()!!.type

                addInstructionsAtControlFlowLabel(
                    startIndex,
                    "invoke-static { }, $EXTENSION_CLASS->onTapAndHoldStart()V"
                )
            }
            it.clearMatch()
        }

        // Track when tap and hold ends. Restoring the speed is the only public method
        // of the controller without parameters and return value.
        mutableClassDefBy(speedControllerType).methods.single { method ->
            AccessFlags.PUBLIC.isSet(method.accessFlags) &&
                    !AccessFlags.CONSTRUCTOR.isSet(method.accessFlags) &&
                    method.returnType == "V" &&
                    method.parameters.isEmpty()
        }.addInstruction(
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
