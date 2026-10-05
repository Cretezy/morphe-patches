/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.mediaviewer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.newInstance
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

/**
 * Composable of the media viewer (full bleed player) caption, user info and action bar.
 */
internal object MediaViewerChromeFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("userInfoAndActionBarAlpha")
    )
)

/**
 * toString of the media viewer chrome state, which logs whether the overlay is visible.
 */
internal object FullBleedChromeStateToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(
        string("FullBleedChromeState(userViewState="),
        string(", isVisible="),
        fieldAccess(
            opcode = Opcode.IGET_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterWithin(3)
        )
    )
)

/**
 * Creates the media viewer chrome state of a post, when it is shown in the media viewer.
 */
internal fun createFullBleedChromeStateFingerprint(chromeStateType: String) = Fingerprint(
    returnType = chromeStateType,
    parameters = listOf("Lcom/reddit/domain/model/Link;", "L", "Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(
        newInstance(chromeStateType)
    )
)
