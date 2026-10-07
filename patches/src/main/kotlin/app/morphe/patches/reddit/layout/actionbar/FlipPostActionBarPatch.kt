/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.actionbar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.misc.settings.settingsPatch
import app.morphe.patches.reddit.shared.Constants.COMPATIBILITY_REDDIT
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/reddit/patches/FlipPostActionBarPatch;"

@Suppress("unused")
val flipPostActionBarPatch = bytecodePatch(
    name = "Flip post action bar",
    description = "Adds options to move the vote and comment buttons of posts to the right side, " +
            "and to swap the vote and comment buttons."
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
        PostActionBarRowFingerprint.method.apply {
            // Calls to the action bar class: the appearance lookup, the vote buttons, then the comment button.
            val ownCalls = findInstructionIndicesReversedOrThrow(POST_ACTION_BAR_METHOD_CALL).asReversed()
            if (ownCalls.size < 3) throw PatchException("Could not find the action bar buttons")
            val (appearanceIndex, voteIndex, commentIndex) = ownCalls
            val appearanceReference = getInstruction(appearanceIndex).getReference<MethodReference>()!!
            val voteReference = getInstruction(voteIndex).getReference<MethodReference>()!!
            if (voteReference.parameterTypes.size != 5 ||
                voteReference.parameterTypes[1].toString() != appearanceReference.returnType
            ) {
                throw PatchException("Unexpected vote buttons call")
            }

            // Row measure policies, with the horizontal arrangement: the buttons row, then the comment row.
            val rowIndices = findInstructionIndicesReversedOrThrow(POST_ACTION_BAR_ROW_CALL).asReversed()
            if (rowIndices.size < 2) throw PatchException("Could not find the action bar rows")
            val (buttonsRowIndex, commentRowIndex) = rowIndices
            if (!(voteIndex < buttonsRowIndex && commentRowIndex < commentIndex)) {
                throw PatchException("Unexpected action bar layout")
            }

            val voteStateParameter = parameterTypes.indexOfFirst {
                it.toString() == voteReference.parameterTypes[0].toString()
            }
            val appearanceParameter = parameterTypes.indexOfFirst {
                it.toString() == appearanceReference.parameterTypes[0].toString()
            }
            val composerRegister = getInstruction<FiveRegisterInstruction>(voteIndex).registerF
            if (voteStateParameter < 0 || appearanceParameter < 0 || composerRegister > 15) {
                throw PatchException("Unexpected vote buttons parameters")
            }

            fun changeArrangement(index: Int, extensionMethod: String) {
                val arrangementRegister = getInstruction<FiveRegisterInstruction>(index).registerC
                if (arrangementRegister > 15) throw PatchException("Arrangement register out of range")
                val arrangementType = getInstruction(index).getReference<MethodReference>()!!.parameterTypes[0]
                addInstructionsAtControlFlowLabel(
                    index,
                    """
                        invoke-static { v$arrangementRegister }, $EXTENSION_CLASS->$extensionMethod(Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v$arrangementRegister
                        check-cast v$arrangementRegister, $arrangementType
                    """
                )
            }

            // From the last change to the first, so the indices stay valid.

            // Show the vote buttons in the comment row, before the comment button.
            val registerProvider = getFreeRegisterProvider(commentIndex, 4, composerRegister)
            val stateRegister = registerProvider.getFreeRegister4Bit()
            val appearanceRegister = registerProvider.getFreeRegister4Bit()
            val modifierRegister = registerProvider.getFreeRegister4Bit()
            val flagsRegister = registerProvider.getFreeRegister4Bit()
            addInstructionsWithLabels(
                commentIndex,
                """
                    invoke-static {}, $EXTENSION_CLASS->moveVoteButtons()Z
                    move-result v$stateRegister
                    if-eqz v$stateRegister, :comment
                    move-object/from16 v$stateRegister, p$voteStateParameter
                    move-object/from16 v$appearanceRegister, p$appearanceParameter
                    invoke-static { v$appearanceRegister }, $appearanceReference
                    move-result-object v$appearanceRegister
                    const/4 v$modifierRegister, 0x0
                    const/4 v$flagsRegister, 0x0
                    invoke-static { v$stateRegister, v$appearanceRegister, v$modifierRegister, v$composerRegister, v$flagsRegister }, $voteReference
                """,
                ExternalLabel("comment", getInstruction(commentIndex))
            )

            // Place the vote and comment buttons in the comment row, at its end when flipped,
            // and the other buttons before the comment row when flipped.
            changeArrangement(commentRowIndex, "getCommentRowArrangement")
            changeArrangement(buttonsRowIndex, "getButtonsRowArrangement")

            // Don't show the vote buttons before the buttons row.
            val skipRegister = getFreeRegisterProvider(voteIndex, 1).getFreeRegister4Bit()
            addInstructionsWithLabels(
                voteIndex,
                """
                    invoke-static {}, $EXTENSION_CLASS->moveVoteButtons()Z
                    move-result v$skipRegister
                    if-nez v$skipRegister, :skip
                """,
                ExternalLabel("skip", getInstruction(voteIndex + 1))
            )
        }

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
