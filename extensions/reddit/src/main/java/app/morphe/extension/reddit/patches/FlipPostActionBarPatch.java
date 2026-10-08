/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3541
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import android.content.res.Resources;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BooleanSetting;

@SuppressWarnings("unused")
public final class FlipPostActionBarPatch {

    /**
     * Declared here instead of in the shared Settings class, so the patch still works when
     * combined with another patch bundle whose copy of Settings is used instead of this one.
     */
    public static final BooleanSetting FLIP_POST_ACTION_BAR = new BooleanSetting("morphe_flip_post_action_bar", false, true);
    public static final BooleanSetting SWAP_POST_VOTE_COMMENT_BUTTONS = new BooleanSetting("morphe_swap_post_vote_comment_buttons", false, true);

    /**
     * Action bar row: [spacer, comment row, spacer, crosspost, spacer, share, spacer, mod].
     */
    private static final int ARRANGEMENT_BUTTONS_ROW = 0;

    /**
     * Comment row, which also gets the vote buttons: [vote buttons, comment button].
     */
    private static final int ARRANGEMENT_COMMENT_ROW = 1;

    private static final float BUTTON_SPACING_DP = 6;

    private static final Object[] arrangements = new Object[2];

    /**
     * @return If this patch was included during patching.
     */
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }

    /**
     * Injection point.
     *
     * @return If the vote buttons are moved into the comment row, next to the comment button.
     */
    public static boolean moveVoteButtons() {
        return FLIP_POST_ACTION_BAR.get() || SWAP_POST_VOTE_COMMENT_BUTTONS.get();
    }

    /**
     * Injection point.
     *
     * @param arrangement Compose horizontal arrangement of the action bar buttons row.
     */
    public static Object getButtonsRowArrangement(Object arrangement) {
        return getRowArrangement(arrangement, ARRANGEMENT_BUTTONS_ROW);
    }

    /**
     * Injection point.
     *
     * @param arrangement Compose horizontal arrangement of the action bar comment row.
     */
    public static Object getCommentRowArrangement(Object arrangement) {
        return getRowArrangement(arrangement, ARRANGEMENT_COMMENT_ROW);
    }

    private static Object getRowArrangement(Object arrangement, int mode) {
        if (!moveVoteButtons()) {
            return arrangement;
        }

        try {
            Object original = arrangement;
            if (Proxy.isProxyClass(arrangement.getClass())
                    && Proxy.getInvocationHandler(arrangement) instanceof ArrangementHandler handler) {
                original = handler.original;
            }

            // Reuse the same instance, so Compose doesn't create a new measure policy each time.
            synchronized (arrangements) {
                Object cached = arrangements[mode];
                if (cached != null && ((ArrangementHandler) Proxy.getInvocationHandler(cached)).original == original) {
                    return cached;
                }

                // The arrangement interfaces, implemented by the original arrangement.
                Object proxy = Proxy.newProxyInstance(
                        original.getClass().getClassLoader(),
                        original.getClass().getInterfaces(),
                        new ArrangementHandler(original, mode)
                );
                arrangements[mode] = proxy;
                return proxy;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "getRowArrangement failure", ex);
            return arrangement;
        }
    }

    private static final class ArrangementHandler implements InvocationHandler {
        private final Object original;
        private final int mode;

        ArrangementHandler(Object original, int mode) {
            this.original = original;
            this.mode = mode;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "FlipPostActionBarArrangement(" + mode + ")";
                };
            }

            // arrange(density, totalSize, sizes, layoutDirection, outPositions). Right to left is unchanged.
            if (args != null && args.length == 5
                    && args[1] instanceof Integer totalSize
                    && args[2] instanceof int[] sizes
                    && args[3] instanceof Enum<?> layoutDirection && layoutDirection.ordinal() == 0
                    && args[4] instanceof int[] positions) {
                if (mode == ARRANGEMENT_COMMENT_ROW) {
                    arrangeCommentRow(totalSize, sizes, positions);
                } else {
                    arrangeButtonsRow(sizes, positions);
                }
                return null;
            }

            return method.invoke(original, args);
        }

        /**
         * Vote and comment buttons, at the end of the row when flipped, otherwise at the start.
         */
        private static void arrangeCommentRow(int totalSize, int[] sizes, int[] positions) {
            final int count = sizes.length;
            final boolean swap = SWAP_POST_VOTE_COMMENT_BUTTONS.get();
            // Same spacing as between the other buttons.
            final int spacing = Math.round(BUTTON_SPACING_DP * Resources.getSystem().getDisplayMetrics().density);

            int x = 0;
            if (FLIP_POST_ACTION_BAR.get()) {
                int used = spacing * Math.max(0, count - 1);
                for (int size : sizes) used += size;
                x = totalSize - used;
            }

            for (int i = 0; i < count; i++) {
                final int index = swap ? count - 1 - i : i;
                positions[index] = x;
                x += sizes[index] + spacing;
            }
        }

        private static void arrangeButtonsRow(int[] sizes, int[] positions) {
            final int count = sizes.length;
            final int[] order = new int[count];

            final boolean flip = FLIP_POST_ACTION_BAR.get();
            if (flip ? count < 4 : count < 2) {
                for (int i = 0; i < count; i++) order[i] = i;
            } else if (flip) {
                // Buttons after the comment row, then both spacers, then the comment row.
                int index = 0;
                for (int i = 3; i < count; i++) order[index++] = i;
                order[index++] = 0;
                order[index++] = 2;
                order[index] = 1;
            } else {
                // The comment row first, so the vote and comment buttons stay at the start.
                order[0] = 1;
                order[1] = 0;
                for (int i = 2; i < count; i++) order[i] = i;
            }

            int x = 0;
            for (int i : order) {
                positions[i] = x;
                x += sizes[i];
            }
        }
    }
}
