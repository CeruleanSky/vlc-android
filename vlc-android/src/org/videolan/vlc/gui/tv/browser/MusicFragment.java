/*
 * *************************************************************************
 *  MusicFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.tv.browser;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.util.SimpleArrayMap;
import android.text.TextUtils;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.helpers.MediaComparators;
import org.videolan.vlc.gui.tv.MainTvActivity;
import org.videolan.vlc.gui.tv.TvUtil;
import org.videolan.vlc.gui.tv.browser.interfaces.BrowserActivityInterface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class MusicFragment extends MediaLibBrowserFragment {

    public static final String MEDIA_SECTION = "section";
    public static final String AUDIO_CATEGORY = "category";
    public static final String AUDIO_FILTER = "filter";

    public static final long FILTER_ARTIST = 3;
    public static final long FILTER_GENRE = 4;

    public static final int CATEGORY_NOW_PLAYING = 0;
    public static final long CATEGORY_ARTISTS = 1;
    public static final long CATEGORY_ALBUMS = 2;
    public static final long CATEGORY_GENRES = 3;
    public static final long CATEGORY_SONGS = 4;

    protected SimpleArrayMap<String, ListItem> mMediaItemMap;
    protected ArrayList<ListItem> mMediaItemList;
    private volatile AsyncAudioUpdate mUpdater = null;

    String mFilter;
    long mCategory;
    long mType;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null){
            mType = savedInstanceState.getLong(MEDIA_SECTION);
            mCategory = savedInstanceState.getLong(AUDIO_CATEGORY);
            mFilter = savedInstanceState.getString(AUDIO_FILTER);
        } else {
            mType = getActivity().getIntent().getLongExtra(MEDIA_SECTION, -1);
            mCategory = getActivity().getIntent().getLongExtra(AUDIO_CATEGORY, 0);
            mFilter = getActivity().getIntent().getStringExtra(AUDIO_FILTER);
        }
    }

    public void onResume() {
        super.onResume();
        if (mUpdater == null) {
            mUpdater = new AsyncAudioUpdate();
            mUpdater.execute();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mUpdater != null)
            mUpdater.cancel(true);
    }

    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putLong(AUDIO_CATEGORY, mCategory);
        outState.putLong(MEDIA_SECTION, mType);
    }

    public class AsyncAudioUpdate extends AsyncTask<Void, ListItem, String> {

        ArrayList<MediaWrapper> audioList;
        public AsyncAudioUpdate() {}

        @Override
        protected void onPreExecute() {
            setTitle(getString(R.string.app_name_full));
            mAdapter.clear();
            mMediaItemMap = new SimpleArrayMap<>();
            mMediaItemList = new ArrayList<>();
            ((BrowserActivityInterface)getActivity()).showProgress(true);
        }

        @Override
        protected String doInBackground(Void... params) {
            String title;
            ListItem item;

            audioList = new ArrayList<>(Arrays.asList(Medialibrary.getInstance(VLCApplication.getAppContext()).nativeGetAudio()));
            if (CATEGORY_ARTISTS == mCategory){
                Collections.sort(audioList, MediaComparators.byArtist);
                title = getString(R.string.artists);
                for (MediaWrapper mediaWrapper : audioList){
                    item = add(mediaWrapper.getArtist(), null, mediaWrapper);
                    if (item != null)
                        publishProgress(item);
                }
            } else if (CATEGORY_ALBUMS == mCategory){
                title = getString(R.string.albums);
                Collections.sort(audioList, MediaComparators.byAlbum);
                for (MediaWrapper mediaWrapper : audioList){
                    if (mFilter == null
                            || (mType == FILTER_ARTIST && mFilter.equals(mediaWrapper.getArtist()))
                            || (mType == FILTER_GENRE && mFilter.equals(mediaWrapper.getGenre()))) {
                        item = add(mediaWrapper.getAlbum(), mediaWrapper.getArtist(), mediaWrapper);
                        if (item != null)
                            publishProgress(item);
                    }
                }
                //Customize title for artist/genre browsing
                if (!mMediaItemList.isEmpty()) {
                    if (mType == FILTER_ARTIST){
                        title = title + " " + mMediaItemList.get(0).mediaList.get(0).getArtist();
                    } else if (mType == FILTER_GENRE){
                        title = title + " " + mMediaItemList.get(0).mediaList.get(0).getGenre();
                    }
                } else
                    title = getString(R.string.app_name_full);
            } else if (CATEGORY_GENRES == mCategory){
                title = getString(R.string.genres);
                Collections.sort(audioList, MediaComparators.byGenre);
                for (MediaWrapper mediaWrapper : audioList){
                    item = add(mediaWrapper.getGenre(), null, mediaWrapper);
                    if (item != null)
                        publishProgress(item);
                }
            } else if (CATEGORY_SONGS == mCategory){
                title = getString(R.string.songs);
                Collections.sort(audioList, MediaComparators.byName);
                ListItem mediaItem;
                for (MediaWrapper mediaWrapper : audioList){
                    mediaItem = new ListItem(mediaWrapper.getTitle(), mediaWrapper.getArtist(), mediaWrapper);
                    mMediaItemMap.put(title, mediaItem);
                    mMediaItemList.add(mediaItem);
                    publishProgress(mediaItem);
                }
            } else {
                title = getString(R.string.app_name_full);
            }
            return title;
        }

        protected void onProgressUpdate(ListItem... items){
            mAdapter.add(items[0]);
        }

        @Override
        protected void onPostExecute(String title) {
            ((BrowserActivityInterface)getActivity()).showProgress(false);
            setTitle(title);
            setOnItemViewClickedListener(new OnItemViewClickedListener() {
                @Override
                public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                          RowPresenter.ViewHolder rowViewHolder, Row row) {
                    ListItem listItem = (ListItem) item;
                    Intent intent;
                    if (CATEGORY_ARTISTS == mCategory) {
                        intent = new Intent(mContext, VerticalGridActivity.class);
                        intent.putExtra(MainTvActivity.BROWSER_TYPE, MainTvActivity.HEADER_CATEGORIES);
                        intent.putExtra(AUDIO_CATEGORY, CATEGORY_ALBUMS);
                        intent.putExtra(MEDIA_SECTION, FILTER_ARTIST);
                        intent.putExtra(AUDIO_FILTER, listItem.mediaList.get(0).getArtist());
                    } else if (CATEGORY_GENRES == mCategory) {
                        intent = new Intent(mContext, VerticalGridActivity.class);
                        intent.putExtra(MainTvActivity.BROWSER_TYPE, MainTvActivity.HEADER_CATEGORIES);
                        intent.putExtra(AUDIO_CATEGORY, CATEGORY_ALBUMS);
                        intent.putExtra(MEDIA_SECTION, FILTER_GENRE);
                        intent.putExtra(AUDIO_FILTER, listItem.mediaList.get(0).getGenre());
                    } else {
                        if (CATEGORY_ALBUMS == mCategory) {
                            Collections.sort(listItem.mediaList, MediaComparators.byTrackNumber);
                            TvUtil.playAudioList(mContext, listItem.mediaList, 0);
                        } else {
                            int position = 0;
                            String location = listItem.mediaList.get(0).getLocation();
                            for (int i = 0; i < audioList.size(); ++i) {
                                if (TextUtils.equals(location, audioList.get(i).getLocation())) {
                                    position = i;
                                    break;
                                }
                            }
                            TvUtil.playAudioList(mContext, audioList, position);
                        }
                        return;
                    }
                    startActivity(intent);
                }
            });
        }
    }

    public static class ListItem {
        public String mTitle;
        public String mSubTitle;
        public ArrayList<MediaWrapper> mediaList;

        public ListItem(String title, String subTitle, MediaWrapper mediaWrapper) {
            mediaList = new ArrayList<>();
            if (mediaWrapper != null)
                mediaList.add(mediaWrapper);
            mTitle = title;
            mSubTitle = subTitle;
        }
    }

    public ListItem add(String title, String subTitle, MediaWrapper mediaWrapper) {
        if(title == null) return null;
        title = title.trim();
        if(subTitle != null) subTitle = subTitle.trim();
        if (mMediaItemMap.containsKey(title))
            mMediaItemMap.get(title).mediaList.add(mediaWrapper);
        else {
            ListItem item = new ListItem(title, subTitle, mediaWrapper);
            mMediaItemMap.put(title, item);
            mMediaItemList.add(item);
            return item;
        }
        return null;
    }

    @Override
    public void updateList() {
        if (mUpdater == null) {
            mUpdater = new AsyncAudioUpdate();
            mUpdater.execute();
        }
    }
}