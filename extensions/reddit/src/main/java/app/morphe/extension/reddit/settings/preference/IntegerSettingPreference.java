/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.reddit.settings.preference;

import static app.morphe.extension.shared.StringRef.str;

import android.content.Context;
import android.text.InputType;

import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.settings.IntegerSetting;
import app.morphe.extension.shared.settings.preference.ResettableEditTextPreference;

@SuppressWarnings({"deprecation", "unused"})
public class IntegerSettingPreference extends ResettableEditTextPreference {
    public IntegerSettingPreference(Context context, IntegerSetting setting) {
        super(context);
        setTitle(str(setting.key + "_title"));

        String summaryKey = setting.key + "_summary";
        if (ResourceUtils.getStringIdentifier(summaryKey) != 0) {
            setSummary(str(summaryKey));
        }

        setKey(setting.key);
        setSetting(setting);
        setText(String.valueOf(setting.get()));
        getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
    }
}
