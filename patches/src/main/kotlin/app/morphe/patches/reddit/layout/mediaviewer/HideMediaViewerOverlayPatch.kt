/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3516
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.mediaviewer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.misc.settings.settingsPatch
import app.morphe.patches.reddit.shared.Constants.COMPATIBILITY_REDDIT
import app.morphe.util.findFreeRegister
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.removeFlags
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/reddit/patches/HideMediaViewerOverlayPatch;"

@Suppress("unused")
val hideMediaViewerOverlayPatch = bytecodePatch(
    name = "Hide media viewer overlay",
    description = "Adds an option to hide the title, buttons and video controls when opening an image or video. " +
            "Tap the media to show them."
) {
    // Older versions hide the overlay from the video player instead of the chrome state.
    compatibleWith(
        with(COMPATIBILITY_REDDIT) {
            Compatibility(
                name = name!!,
                packageName = packageName!!,
                description = description,
                apkFileType = apkFileType,
                appIconColor = appIconColor,
                signatures = signatures,
                targets = targets.filter { it.isExperimental }
            )
        }
    )

    dependsOn(settingsPatch)

    execute {
        // The chrome state 'isVisible' field is toggled when tapping the media.
        val visibleField = FullBleedChromeStateToStringFingerprint.instructionMatches.last()
            .getFieldAccessed().apply {
                // The field is final, and is written from another class.
                removeFlags(AccessFlags.FINAL)
            }

        // Hide the overlay of each newly created chrome state.
        createFullBleedChromeStateFingerprint(
            FullBleedChromeStateToStringFingerprint.classDef.type
        ).method.apply {
            findInstructionIndicesReversedOrThrow(Opcode.RETURN_OBJECT)
                .forEach { index ->
                    val stateRegister = getInstruction<OneRegisterInstruction>(index).registerA
                    val freeRegister = findFreeRegister(index, stateRegister)
                    addInstructionsWithLabels(
                        index,
                        """
                            invoke-static { }, $EXTENSION_CLASS->hideOverlay()Z
                            move-result v$freeRegister
                            if-eqz v$freeRegister, :show_overlay
                            const/4 v$freeRegister, 0x0
                            iput-boolean v$freeRegister, v$stateRegister, $visibleField
                        """,
                        ExternalLabel("show_overlay", getInstruction(index))
                    )
                }
        }

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
