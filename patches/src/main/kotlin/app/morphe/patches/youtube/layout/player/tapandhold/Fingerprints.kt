/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.layout.player.tapandhold

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patches.youtube.layout.player.overlay.CreatePlayerOverviewFingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * Called while the fullscreen "More videos" panel is dragged.
 * Shows the player controls when the drag goes up while the panel is peeking.
 */
internal object ShowControlsOnRelatedPanelDragFingerprint : Fingerprint(
    classFingerprint = CreatePlayerOverviewFingerprint,
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("F"),
    filters = listOf(
        opcode(Opcode.IF_GEZ),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            definingClass = "this",
            parameters = listOf(),
            returnType = "V"
        ),
        opcode(Opcode.RETURN_VOID, MatchAfterImmediately())
    )
)
