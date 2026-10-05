/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.actionbar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.string

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
internal fun postActionBarRowFingerprint(actionBarType: String) = Fingerprint(
    definingClass = actionBarType,
    returnType = "V",
    filters = listOf(
        // Spacing between the buttons.
        literal(6f)
    ),
    custom = { method, _ ->
        val parameters = method.parameterTypes.map { it.toString() }
        parameters.size == 8 &&
                parameters[2] == parameters[3] &&
                parameters[3] == parameters[4] &&
                parameters[4] == parameters[5] &&
                parameters[7] == "I"
    }
)
