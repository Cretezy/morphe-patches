/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.actionbar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionFilter
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private val STATIC_CALL_OPCODES = listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)

internal val POST_ACTION_BAR_METHOD_CALL = methodCall(
    definingClass = "this",
    opcodes = STATIC_CALL_OPCODES
)

private val STATIC_METHOD_CALL = methodCall(opcodes = STATIC_CALL_OPCODES)

// The row's composer type must match the enclosing action bar's composer parameter.
internal val POST_ACTION_BAR_ROW_CALL = InstructionFilter { method, instruction ->
    val parameters = instruction.getReference<MethodReference>()?.parameterTypes
    STATIC_METHOD_CALL.matches(method, instruction) && parameters?.size == 4 &&
            parameters[2].toString() == method.parameterTypes[6].toString()
}

/**
 * Class with the post action bar composables.
 */
internal object PostActionBarTagsFingerprint : Fingerprint(
    filters = listOf(
        string("actionBar_comment_button")
    )
)

/**
 * Post action bar used in the feed and post details: the vote buttons, then a row with
 * the comment button in a weighted row, then the crosspost, share and mod buttons.
 * Parameters: vote state, appearance, then the comment, crosspost, share and mod states.
 */
internal object PostActionBarRowFingerprint : Fingerprint(
    classFingerprint = PostActionBarTagsFingerprint,
    returnType = "V",
    filters = listOf(
        // Spacing between the buttons.
        literal(6f)
    ),
    // Parameter declarations cannot express equality between obfuscated parameter types.
    custom = { method, _ ->
        val parameters = method.parameterTypes.map { it.toString() }
        parameters.size == 8 &&
                parameters[2] == parameters[3] &&
                parameters[3] == parameters[4] &&
                parameters[4] == parameters[5] &&
                parameters[7] == "I"
    }
)
