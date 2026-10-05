/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.mediaviewer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.reddit.misc.settings.settingsPatch
import app.morphe.patches.reddit.shared.Constants.COMPATIBILITY_REDDIT
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/reddit/patches/MediaViewerBlackBackgroundPatch;"

@Suppress("unused")
val mediaViewerBlackBackgroundPatch = bytecodePatch(
    name = "Media viewer black background",
    description = "Adds an option to use a black background behind images and videos in the media viewer, " +
            "instead of the theme background color."
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
        // Method to the indices of its background calls.
        val backgrounds = mutableMapOf<String, Pair<MutableMethod, MutableSet<Int>>>()
        fun addBackground(method: MutableMethod, backgroundIndex: Int) {
            val key = method.definingClass + method.name + method.parameterTypes + method.returnType
            backgrounds.getOrPut(key) { method to mutableSetOf() }.second += backgroundIndex
        }

        val missingTags = MEDIA_VIEWER_BACKGROUND_TAGS.toMutableSet()
        MediaViewerBackgroundFingerprint.matchAll().forEach { match ->
            match.method.apply {
                findInstructionIndicesReversedOrThrow(mediaViewerBackgroundTagFilter).forEach { tagIndex ->
                    missingTags -= getInstruction(tagIndex).getReference<StringReference>()!!.string
                    // The nearest background call before each tag.
                    addBackground(
                        this,
                        indexOfFirstInstructionReversedOrThrow(tagIndex, mediaViewerBackgroundCallFilter)
                    )
                }
            }
        }

        if (missingTags.isNotEmpty()) {
            throw PatchException("Could not find media viewer background tags: $missingTags")
        }

        MediaViewerBottomSheetMenuFingerprint.match().let { match ->
            addBackground(match.method, match.instructionMatches.last().index)
        }

        backgrounds.values.forEach { (method, backgroundIndices) ->
            backgroundIndices.map { backgroundIndex ->
                // The theme color is read right before.
                method.indexOfFirstInstructionReversedOrThrow(backgroundIndex, Opcode.MOVE_RESULT_WIDE)
            }.distinct().sortedDescending().forEach { colorIndex ->
                val register = method.getInstruction<OneRegisterInstruction>(colorIndex).registerA
                method.addInstructions(
                    colorIndex + 1,
                    """
                        invoke-static/range { v$register .. v${register + 1} }, $EXTENSION_CLASS->getBackgroundColor(J)J
                        move-result-wide v$register
                    """
                )
            }
        }

        // Top fade of each page.
        MediaViewerTopGradientFingerprint.method.apply {
            val colorIndex = indexOfFirstInstructionOrThrow(Opcode.MOVE_RESULT_WIDE)
            val register = getInstruction<OneRegisterInstruction>(colorIndex).registerA
            addInstructions(
                colorIndex + 1,
                """
                    invoke-static/range { v$register .. v${register + 1} }, $EXTENSION_CLASS->getBackgroundColor(J)J
                    move-result-wide v$register
                """
            )
        }

        // Video player letterbox. The player is shared with the feed,
        // so the extension checks the composition context is the media viewer.
        val localContextField = LocalContextFingerprint.instructionMatches.first()
            .getInstruction<ReferenceInstruction>().reference
        VideoPlayerBackgroundFingerprint.let { match ->
            match.method.apply {
                val backgroundIndex = match.instructionMatches.last().index

                // The theme color read, with the composer.
                val themeRead = match.instructionMatches[1].getInstruction<FiveRegisterInstruction>()
                val composerRegister = themeRead.registerC
                val readLocal = (themeRead as ReferenceInstruction).reference

                // The shape is loaded right before the background call, so its register is free.
                val shapeIndex = backgroundIndex - 1
                if (getInstruction(shapeIndex).opcode != Opcode.SGET_OBJECT) {
                    throw PatchException("Unexpected video player background shape")
                }
                val freeRegister = getInstruction<OneRegisterInstruction>(shapeIndex).registerA
                val colorRegister = getInstruction<FiveRegisterInstruction>(backgroundIndex).registerD
                if (maxOf(composerRegister, freeRegister, colorRegister + 1) > 15) {
                    throw PatchException("Video player background registers out of range")
                }

                addInstructions(
                    shapeIndex,
                    """
                        sget-object v$freeRegister, $localContextField
                        invoke-virtual { v$composerRegister, v$freeRegister }, $readLocal
                        move-result-object v$freeRegister
                        invoke-static { v$colorRegister, v${colorRegister + 1}, v$freeRegister }, $EXTENSION_CLASS->getVideoPlayerBackgroundColor(JLjava/lang/Object;)J
                        move-result-wide v$colorRegister
                    """
                )
            }
        }

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
