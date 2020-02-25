package aniAdd.startup;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.commons.cli.Option;

/**
 * Created by Archie on 23.12.2015.
 */
class AOMOption {
    private final String mArgname;
    private boolean mRequired;
    private String mShortOpt = null;
    private String mLongOpt = "";
    private String mDescription = "";
    private boolean mHasValue = false;

    public AOMOption(@Nullable String shortOpt, @NotNull String longOpt, @NotNull String description, boolean hasValue, String argName, boolean required) {
        this.mShortOpt = shortOpt;
        this.mLongOpt = longOpt;
        this.mDescription = description;
        this.mHasValue = hasValue;
        mArgname = argName;
        mRequired = required;
    }

    public String getName() {
        if (mShortOpt != null) {
            return mShortOpt;
        } else {
            return mLongOpt;
        }
    }

    public Option toOption() {
        Option.Builder builder;
        if (mShortOpt != null) {
            builder = Option.builder(mShortOpt);
        } else {
            builder = Option.builder();
        }
        return builder.longOpt(this.mLongOpt).hasArg(mHasValue).desc(mDescription).hasArg(mArgname != null).required(mRequired)
                .argName(mArgname).build();
    }
}
