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
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.misc.settings.settingsPatch
import app.morphe.patches.reddit.shared.Constants.COMPATIBILITY_REDDIT
import app.morphe.util.findFreeRegister
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/reddit/patches/MediaViewerFadePatch;"

private const val SCRIM_STYLE_CLASS = "Lcom/reddit/fullbleedplayer/FbpChromeScrimStyle;"

@Suppress("unused")
val mediaViewerFadePatch = bytecodePatch(
    name = "Media viewer fade",
    description = "Adds an option to lower or remove the dark fade behind the caption when viewing images and videos."
) {
    // Only tested on the recommended and the experimental versions.
    compatibleWith(
        with(COMPATIBILITY_REDDIT) {
            Compatibility(
                name = name!!,
                packageName = packageName!!,
                description = description,
                apkFileType = apkFileType,
                appIconColor = appIconColor,
                signatures = signatures,
                targets = targets.filter { it.version == "2026.24.0" || it.isExperimental }
            )
        }
    )

    dependsOn(settingsPatch)

    execute {
        MediaViewerChromeFingerprint.method.apply {
            val instructionList = instructions.toList()

            fun methodReferenceAt(index: Int): MethodReference? {
                val instruction = instructionList[index]
                if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
                    return null
                }
                return (instruction as ReferenceInstruction).reference as? MethodReference
            }

            // The fade is a vertical gradient built from an array of (position, color) pairs.
            val builderIndex = instructionList.indices.firstOrNull { index ->
                methodReferenceAt(index)?.parameterTypes?.firstOrNull()?.toString() == "[Lkotlin/Pair;"
            } ?: throw PatchException("Could not find the fade gradient")
            val brushType = methodReferenceAt(builderIndex)!!.returnType

            // Reddit can swap in a darker static gradient right after building the default one.
            val alternateIndex = (builderIndex + 1..minOf(builderIndex + 6, instructionList.lastIndex))
                .firstOrNull { index ->
                    val instruction = instructionList[index]
                    instruction.opcode == Opcode.SGET_OBJECT &&
                            ((instruction as ReferenceInstruction).reference as FieldReference).type == brushType
                }

            // Each color stop is made with Color.copy(alpha), a static (long, float) -> long call.
            val alphaCallIndices = (0 until builderIndex).filter { index ->
                val reference = methodReferenceAt(index) ?: return@filter false
                reference.returnType == "J" && reference.parameterTypes.map { it.toString() } == listOf("J", "F")
            }
            if (alphaCallIndices.isEmpty()) throw PatchException("Could not find the fade colors")

            // Newer versions pick between several fade styles. Only the legacy gradient is built here.
            val scrimStyleIndex = parameterTypes.indexOfFirst { it.toString() == SCRIM_STYLE_CLASS }

            // Patch from the last index backwards, so earlier indices stay valid.
            if (alternateIndex != null) {
                // The default gradient is still live in this register when the swap is skipped.
                val brushRegister = (getInstruction(alternateIndex) as OneRegisterInstruction).registerA
                val freeRegister = findFreeRegister(alternateIndex, brushRegister)
                addInstructionsWithLabels(
                    alternateIndex,
                    """
                        invoke-static { }, $EXTENSION_CLASS->useAlternateFade()Z
                        move-result v$freeRegister
                        if-eqz v$freeRegister, :keep_default_fade
                    """,
                    ExternalLabel("keep_default_fade", getInstruction(alternateIndex + 1))
                )
            }

            alphaCallIndices.reversed().forEach { index ->
                val instruction = getInstruction(index)
                val alphaRegister = if (instruction is RegisterRangeInstruction) {
                    instruction.startRegister + 2
                } else {
                    (instruction as FiveRegisterInstruction).registerE
                }

                addInstructions(
                    index,
                    """
                        invoke-static/range { v$alphaRegister .. v$alphaRegister }, $EXTENSION_CLASS->scaleFadeAlpha(F)F
                        move-result v$alphaRegister
                    """
                )
            }

            if (scrimStyleIndex >= 0) {
                if (!AccessFlags.STATIC.isSet(accessFlags)) throw PatchException("Expected a static method")
                val styleRegister = "p" + parameters.take(scrimStyleIndex).sumOf { parameter ->
                    if (parameter.type == "J" || parameter.type == "D") 2L else 1L
                }
                addInstructions(
                    0,
                    """
                        invoke-static/range { $styleRegister .. $styleRegister }, $EXTENSION_CLASS->getScrimStyle(Ljava/lang/Enum;)Ljava/lang/Enum;
                        move-result-object $styleRegister
                        check-cast $styleRegister, $SCRIM_STYLE_CLASS
                    """
                )
            }
        }

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
