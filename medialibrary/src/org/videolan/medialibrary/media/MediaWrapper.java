/*****************************************************************************
 * MediaWrapper.java
 *****************************************************************************
 * Copyright © 2011-2015 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.medialibrary.media;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.Media.Meta;
import org.videolan.libvlc.Media.VideoTrack;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.Extensions;

import java.util.Locale;

public class MediaWrapper extends MediaLibraryItem implements Parcelable {
    public final static String TAG = "VLC/MediaWrapper";

    public final static int TYPE_ALL = -1;
    public final static int TYPE_VIDEO = 0;
    public final static int TYPE_AUDIO = 1;
    public final static int TYPE_GROUP = 2;
    public final static int TYPE_DIR = 3;
    public final static int TYPE_SUBTITLE = 4;
    public final static int TYPE_PLAYLIST = 5;

    public final static int MEDIA_VIDEO = 0x01;
    public final static int MEDIA_NO_HWACCEL = 0x02;
    public final static int MEDIA_PAUSED = 0x4;
    public final static int MEDIA_FORCE_AUDIO = 0x8;

    private static final StringBuilder sb = new StringBuilder();

    protected String mTitle;
    protected String mDisplayTitle;
    private String mArtist;
    private String mGenre;
    private String mCopyright;
    private String mAlbum;
    private int mTrackNumber;
    private int mDiscNumber;
    private String mAlbumArtist;
    private String mDescription;
    private String mRating;
    private String mDate;
    private String mSettings;
    private String mNowPlaying;
    private String mPublisher;
    private String mEncodedBy;
    private String mTrackID;
    private String mArtworkURL;

    private final Uri mUri;
    private String mFilename;
    private long mTime = 0;
    /* -1 is a valid track (Disabled) */
    private int mAudioTrack = -2;
    private int mSpuTrack = -2;
    private long mLength = 0;
    private int mType;
    private int mWidth = 0;
    private int mHeight = 0;
    private Bitmap mPicture;
    private boolean mIsPictureParsed;
    private int mFlags = 0;
    private long mLastModified = 0l;
    private Media.Slave mSlaves[] = null;

    /**
     * Create a new MediaWrapper
     * @param mrl Should not be null.
     */
    public MediaWrapper(long id, String mrl, long time, long length, int type,
                      String title, String artist, String genre, String album, String albumArtist, int width,
                      int height, String artworkURL, int audio, int spu, int trackNumber, int discNumber, long lastModified) {
        if (TextUtils.isEmpty(mrl))
            throw new IllegalArgumentException("uri was empty");

        if (mrl.charAt(0) == '/')
            mrl = "file://"+mrl;
        mUri = Uri.parse(mrl);
        mId = id;
        init(time, length, type, null, title, artist, genre, album, albumArtist, width, height, artworkURL, audio, spu, trackNumber, discNumber, lastModified, null);
        String artistMeta = getReferenceArtist();
        boolean hasArtistMeta = !TextUtils.isEmpty(artistMeta);
        sb.setLength(0);
        if (!TextUtils.isEmpty(album)) {
            sb.append(album);
            if (hasArtistMeta)
                sb.append(" - ");
        }
        if (hasArtistMeta)
            sb.append(artistMeta);
        if (sb.length() > 0)
            mDescription = sb.toString();
    }

    /**
     * Create a new MediaWrapper
     * @param uri Should not be null.
     */
    public MediaWrapper(Uri uri) {
        if (uri == null)
            throw new NullPointerException("uri was null");

        mUri = uri;
        init(null);
    }

    /**
     * Create a new MediaWrapper
     * @param media should be parsed and not NULL
     */
    public MediaWrapper(Media media) {
        if (media == null)
            throw new NullPointerException("media was null");

        mUri = media.getUri();
        init(media);
    }

    @Override
    public boolean equals(Object obj) {
        Uri otherUri = ((MediaWrapper)obj).getUri();
        if (mUri == null || otherUri == null)
            return false;
        if (mUri == otherUri)
            return true;
        return mUri.equals(otherUri);
    }

    private void init(Media media) {
        mType = TYPE_ALL;

        if (media != null) {
            if (media.isParsed()) {
                mLength = media.getDuration();

                for (int i = 0; i < media.getTrackCount(); ++i) {
                    final Media.Track track = media.getTrack(i);
                    if (track == null)
                        continue;
                    if (track.type == Media.Track.Type.Video) {
                        final Media.VideoTrack videoTrack = (VideoTrack) track;
                        mType = TYPE_VIDEO;
                        mWidth = videoTrack.width;
                        mHeight = videoTrack.height;
                    } else if (mType == TYPE_ALL && track.type == Media.Track.Type.Audio){
                        mType = TYPE_AUDIO;
                    }
                }
            }
            updateMeta(media);
            if (mType == TYPE_ALL)
                switch (media.getType()) {
                    case Media.Type.Directory:
                        mType = TYPE_DIR;
                        break;
                    case Media.Type.Playlist:
                        mType = TYPE_PLAYLIST;
                        break;
                }
            mSlaves = media.getSlaves();
        }
        defineType();
    }

    public void defineType() {
        if (mType != TYPE_ALL)
            return;

        String fileExt = null, filename = mUri.getLastPathSegment();
        if (TextUtils.isEmpty(filename))
            filename = mTitle;
        if (TextUtils.isEmpty(filename))
            return;
        int index = filename.indexOf('?');
        if (index != -1)
            filename = filename.substring(0, index);

        index = filename.lastIndexOf(".");

        if (index != -1)
            fileExt = filename.substring(index).toLowerCase(Locale.ENGLISH);

        if (!TextUtils.isEmpty(fileExt)) {
            if (Extensions.VIDEO.contains(fileExt)) {
                mType = TYPE_VIDEO;
            } else if (Extensions.AUDIO.contains(fileExt)) {
                mType = TYPE_AUDIO;
            } else if (Extensions.SUBTITLES.contains(fileExt)) {
                mType = TYPE_SUBTITLE;
            } else if (Extensions.PLAYLIST.contains(fileExt)) {
                mType = TYPE_PLAYLIST;
            }
        }
    }

    private void init(long time, long length, int type,
                      Bitmap picture, String title, String artist, String genre, String album, String albumArtist,
                      int width, int height, String artworkURL, int audio, int spu, int trackNumber, int discNumber, long lastModified,
                      Media.Slave[] slaves) {
        mFilename = null;
        mTime = time;
        mAudioTrack = audio;
        mSpuTrack = spu;
        mLength = length;
        mType = type;
        mPicture = picture;
        mWidth = width;
        mHeight = height;

        mTitle = title != null ? title.trim() : null;
        mArtist = artist != null ? artist.trim() : null;
        mGenre = genre != null ? genre.trim() : null;
        mAlbum = album != null ? album.trim() : null;
        mAlbumArtist = albumArtist != null ? albumArtist.trim() : null;
        mArtworkURL = artworkURL;
        mTrackNumber = trackNumber;
        mDiscNumber = discNumber;
        mLastModified = lastModified;
        mSlaves = slaves;
    }

    public MediaWrapper(Uri uri, long time, long length, int type,
                 Bitmap picture, String title, String artist, String genre, String album, String albumArtist,
                 int width, int height, String artworkURL, int audio, int spu, int trackNumber, int discNumber, long lastModified) {
        mUri = uri;
        init(time, length, type, picture, title, artist, genre, album, albumArtist,
             width, height, artworkURL, audio, spu, trackNumber, discNumber, lastModified, null);
    }

    public long getId() {
        return mId;
    }

    public String getLocation() {
        return mUri.toString();
    }

    public Uri getUri() {
        return mUri;
    }

    private static String getMetaId(Media media, String defaultMeta, int id, boolean trim) {
        String meta = media.getMeta(id);
        return meta != null ? trim ? meta.trim() : meta : defaultMeta;
    }

    public void updateMeta(Media media) {
        mTitle = getMetaId(media, mTitle, Meta.Title, true);
        mArtist = getMetaId(media, mArtist, Meta.Artist, true);
        mAlbum = getMetaId(media, mAlbum, Meta.Album, true);
        mGenre = getMetaId(media, mGenre, Meta.Genre, true);
        mAlbumArtist = getMetaId(media, mAlbumArtist, Meta.AlbumArtist, true);
        mArtworkURL = getMetaId(media, mArtworkURL, Meta.ArtworkURL, false);
        mNowPlaying = getMetaId(media, mNowPlaying, Meta.NowPlaying, false);
        final String trackNumber = getMetaId(media, null, Meta.TrackNumber, false);
        if (!TextUtils.isEmpty(trackNumber)) {
            try {
                mTrackNumber = Integer.parseInt(trackNumber);
            } catch (NumberFormatException ignored) {}
        }
        final String discNumber = getMetaId(media, null, Meta.DiscNumber, false);
        if (!TextUtils.isEmpty(discNumber)) {
            try {
                mDiscNumber = Integer.parseInt(discNumber);
            } catch (NumberFormatException ignored) {}
        }
    }

    public void updateMeta(MediaPlayer mediaPlayer) {
        if (!TextUtils.isEmpty(mTitle) && TextUtils.isEmpty(mDisplayTitle))
            mDisplayTitle = mTitle;
        final Media media = mediaPlayer.getMedia();
        if (media == null)
            return;
        updateMeta(media);
        media.release();
    }

    public String getFileName() {
        if (mFilename == null) {
            mFilename = mUri.getLastPathSegment();
        }
        return mFilename;
    }

    public long getTime() {
        return mTime;
    }

    public void setTime(long time) {
        mTime = time;
    }

    public int getAudioTrack() {
        return mAudioTrack;
    }

    public void setAudioTrack(int track) {
        mAudioTrack = track;
    }

    public int getSpuTrack() {
        return mSpuTrack;
    }

    public void setSpuTrack(int track) {
        mSpuTrack = track;
    }

    public long getLength() {
        return mLength;
    }

    public int getType() {
        return mType;
    }

    public void setType(int type){
        mType = type;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    /**
     * Returns the raw picture object. Likely to be NULL in VLC for Android
     * due to lazy-loading.
     *
     * @return The raw picture or NULL
     */
    public Bitmap getPicture() {
        return mPicture;
    }

    /**
     * Sets the raw picture object.
     *
     * @param p Bitmap picture
     */
    public void setPicture(Bitmap p) {
        mPicture = p;
    }

    public boolean isPictureParsed() {
        return mIsPictureParsed;
    }

    public void setPictureParsed(boolean isParsed) {
        mIsPictureParsed = isParsed;
    }

    public void setDisplayTitle(String title){
        mDisplayTitle = title;
    }

    public void setArtist(String artist){
        mArtist = artist;
    }

    public String getTitle() {
        if (!TextUtils.isEmpty(mDisplayTitle))
            return mDisplayTitle;
        if (!TextUtils.isEmpty(mTitle))
            return mTitle;
        String fileName = getFileName();
        if (fileName == null)
            return "";
        int end = fileName.lastIndexOf(".");
        if (end <= 0)
            return fileName;
        return fileName.substring(0, end);
    }

    public String getReferenceArtist() {
        return mAlbumArtist == null ? mArtist : mAlbumArtist;
    }

    public String getArtist() {
        return mArtist;
    }

    public Boolean isArtistUnknown() {
        return mArtist == null;
    }

    public String getGenre() {
        if (mGenre == null)
            return null;
        else if (mGenre.length() > 1)/* Make genres case insensitive via normalisation */
            return Character.toUpperCase(mGenre.charAt(0)) + mGenre.substring(1).toLowerCase(Locale.getDefault());
        else
            return mGenre;
    }

    public String getCopyright() {
        return mCopyright;
    }

    public String getAlbum() {
        return mAlbum;
    }

    public String getAlbumArtist() {
        return mAlbumArtist;
    }

    public Boolean isAlbumUnknown() {
        return mAlbum == null;
    }

    public int getTrackNumber() {
        return mTrackNumber;
    }

    public int getDiscNumber() {
        return mDiscNumber;
    }

    public void setDescription(String description){
        mDescription = description;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getRating() {
        return mRating;
    }

    public String getDate() {
        return mDate;
    }

    public String getSettings() {
        return mSettings;
    }

    public String getNowPlaying() {
        return mNowPlaying;
    }

    public String getPublisher() {
        return mPublisher;
    }

    public String getEncodedBy() {
        return mEncodedBy;
    }

    public String getTrackID() {
        return mTrackID;
    }

    public String getArtworkURL() {
        return mArtworkURL;
    }

    public String getArtworkMrl() {
        return mArtworkURL;
    }

    public void setArtworkURL(String url) {
        mArtworkURL = url;
    }

    public long getLastModified() {
        return mLastModified;
    }

    public void setLastModified(long mLastModified) {
        this.mLastModified = mLastModified;
    }

    public void addFlags(int flags) {
        mFlags |= flags;
    }
    public void setFlags(int flags) {
        mFlags = flags;
    }
    public int getFlags() {
        return mFlags;
    }
    public boolean hasFlag(int flag) {
        return (mFlags & flag) != 0;
    }
    public void removeFlags(int flags) {
        mFlags &= ~flags;
    }

    @Nullable
    public Media.Slave[] getSlaves() {
        return mSlaves;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public MediaWrapper(Parcel in) {
        mUri = in.readParcelable(Uri.class.getClassLoader());
        init(in.readLong(),
                in.readLong(),
                in.readInt(),
                (Bitmap) in.readParcelable(Bitmap.class.getClassLoader()),
                in.readString(),
                in.readString(),
                in.readString(),
                in.readString(),
                in.readString(),
                in.readInt(),
                in.readInt(),
                in.readString(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readLong(),
                in.createTypedArray(PSlave.CREATOR));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mUri, flags);
        dest.writeLong(getTime());
        dest.writeLong(getLength());
        dest.writeInt(getType());
        dest.writeParcelable(getPicture(), flags);
        dest.writeString(getTitle());
        dest.writeString(getArtist());
        dest.writeString(getGenre());
        dest.writeString(getAlbum());
        dest.writeString(getAlbumArtist());
        dest.writeInt(getWidth());
        dest.writeInt(getHeight());
        dest.writeString(getArtworkURL());
        dest.writeInt(getAudioTrack());
        dest.writeInt(getSpuTrack());
        dest.writeInt(getTrackNumber());
        dest.writeInt(getDiscNumber());
        dest.writeLong(getLastModified());

        if (mSlaves != null) {
            PSlave pslaves[] = new PSlave[mSlaves.length];
            for (int i = 0; i < mSlaves.length; ++i) {
                pslaves[i] = new PSlave(mSlaves[i]);
            }
            dest.writeTypedArray(pslaves, flags);
        }
        else
            dest.writeTypedArray(null, flags);
    }

    public static final Parcelable.Creator<MediaWrapper> CREATOR = new Parcelable.Creator<MediaWrapper>() {
        public MediaWrapper createFromParcel(Parcel in) {
            return new MediaWrapper(in);
        }
        public MediaWrapper[] newArray(int size) {
            return new MediaWrapper[size];
        }
    };

    private static class PSlave extends Media.Slave implements Parcelable {

        protected PSlave(Media.Slave slave) {
            super(slave.type, slave.priority, slave.uri);
        }

        protected PSlave(Parcel in) {
            super(in.readInt(), in.readInt(), in.readString());
        }

        public static final Creator<PSlave> CREATOR = new Creator<PSlave>() {
            @Override
            public PSlave createFromParcel(Parcel in) {
                return new PSlave(in);
            }

            @Override
            public PSlave[] newArray(int size) {
                return new PSlave[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(type);
            parcel.writeInt(priority);
            parcel.writeString(uri);
        }
    }
}