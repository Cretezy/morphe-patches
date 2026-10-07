/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.mediaviewer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.misc.settings.settingsPatch
import app.morphe.patches.reddit.shared.Constants.COMPATIBILITY_REDDIT
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/reddit/patches/HideJoinConversationButtonPatch;"

@Suppress("unused")
val hideJoinConversationButtonPatch = bytecodePatch(
    name = "Hide See the conversation button",
    description = "Adds an option to hide the \"See the conversation\" button at the bottom of the media viewer."
) {
    // Only tested on the experimental versions.
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
        // Don't show the dock. Its button is then not drawn, and the media is not padded for it,
        // like when the comments sheet is open.
        JoinConversationDockFingerprint.let {
            it.method.apply {
                val index = it.instructionMatches[2].index
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                addInstructions(
                    index,
                    """
                        invoke-static/range { v$register .. v$register }, $EXTENSION_CLASS->showDock(Z)Z
                        move-result v$register
                    """
                )
            }
        }

        // Without the dock, the media is centered between the top of the screen and the
        // navigation bar, so it is too high. Also pad images and videos for the status bar,
        // so the media is centered between the system bars.
        MediaViewerPageFingerprint.let {
            it.method.apply {
                val statusBarPaddingIndex = it.instructionMatches.last().index

                // Images and videos skip the padding.
                val skipIndex = statusBarPaddingIndex - 1
                val skipInstruction = getInstruction<BuilderInstruction21t>(skipIndex)
                if (skipInstruction.opcode != Opcode.IF_NEZ) throw PatchException("Unexpected instruction")
                val register = skipInstruction.registerA
                val skipTarget = skipInstruction.target.location.instruction!!

                // Replace the branch, so branches to it still run the check.
                replaceInstruction(
                    skipIndex,
                    "invoke-static/range { v$register .. v$register }, $EXTENSION_CLASS->skipStatusBarPadding(Z)Z"
                )
                addInstructionsWithLabels(
                    skipIndex + 1,
                    """
                        move-result v$register
                        if-nez v$register, :skip_padding
                    """,
                    ExternalLabel("skip_padding", skipTarget)
                )
            }
        }

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
