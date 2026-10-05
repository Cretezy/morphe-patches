/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.misc.collapsedcomments

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.toInstructions
import app.morphe.patches.reddit.misc.settings.settingsPatch
import app.morphe.patches.reddit.shared.Constants.COMPATIBILITY_REDDIT
import app.morphe.util.setExtensionIsPatchIncluded
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/reddit/patches/RememberCollapsedCommentsPatch;"

/**
 * Setter and raw getter added to the Comment class, so the extension can
 * mark a remembered comment as collapsed and see the flag Reddit itself set.
 */
private const val SET_COLLAPSED_METHOD = "morphe_setCollapsed"
private const val GET_RAW_COLLAPSED_METHOD = "morphe_getRawCollapsed"

@Suppress("unused")
val rememberCollapsedCommentsPatch = bytecodePatch(
    name = "Remember collapsed comments",
    description = "Adds an option to keep comments you collapsed collapsed when reopening a post."
) {
    compatibleWith(COMPATIBILITY_REDDIT)

    dependsOn(settingsPatch)

    execute {
        // 1. Report remembered comments as collapsed. Reddit copies comments in many places
        //    (loading, merging pages, translations), so hook the getter every copy goes through.
        CommentGetCollapsedFingerprint.let {
            val getIndex = it.instructionMatches.first().index
            val collapsedField = it.method.getInstruction<ReferenceInstruction>(getIndex).reference as FieldReference
            val fieldReference = "${collapsedField.definingClass}->${collapsedField.name}:${collapsedField.type}"

            it.classDef.apply {
                // The getter has a single register, so replace its body.
                val getter = it.method
                methods.remove(getter)
                methods.add(
                    ImmutableMethod(
                        type,
                        getter.name,
                        getter.parameters,
                        getter.returnType,
                        getter.accessFlags,
                        getter.annotations,
                        getter.hiddenApiRestrictions,
                        ImmutableMethodImplementation(
                            2,
                            """
                                iget-boolean v0, v1, $fieldReference
                                invoke-static { v1, v0 }, $EXTENSION_CLASS->isCollapsed(Ljava/lang/Object;Z)Z
                                move-result v0
                                return v0
                            """.toInstructions(),
                            null,
                            null,
                        ),
                    ).toMutable()
                )

                // The field is final. Writing it from a method of the same class passes
                // verification, but drop the flag anyway so the write is always legal.
                fields.first { field ->
                    field.name == collapsedField.name && field.type == collapsedField.type
                }.apply {
                    accessFlags = accessFlags and AccessFlags.FINAL.value.inv()
                }

                // Standalone smali has no parameter info, so use v registers (parameters are last).
                methods.add(
                    ImmutableMethod(
                        type,
                        SET_COLLAPSED_METHOD,
                        listOf(ImmutableMethodParameter("Z", emptySet(), "collapsed")),
                        "V",
                        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                        emptySet(),
                        null,
                        ImmutableMethodImplementation(
                            2,
                            """
                                iput-boolean v1, v0, $fieldReference
                                return-void
                            """.toInstructions(),
                            null,
                            null,
                        ),
                    ).toMutable()
                )

                methods.add(
                    ImmutableMethod(
                        type,
                        GET_RAW_COLLAPSED_METHOD,
                        emptyList(),
                        "Z",
                        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                        emptySet(),
                        null,
                        ImmutableMethodImplementation(
                            2,
                            """
                                iget-boolean v0, v1, $fieldReference
                                return v0
                            """.toInstructions(),
                            null,
                            null,
                        ),
                    ).toMutable()
                )
            }
        }

        // 2. Record every collapse and expand done in the comment tree.
        CommentTreeReplaceItemFingerprint.let {
            it.method.apply {
                val invokeIndex = it.instructionMatches[1].index
                val moveResultIndex = it.instructionMatches.last().index

                // invoke-interface { transform, oldItem }, Function1->invoke(Object)Object
                val oldItemRegister = getInstruction<FiveRegisterInstruction>(invokeIndex).registerD
                val newItemRegister = getInstruction<OneRegisterInstruction>(moveResultIndex).registerA
                if (oldItemRegister == newItemRegister) {
                    throw PatchException("Old comment tree item is overwritten by the new one")
                }

                addInstruction(
                    moveResultIndex + 1,
                    "invoke-static { v$oldItemRegister, v$newItemRegister }, " +
                            "$EXTENSION_CLASS->onCommentTreeItemUpdated(Ljava/lang/Object;Ljava/lang/Object;)V"
                )
            }
        }

        setExtensionIsPatchIncluded(EXTENSION_CLASS)
    }
}
