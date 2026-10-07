/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.fullwidth

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.misc.settings.settingsPatch
import app.morphe.patches.reddit.shared.Constants.COMPATIBILITY_REDDIT
import app.morphe.util.findFreeRegister
import app.morphe.util.findInstructionIndicesReversed
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/reddit/patches/FullWidthFeedMediaPatch;"

@Suppress("unused")
val fullWidthFeedMediaPatch = bytecodePatch(
    name = "Full width feed media",
    description = "Adds an option to show images and videos edge to edge in the feed and post details, " +
            "without side padding and rounded corners."
) {
    compatibleWith(COMPATIBILITY_REDDIT)

    dependsOn(settingsPatch)

    execute {
        // Find which boolean getter of the feed post style is 'mediaInsetEnabled'.
        val styleClass = FeedPostStyleToStringFingerprint.classDef
        val insetField = FeedPostStyleToStringFingerprint.method.getInstruction<ReferenceInstruction>(
            FeedPostStyleToStringFingerprint.instructionMatches.last().index
        ).reference as FieldReference

        val insetFieldRead = fieldAccess(opcode = Opcode.IGET_BOOLEAN, name = insetField.name)
        val getterName = styleClass.methods.firstOrNull { method ->
            method.returnType == "Z" && method.parameterTypes.isEmpty() &&
                    method.indexOfFirstInstruction(insetFieldRead) == 0
        }?.name ?: throw PatchException("Could not find the media inset getter")

        // Every post style (regular, crosspost, ...) extends the same base class.
        val baseClass = styleClass.superclass
            ?: throw PatchException("Feed post style has no base class")

        var patchedCount = 0
        classDefForEach { classDef ->
            if (classDef.superclass != baseClass) return@classDefForEach
            if (classDef.methods.none { it.name == getterName && it.returnType == "Z" && it.parameterTypes.isEmpty() }) {
                return@classDefForEach
            }

            mutableClassDefBy(classDef).methods.filter { method ->
                method.name == getterName && method.returnType == "Z" && method.parameterTypes.isEmpty() &&
                        !AccessFlags.ABSTRACT.isSet(method.accessFlags)
            }.forEach { method ->
                method.findInstructionIndicesReversed(Opcode.RETURN).forEach { index ->
                    val register = method.getInstruction<OneRegisterInstruction>(index).registerA
                    method.addInstructions(
                        index,
                        """
                            invoke-static { v$register }, $EXTENSION_CLASS->isMediaInsetEnabled(Z)Z
                            move-result v$register
                        """
                    )
                }
                patchedCount++
            }
        }
        if (patchedCount == 0) throw PatchException("No feed post styles found")

        // Media height is computed from the screen width minus the inset padding, regardless of
        // the post style. Without the padding the media is wider, so it must be taller to keep
        // its aspect ratio instead of being cropped.
        listOf(
            FeedImageSizeFingerprint,
            FeedImageMaxHeightFingerprint,
            FeedVideoHeightFingerprint,
            FeedGalleryHeightFingerprint
        ).forEach { fingerprint ->
            fingerprint.method.apply {
                val index = fingerprint.instructionMatches[1].index + 1
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                addInstructions(
                    index + 1,
                    """
                        invoke-static/range { v$register .. v$register }, $EXTENSION_CLASS->getMediaInset(I)I
                        move-result v$register
                    """
                )
            }
        }

        // Images in the post details are inset by their own flag.
        PostImagePropsToStringFingerprint.matchOrNull()?.let { match ->
            val insetField = match.method.getInstruction<ReferenceInstruction>(
                match.instructionMatches.last().index
            ).reference as FieldReference

            match.classDef.methods.filter { it.name == "<init>" }.forEach { constructor ->
                constructor.findInstructionIndicesReversed(
                    fieldAccess(opcode = Opcode.IPUT_BOOLEAN, name = insetField.name)
                ).forEach { index ->
                    val register = constructor.getInstruction<TwoRegisterInstruction>(index).registerA
                    constructor.addInstructions(
                        index,
                        """
                            invoke-static/range { v$register .. v$register }, $EXTENSION_CLASS->isMediaInsetEnabled(Z)Z
                            move-result v$register
                        """
                    )
                }
            }
        }

        // Post details. Not found on older versions, which keep the padding there.
        run postDetailVideos@{
            // Videos are padded by the post content around them.
            val propsMatch = PostContentPropsToStringFingerprint.matchOrNull() ?: return@postDetailVideos
            val propsType = propsMatch.classDef.type
            val contentField = propsMatch.method.getInstruction<ReferenceInstruction>(
                propsMatch.instructionMatches.last().index
            ).reference as FieldReference
            val videoContentType = GifAndVideoContentToStringFingerprint.classDefOrNull?.type
                ?: return@postDetailVideos
            val contentLambdaConstructor = postContentLambdaConstructorFingerprint(propsType).methodOrNull
                ?: return@postDetailVideos
            // Shown at a precomputed size, for the padded width.
            val video = PostDetailVideoFingerprint.methodOrNull ?: return@postDetailVideos
            // Rounds the corners of the video.
            val videoLambdaConstructor = PostDetailVideoLambdaConstructorFingerprint.methodOrNull
                ?: return@postDetailVideos

            mutableClassDefBy(EXTENSION_CLASS).methods.apply {
                first { it.name == "getPostContent" }.addInstructions(
                    0,
                    """
                        check-cast p0, $propsType
                        iget-object v0, p0, $contentField
                        return-object v0
                    """
                )
                first { it.name == "isVideoContent" }.addInstructions(
                    0,
                    """
                        instance-of v0, p0, $videoContentType
                        return v0
                    """
                )
            }

            contentLambdaConstructor.addInstructions(
                0,
                """
                    invoke-static { p5, p1 }, $EXTENSION_CLASS->isPostContentInset(ZLjava/lang/Object;)Z
                    move-result p5
                """
            )

            // The method has many registers, so the parameter registers can be too high for move-result.
            video.apply {
                val register = findFreeRegister(0)
                addInstructions(
                    0,
                    """
                        invoke-static/range { p2 .. p3 }, $EXTENSION_CLASS->getPostVideoHeight(II)I
                        move-result v$register
                        move/16 p3, v$register
                        invoke-static/range { p2 .. p2 }, $EXTENSION_CLASS->getPostVideoWidth(I)I
                        move-result v$register
                        move/16 p2, v$register
                    """
                )
            }

            videoLambdaConstructor.addInstructions(
                0,
                """
                    invoke-static { p4 }, $EXTENSION_CLASS->isMediaInsetEnabled(Z)Z
                    move-result p4
                """
            )
        }

        // Galleries in the post details. Same register limits as videos.
        PostDetailGalleryFingerprint.methodOrNull?.apply {
            val register = findFreeRegister(0)
            addInstructions(
                0,
                """
                    invoke-static/range { p13 .. p13 }, $EXTENSION_CLASS->isMediaInsetEnabled(Z)Z
                    move-result v$register
                    move/16 p13, v$register
                """
            )
        }

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
