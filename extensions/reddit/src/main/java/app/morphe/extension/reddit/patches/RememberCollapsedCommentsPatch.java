/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.patches;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.reddit.domain.model.Comment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BooleanSetting;

/**
 * Remembers which comments the user collapsed, and shows them collapsed
 * again when the post is opened later.
 * <p>
 * Ids (t1_xxx) are kept in a dedicated SharedPreferences file, one key per comment,
 * with the time the comment was collapsed as the value. Only the {@link #MAX_ENTRIES}
 * most recently collapsed comments are kept, and none older than {@link #MAX_AGE_MILLIS}.
 */
@SuppressWarnings("unused")
public final class RememberCollapsedCommentsPatch {

    /**
     * Declared here instead of in the shared Settings class, so the patch still works when
     * combined with another patch bundle whose copy of Settings is used instead of this one.
     */
    public static final BooleanSetting REMEMBER_COLLAPSED_COMMENTS =
            new BooleanSetting("morphe_remember_collapsed_comments", true);

    private static final String PREFS_NAME = "morphe_collapsed_comments";
    private static final int MAX_ENTRIES = 5000;
    private static final long MAX_AGE_MILLIS = 180L * 24 * 60 * 60 * 1000;

    /**
     * Comment id to the time it was collapsed. Null until loaded.
     */
    @Nullable
    private static volatile Map<String, Long> collapsedComments;

    /**
     * Comments marked as collapsed by this patch, by id. Reddit caches loaded comments,
     * so these are reset when the comment is expanded and forgotten.
     */
    private static final Map<String, List<WeakReference<Comment>>> markedComments = new HashMap<>();

    /**
     * @return If this patch was included during patching.
     */
    public static boolean isPatchIncluded() {
        return false;  // Modified during patching.
    }

    /**
     * Injection point.
     * <p>
     * Called by Comment.getCollapsed() with the collapsed flag Reddit set.
     */
    public static boolean isCollapsed(Object item, boolean collapsed) {
        if (collapsed) return true;

        try {
            if (!REMEMBER_COLLAPSED_COMMENTS.get()) return false;

            Map<String, Long> collapsedIds = getCollapsedComments();
            if (collapsedIds == null || collapsedIds.isEmpty()) return false;

            Comment comment = (Comment) item;
            String id = comment.getKindWithId();
            if (id == null || !collapsedIds.containsKey(id)) return false;

            // Store it in the comment too, so copies made from it stay collapsed
            // and an expand can be told apart from other changes to the comment.
            comment.morphe_setCollapsed(true);
            synchronized (markedComments) {
                List<WeakReference<Comment>> marked = markedComments.get(id);
                if (marked == null) {
                    marked = new ArrayList<>(1);
                    markedComments.put(id, marked);
                }
                marked.add(new WeakReference<>(comment));
            }
            return true;
        } catch (Exception ex) {
            Logger.printException(() -> "isCollapsed failure", ex);
            return false;
        }
    }

    /**
     * Injection point.
     * <p>
     * Called when the comment tree replaces one of its items. Collapse, expand and
     * collapse thread all replace the comment with a copy that has a different collapsed flag.
     */
    public static void onCommentTreeItemUpdated(@Nullable Object oldItem, @Nullable Object newItem) {
        try {
            if (!(oldItem instanceof Comment) || !(newItem instanceof Comment)) return;

            final boolean wasCollapsed = ((Comment) oldItem).getCollapsed();
            Comment newComment = (Comment) newItem;
            // The raw flag, since the getter would still report a remembered comment as collapsed.
            final boolean collapsed = newComment.morphe_getRawCollapsed();
            if (wasCollapsed == collapsed) return;
            if (!REMEMBER_COLLAPSED_COMMENTS.get()) return;

            String id = newComment.getKindWithId();
            if (id == null) return;

            if (collapsed) {
                remember(id);
            } else {
                forget(id);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "onCommentTreeItemUpdated failure", ex);
        }
    }

    private static synchronized void remember(String id) {
        Map<String, Long> collapsedIds = getCollapsedComments();
        SharedPreferences prefs = getPrefs();
        if (collapsedIds == null || prefs == null) return;

        final long now = System.currentTimeMillis();
        collapsedIds.put(id, now);
        SharedPreferences.Editor editor = prefs.edit().putLong(id, now);
        trim(collapsedIds, editor, now);
        editor.apply();

        Logger.printDebug(() -> "Remembered collapsed comment: " + id);
    }

    private static synchronized void forget(String id) {
        Map<String, Long> collapsedIds = getCollapsedComments();
        SharedPreferences prefs = getPrefs();
        if (collapsedIds == null || prefs == null || collapsedIds.remove(id) == null) return;

        prefs.edit().remove(id).apply();

        List<WeakReference<Comment>> marked;
        synchronized (markedComments) {
            marked = markedComments.remove(id);
        }
        if (marked != null) {
            for (WeakReference<Comment> reference : marked) {
                Comment comment = reference.get();
                if (comment != null) comment.morphe_setCollapsed(false);
            }
        }

        Logger.printDebug(() -> "Forgot collapsed comment: " + id);
    }

    /**
     * Removes expired entries, and the oldest ones above the limit.
     */
    private static void trim(Map<String, Long> collapsedIds, SharedPreferences.Editor editor, long now) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(collapsedIds.entrySet());
        Collections.sort(entries, (a, b) -> Long.compare(a.getValue(), b.getValue()));

        int excess = entries.size() - MAX_ENTRIES;
        for (Map.Entry<String, Long> entry : entries) {
            if (excess <= 0 && now - entry.getValue() <= MAX_AGE_MILLIS) {
                // Sorted oldest first, so everything after this is kept.
                break;
            }
            collapsedIds.remove(entry.getKey());
            editor.remove(entry.getKey());
            excess--;
        }
    }

    @Nullable
    private static Map<String, Long> getCollapsedComments() {
        Map<String, Long> collapsedIds = collapsedComments;
        if (collapsedIds != null) return collapsedIds;

        synchronized (RememberCollapsedCommentsPatch.class) {
            if (collapsedComments != null) return collapsedComments;

            SharedPreferences prefs = getPrefs();
            if (prefs == null) return null;

            Map<String, Long> map = new ConcurrentHashMap<>();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                if (entry.getValue() instanceof Long) {
                    map.put(entry.getKey(), (Long) entry.getValue());
                }
            }

            final int sizeBefore = map.size();
            SharedPreferences.Editor editor = prefs.edit();
            trim(map, editor, System.currentTimeMillis());
            if (map.size() != sizeBefore) editor.apply();

            Logger.printDebug(() -> "Loaded " + map.size() + " collapsed comments");
            collapsedComments = map;
            return map;
        }
    }

    @Nullable
    private static SharedPreferences getPrefs() {
        Context context = Utils.getContext();
        return context != null
                ? context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                : null;
    }
}
