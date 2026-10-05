/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.mediaviewer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction

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

/**
 * Media viewer pager. When the comments sheet is hidden, a dock with the "See the conversation"
 * button is shown below the media, and the pager is padded at the bottom to make room for it:
 * 60dp, plus room for a docked seekbar.
 */
internal object JoinConversationDockFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        methodCall(
            definingClass = "Lcom/reddit/fullbleedplayer/FbpVideoControls;",
            name = "isSeekbarDocked"
        ),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        // Whether the dock is shown.
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately())
    ),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.opcode == Opcode.ADD_INT_LIT8 &&
                    (instruction as NarrowLiteralInstruction).narrowLiteral == 60
        } == true
    }
)

/**
 * Page of the media viewer pager. Pages are padded for the navigation bar at the bottom,
 * and for the status bar at the top, except images and videos.
 */
internal object MediaViewerPageFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(
        methodCall(
            definingClass = "Lcom/reddit/fullbleedplayer/FbpVideoControls;",
            name = "isSeekbarDocked"
        ),
        fieldAccess(
            definingClass = "Landroid/content/res/Configuration;",
            name = "orientation",
            location = MatchAfterWithin(6)
        )
    )
)
