package com.marverenic.music.viewmodel;

import android.content.Context;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Switch;

import com.marverenic.music.BR;
import com.marverenic.music.JockeyApplication;
import com.marverenic.music.data.store.PlaylistStore;
import com.marverenic.music.instances.AutoPlaylist;
import com.marverenic.music.instances.playlistrules.AutoPlaylistRule;

import javax.inject.Inject;

public class RuleHeaderViewModel extends BaseObservable {

    private static final String TAG = "RuleHeaderViewModel";

    private static final int[] TRUNCATE_CHOICES = new int[] {
            AutoPlaylistRule.ID,
            AutoPlaylistRule.NAME,
            AutoPlaylistRule.PLAY_COUNT,
            AutoPlaylistRule.PLAY_COUNT,
            AutoPlaylistRule.SKIP_COUNT,
            AutoPlaylistRule.SKIP_COUNT,
            AutoPlaylistRule.DATE_ADDED,
            AutoPlaylistRule.DATE_ADDED,
            AutoPlaylistRule.DATE_PLAYED,
            AutoPlaylistRule.DATE_PLAYED
    };

    private static final boolean[] TRUNCATE_ORDER_ASCENDING = new boolean[] {
            true,
            true,
            false,
            true,
            false,
            true,
            false,
            true,
            false,
            true
    };

    @Inject PlaylistStore mPlaylistStore;

    private AutoPlaylist.Builder mBuilder;

    public RuleHeaderViewModel(Context context) {
        JockeyApplication.getComponent(context).inject(this);
    }

    public void setBuilder(AutoPlaylist.Builder builder) {
        mBuilder = builder;
        notifyPropertyChanged(BR.playlistName);
        notifyPropertyChanged(BR.playlistNameError);
        notifyPropertyChanged(BR.matchAllRules);
        notifyPropertyChanged(BR.matchAllRules);
        notifyPropertyChanged(BR.songCountCapped);
        notifyPropertyChanged(BR.chosenBySelection);
        notifyPropertyChanged(BR.songCap);
    }

    @Bindable
    public String getPlaylistName() {
        return mBuilder.getName();
    }

    public TextWatcher onPlaylistNameChanged() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                mBuilder.setName(charSequence.toString());
                notifyPropertyChanged(BR.playlistName);
                notifyPropertyChanged(BR.playlistNameError);
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        };
    }

    @Bindable
    public String getPlaylistNameError() {
        return mPlaylistStore.verifyPlaylistName(getPlaylistName());
    }

    @Bindable
    public boolean isMatchAllRules() {
        return mBuilder.isMatchAllRules();
    }

    public View.OnClickListener onMatchAllContainerClick() {
        return v -> {
            mBuilder.setMatchAllRules(!isMatchAllRules());
            notifyPropertyChanged(BR.matchAllRules);
        };
    }

    public Switch.OnCheckedChangeListener onMatchAllToggle() {
        return (checkBox, enabled) -> mBuilder.setMatchAllRules(enabled);
    }

    @Bindable
    public boolean isSongCountCapped() {
        return mBuilder.getMaximumEntries() > 0;
    }

    public View.OnClickListener onSongCapContainerClick() {
        return v -> {
            mBuilder.setMaximumEntries(-1 * mBuilder.getMaximumEntries());
            notifyPropertyChanged(BR.songCountCapped);
        };
    }

    @Bindable
    public String getSongCap() {
        return Integer.toString(Math.abs(mBuilder.getMaximumEntries()));
    }

    public TextWatcher onSongCapChanged() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String value = charSequence.toString();
                if (value.isEmpty()) {
                    mBuilder.setMaximumEntries(0);
                } else {
                    try {
                        mBuilder.setMaximumEntries(Integer.parseInt(charSequence.toString()));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "onTextChanged: Failed to parse song cap", e);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        };
    }

    @Bindable
    public int getChosenBySelection() {
        int i = 0;
        while (TRUNCATE_CHOICES[i] != mBuilder.getTruncateMethod()) {
            i++;
        }
        while (TRUNCATE_ORDER_ASCENDING[i] != mBuilder.isTruncateAscending()) {
            i++;
        }
        return i;
    }

    public Spinner.OnItemSelectedListener onTruncateMethodSelected() {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                mBuilder.setTruncateMethod(TRUNCATE_CHOICES[pos]);
                mBuilder.setTruncateAscending(TRUNCATE_ORDER_ASCENDING[pos]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        };
    }

    public CheckBox.OnCheckedChangeListener onTruncateToggle() {
        return (checkBox, enabled) -> {
            if (enabled) {
                mBuilder.setMaximumEntries(Math.abs(mBuilder.getMaximumEntries()));
            } else {
                mBuilder.setMaximumEntries(-1 * Math.abs(mBuilder.getMaximumEntries()));
            }

            notifyPropertyChanged(BR.songCountCapped);
        };
    }
}
