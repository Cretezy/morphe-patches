/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.reddit.layout.mediaviewer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionFilter
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.anyInstruction
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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
        opcode(Opcode.IF_EQZ, location = MatchAfterWithin(3)),
        literal(60, listOf(Opcode.ADD_INT_LIT8))
    )
)

/**
 * Page of the media viewer pager. Pages are padded for the navigation bar at the bottom,
 * and for the status bar at the top, except images and videos.
 */
private val modifierPaddingCall = methodCall(
    parameters = listOf("L"),
    returnType = "L",
    opcodes = listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)
)

// The modifier type is obfuscated. Its parameter and return type must be identical.
private val modifierPaddingFilter = InstructionFilter { method, instruction ->
    modifierPaddingCall.matches(method, instruction) &&
            instruction.getReference<MethodReference>()!!.let { reference ->
                reference.parameterTypes.first().toString() == reference.returnType
            }
}

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
        ),
        modifierPaddingFilter, // Navigation bar padding.
        modifierPaddingFilter // Status bar padding.
    )
)

/**
 * Test tags of the media viewer screen and pagers, each drawn with the theme background color.
 * Video pages don't draw their own background, so the pager color shows around them.
 */
internal val MEDIA_VIEWER_BACKGROUND_TAGS = setOf(
    "fbp_screen",
    "fbp_screen_horizontal_pager",
    "fbp_horizontal_pager"
)

internal val mediaViewerBackgroundTagFilter = anyInstruction(
    *MEDIA_VIEWER_BACKGROUND_TAGS.map { string(it) }.toTypedArray()
)

// Modifier.background(color, shape).
internal val mediaViewerBackgroundCallFilter = methodCall(
    parameters = listOf("L", "J", "L"),
    opcodes = listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)
)

internal object MediaViewerBackgroundFingerprint : Fingerprint(
    filters = listOf(mediaViewerBackgroundTagFilter)
)

/**
 * Bottom sheet menu host around the media viewer pager, with the theme background color.
 * It covers the page area between the system bars.
 */
internal object MediaViewerBottomSheetMenuFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        newInstance($$"Lcom/reddit/fullbleedplayer/ui/composables/BottomSheetMenuKt$BottomSheetMenu$2$1;"),
        mediaViewerBackgroundCallFilter
    )
)

/**
 * Top fade of a media viewer page, a gradient from the theme background color.
 */
internal object MediaViewerTopGradientFingerprint : Fingerprint(
    definingClass = "Lcom/reddit/fullbleedplayer/ui/composables/a;",
    returnType = "V",
    filters = listOf(
        literal(80f),
        methodCall(
            definingClass = "Ljava/util/Arrays;",
            name = "asList"
        )
    )
)

/**
 * Video player shared by the feed and the media viewer.
 * Its background is black, or the theme background color with some experiments.
 */
internal object VideoPlayerBackgroundFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        fieldAccess(
            definingClass = "Lcom/reddit/features/VideoThumbnailFadeInVariant;",
            name = "CONTROL"
        ),
        methodCall(
            returnType = "Ljava/lang/Object;",
            parameters = listOf("L"),
            opcode = Opcode.INVOKE_VIRTUAL
        ),
        mediaViewerBackgroundCallFilter
    )
)

/**
 * Any read of LocalContext from a composable.
 */
internal object LocalContextFingerprint : Fingerprint(
    filters = listOf(
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;"
        ),
        methodCall(
            returnType = "Ljava/lang/Object;",
            parameters = listOf("L"),
            location = MatchAfterImmediately()
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        checkCast("Landroid/content/Context;", location = MatchAfterImmediately())
    )
)

/**
 * The fade brush is built from an array of (position, color) pairs. The remaining
 * parameters vary between versions, so only the first parameter is constrained.
 */
private val staticMethodCall = methodCall(
    opcodes = listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)
)

internal object MediaViewerFadeGradientFingerprint : Fingerprint(
    filters = listOf(
        InstructionFilter { method, instruction ->
            staticMethodCall.matches(method, instruction) &&
                    instruction.getReference<MethodReference>()!!.parameterTypes
                        .firstOrNull()?.toString() == "[Lkotlin/Pair;"
        }
    )
)

// Color.copy(alpha) calls that construct the fade's color stops.
internal val mediaViewerFadeAlphaFilter = methodCall(
    parameters = listOf("J", "F"),
    returnType = "J",
    opcodes = listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)
)
