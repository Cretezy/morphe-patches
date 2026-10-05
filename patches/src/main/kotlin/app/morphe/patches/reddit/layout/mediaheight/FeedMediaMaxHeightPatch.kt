/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.mediaheight

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.reddit.layout.fullwidth.FeedImageMaxHeightFingerprint
import app.morphe.patches.reddit.layout.fullwidth.FeedImageSizeFingerprint
import app.morphe.patches.reddit.layout.fullwidth.FeedVideoHeightFingerprint
import app.morphe.patches.reddit.misc.settings.settingsPatch
import app.morphe.patches.reddit.shared.Constants.COMPATIBILITY_REDDIT
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/reddit/patches/FeedMediaMaxHeightPatch;"

private val FOUR_THIRDS_BITS = (4f / 3f).toRawBits()

/**
 * Replaces each `width * 4 / 3` with the configured maximum height for that width.
 */
private fun MutableMethod.replaceIntMaxHeight(): Int {
    val list = instructions.toList()
    val indices = (0 until list.lastIndex).filter { index ->
        val multiply = list[index]
        val divide = list[index + 1]
        (multiply.opcode == Opcode.MUL_INT_LIT8 || multiply.opcode == Opcode.MUL_INT_LIT16) &&
                (multiply as NarrowLiteralInstruction).narrowLiteral == 4 &&
                (divide.opcode == Opcode.DIV_INT_LIT8 || divide.opcode == Opcode.DIV_INT_LIT16) &&
                (divide as NarrowLiteralInstruction).narrowLiteral == 3 &&
                (divide as TwoRegisterInstruction).registerB == (multiply as TwoRegisterInstruction).registerA
    }

    indices.reversed().forEach { index ->
        val multiply = list[index] as TwoRegisterInstruction
        val widthRegister = multiply.registerB
        val resultRegister = (list[index + 1] as TwoRegisterInstruction).registerA
        replaceInstruction(
            index,
            "invoke-static/range { v$widthRegister .. v$widthRegister }, $EXTENSION_CLASS->getMaxHeight(I)I"
        )
        replaceInstruction(index + 1, "move-result v$resultRegister")
    }
    return indices.size
}

/**
 * Replaces each 4:3 float constant with the configured maximum height to width ratio.
 */
private fun MutableMethod.replaceFloatMaxHeightRatio(): Int {
    val indices = instructions.withIndex().filter { (_, instruction) ->
        instruction.opcode == Opcode.CONST &&
                (instruction as NarrowLiteralInstruction).narrowLiteral == FOUR_THIRDS_BITS
    }.map { it.index }

    indices.reversed().forEach { index ->
        val register = (instructions[index] as OneRegisterInstruction).registerA
        replaceInstruction(index, "invoke-static { }, $EXTENSION_CLASS->getMaxHeightRatio()F")
        addInstruction(index + 1, "move-result v$register")
    }
    return indices.size
}

@Suppress("unused")
val feedMediaMaxHeightPatch = bytecodePatch(
    name = "Feed media max height",
    description = "Adds an option to change the maximum height of images, videos and galleries in the feed."
) {
    compatibleWith(COMPATIBILITY_REDDIT)

    dependsOn(settingsPatch)

    execute {
        // Media is cropped to at most 4:3 (height to width) of the media width.
        listOf(
            FeedImageMaxHeightFingerprint,
            FeedVideoHeightFingerprint,
            FeedGalleryHeightForWidthFingerprint
        ).forEach { fingerprint ->
            if (fingerprint.method.replaceIntMaxHeight() == 0) {
                throw PatchException("Could not find the max height of ${fingerprint.method.name}")
            }
        }

        listOf(
            FeedImageSizeFingerprint,
            GalleryHeightFingerprint,
            FeedVideoFingerprint
        ).forEach { fingerprint ->
            if (fingerprint.method.replaceFloatMaxHeightRatio() == 0) {
                throw PatchException("Could not find the max height ratio of ${fingerprint.method.name}")
            }
        }

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
