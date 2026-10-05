/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.mediaviewer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.reddit.misc.settings.settingsPatch
import app.morphe.patches.reddit.shared.Constants.COMPATIBILITY_REDDIT
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
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
        fun Instruction.isBackgroundCall(): Boolean {
            if (opcode != Opcode.INVOKE_STATIC && opcode != Opcode.INVOKE_STATIC_RANGE) return false
            // Modifier.background(color, shape).
            val parameters = ((this as ReferenceInstruction).reference as MethodReference).parameterTypes
            return parameters.size == 3 && parameters[1].toString() == "J"
        }

        // Method to the indices of its background calls.
        val backgrounds = mutableMapOf<String, Pair<MutableMethod, MutableSet<Int>>>()
        fun addBackground(method: MutableMethod, backgroundIndex: Int) {
            val key = method.definingClass + method.name + method.parameterTypes + method.returnType
            backgrounds.getOrPut(key) { method to mutableSetOf() }.second += backgroundIndex
        }

        mediaViewerBackgroundFingerprints.flatMap { it.matchAll() }.forEach { match ->
            val list = match.method.instructions.toList()
            list.indices.filter { index ->
                val reference = (list[index] as? ReferenceInstruction)?.reference
                reference is StringReference && reference.string in MEDIA_VIEWER_BACKGROUND_TAGS
            }.forEach { tagIndex ->
                // Right before each tag.
                val backgroundIndex = (tagIndex downTo 0).firstOrNull { list[it].isBackgroundCall() }
                    ?: throw PatchException("Could not find the media viewer background")
                addBackground(match.method, backgroundIndex)
            }
        }

        MediaViewerBottomSheetMenuFingerprint.match().let { match ->
            val list = match.method.instructions.toList()
            val backgroundIndex = (match.instructionMatches.first().index until list.size)
                .firstOrNull { list[it].isBackgroundCall() }
                ?: throw PatchException("Could not find the bottom sheet menu background")
            addBackground(match.method, backgroundIndex)
        }

        backgrounds.values.forEach { (method, backgroundIndices) ->
            val list = method.instructions.toList()
            backgroundIndices.map { backgroundIndex ->
                // The theme color is read right before.
                (backgroundIndex downTo 0).first { list[it].opcode == Opcode.MOVE_RESULT_WIDE }
            }.distinct().sortedDescending().forEach { colorIndex ->
                val register = (list[colorIndex] as OneRegisterInstruction).registerA
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
            val colorIndex = instructions.indexOfFirst { it.opcode == Opcode.MOVE_RESULT_WIDE }
            if (colorIndex < 0) throw PatchException("Could not find the media viewer top fade color")
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
                val list = instructions.toList()
                val controlIndex = match.instructionMatches.first().index
                val backgroundIndex = (controlIndex until list.size).firstOrNull { list[it].isBackgroundCall() }
                    ?: throw PatchException("Could not find the video player background")

                // The theme color read, with the composer.
                val themeReadIndex = (controlIndex until backgroundIndex).first { index ->
                    val instruction = list[index]
                    if (instruction.opcode != Opcode.INVOKE_VIRTUAL) return@first false
                    val reference = (instruction as ReferenceInstruction).reference as MethodReference
                    reference.returnType == "Ljava/lang/Object;" && reference.parameterTypes.size == 1
                }
                val themeRead = list[themeReadIndex] as FiveRegisterInstruction
                val composerRegister = themeRead.registerC
                val readLocal = (themeRead as ReferenceInstruction).reference

                // The shape is loaded right before the background call, so its register is free.
                val shapeIndex = backgroundIndex - 1
                if (list[shapeIndex].opcode != Opcode.SGET_OBJECT) {
                    throw PatchException("Unexpected video player background shape")
                }
                val freeRegister = (list[shapeIndex] as OneRegisterInstruction).registerA
                val colorRegister = (list[backgroundIndex] as FiveRegisterInstruction).registerD
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
