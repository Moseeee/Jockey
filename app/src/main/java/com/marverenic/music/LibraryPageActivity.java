package com.marverenic.music;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.marverenic.music.adapters.AlbumGridAdapter;
import com.marverenic.music.adapters.ArtistPageAdapter;
import com.marverenic.music.adapters.SongListAdapter;
import com.marverenic.music.instances.Album;
import com.marverenic.music.instances.Artist;
import com.marverenic.music.instances.Genre;
import com.marverenic.music.instances.Playlist;
import com.marverenic.music.instances.Song;
import com.marverenic.music.utils.Debug;
import com.marverenic.music.utils.Fetch;
import com.marverenic.music.utils.Navigate;
import com.marverenic.music.utils.Themes;
import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class LibraryPageActivity extends Activity implements View.OnClickListener {

    public static final byte PLAYLIST = 0;
    public static final byte ARTIST = 1;
    public static final byte ALBUM = 2;
    public static final byte GENRE = 3;

    private byte type;

    private BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            update();
        }
    };
    private int albumCount = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: Split these into separate classes

        Themes.setTheme(this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        Object parent = getIntent().getParcelableExtra("entry");

        if (parent != null) {

            setContentView(R.layout.fragment_list_page);
            final ListView songListView = (ListView) findViewById(R.id.list);
            ArrayList<Song> songEntries = new ArrayList<>();

            //
            //      PLAYLIST SCANNER
            //
            if (parent.getClass().equals(Playlist.class)) {
                initPlaylist(parent, songEntries);
            }
            //
            //      ALBUM SCANNER
            //
            else if (parent.getClass().equals(Album.class)) {
                initAlbum(parent, songEntries, songListView);
            }
            //
            //      GENRE SCANNER
            //
            else if (parent.getClass().equals(Genre.class)) {
                initGenre(parent, songEntries);
            } else if (parent.getClass().equals(Artist.class)) {
                initArtist(parent);
            }

            if (type != ARTIST) {
                if (type != ALBUM) {
                    Comparator<Song> songComparator = new Comparator<Song>() {
                        @Override
                        public int compare(Song o1, Song o2) {
                            String o1c = o1.songName.toLowerCase(Locale.ENGLISH);
                            String o2c = o2.songName.toLowerCase(Locale.ENGLISH);
                            if (o1c.startsWith("the ")) {
                                o1c = o1c.substring(4);
                            } else if (o1c.startsWith("a ")) {
                                o1c = o1c.substring(2);
                            }
                            if (o2c.startsWith("the ")) {
                                o2c = o2c.substring(4);
                            } else if (o2c.startsWith("a ")) {
                                o2c = o2c.substring(2);
                            }
                            if (!o1c.matches("[a-z]") && o2c.matches("[a-z]")) {
                                return o2c.compareTo(o1c);
                            }
                            return o1c.compareTo(o2c);
                        }
                    };
                    Collections.sort(songEntries, songComparator);
                }

                SongListAdapter adapter = new SongListAdapter(songEntries, this);
                songListView.setAdapter(adapter);
                songListView.setOnItemClickListener(adapter);
                songListView.setOnItemLongClickListener(adapter);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (getActionBar() != null)
                    getActionBar().setElevation(getResources().getDimension(R.dimen.header_elevation));
                else
                    Debug.log(Debug.WTF, "LibraryPageActivity", "Couldn't find the action bar", this);
            }

            Themes.themeActivity(R.layout.fragment_list, getWindow().findViewById(android.R.id.content), this);
        } else {
            type = -1;
            setContentView(R.layout.page_error);
            Debug.log(Debug.WTF, "LibraryPageActivity", "An invalid item was passed as the parent object", this);
        }
        registerReceiver(updateReceiver, new IntentFilter(Player.UPDATE_BROADCAST));
        update();
    }

    private void initPlaylist(Object parent, ArrayList<Song> songEntries) {
        type = PLAYLIST;
        if (getActionBar() != null) getActionBar().setTitle(((Playlist) parent).playlistName);
        else Debug.log(Debug.WTF, "LibraryPageActivity", "Couldn't find the action bar", this);

        Cursor cur = getContentResolver().query(
                MediaStore.Audio.Playlists.Members.getContentUri("external", ((Playlist) parent).playlistId),
                new String[]{
                        MediaStore.Audio.Playlists.Members.TITLE,
                        MediaStore.Audio.Playlists.Members.ARTIST,
                        MediaStore.Audio.Playlists.Members.ALBUM,
                        MediaStore.Audio.Playlists.Members.DURATION,
                        MediaStore.Audio.Playlists.Members.DATA,
                        MediaStore.Audio.Playlists.Members.ALBUM_ID},
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null);

        for (int i = 0; i < cur.getCount(); i++) {
            cur.moveToPosition(i);
            songEntries.add(new Song(
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Playlists.Members.TITLE)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Playlists.Members.ARTIST)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Playlists.Members.ALBUM)),
                    cur.getInt(cur.getColumnIndex(MediaStore.Audio.Playlists.Members.DURATION)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Playlists.Members.DATA)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Playlists.Members.ALBUM_ID))));
        }
        cur.close();
    }

    private void initAlbum(Object parent, ArrayList<Song> songEntries, ListView songListView) {
        type = ALBUM;
        if (getActionBar() != null) getActionBar().setTitle(((Album) parent).albumName);
        else Debug.log(Debug.WTF, "LibraryPageActivity", "Couldn't find the action bar", this);

        Cursor cur = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.ALBUM_ID},
                MediaStore.Audio.Media.IS_MUSIC + "!= 0 AND " + MediaStore.Audio.Media.ALBUM_ID + "=?",
                new String[]{((Album) parent).albumId},
                MediaStore.Audio.Media.TRACK);
        cur.moveToFirst();

        for (int i = 0; i < cur.getCount(); i++) {
            cur.moveToPosition(i);
            songEntries.add(new Song(
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.TITLE)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.ARTIST)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.ALBUM)),
                    cur.getInt(cur.getColumnIndex(MediaStore.Audio.Media.DURATION)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.DATA)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))));
        }

        cur.close();

        Bitmap art = Fetch.fetchAlbumArtLocal(this, ((Album) parent).albumId);

        if (art != null) {
            View artView = View.inflate(this, R.layout.album_header, null);
            songListView.addHeaderView(artView, null, false);
            ((ImageView) findViewById(R.id.header)).setImageBitmap(art);
        }
    }

    private void initGenre(Object parent, ArrayList<Song> songEntries) {
        type = GENRE;
        if (getActionBar() != null) getActionBar().setTitle(((Genre) parent).genreName);
        else Debug.log(Debug.WTF, "LibraryPageActivity", "Couldn't find the action bar", this);

        Cursor cur = getContentResolver().query(
                MediaStore.Audio.Genres.Members.getContentUri("external", ((Genre) parent).genreId),
                new String[]{
                        MediaStore.Audio.Genres.Members.TITLE,
                        MediaStore.Audio.Genres.Members.ARTIST,
                        MediaStore.Audio.Genres.Members.ALBUM,
                        MediaStore.Audio.Genres.Members.DURATION,
                        MediaStore.Audio.Genres.Members.DATA,
                        MediaStore.Audio.Genres.Members.ALBUM_ID},
                MediaStore.Audio.Media.IS_MUSIC + " != 0 ", null, null);
        cur.moveToFirst();

        for (int i = 0; i < cur.getCount(); i++) {
            cur.moveToPosition(i);
            songEntries.add(new Song(
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Playlists.Members.TITLE)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Playlists.Members.ARTIST)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Playlists.Members.ALBUM)),
                    cur.getInt(cur.getColumnIndex(MediaStore.Audio.Playlists.Members.DURATION)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Playlists.Members.DATA)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Playlists.Members.ALBUM_ID))));
        }
        cur.close();
    }

    private void initArtist(Object parent) {
        type = ARTIST;
        if (getActionBar() != null) getActionBar().setTitle(((Artist) parent).artistName);
        else Debug.log(Debug.WTF, "LibraryPageActivity", "Couldn't find the action bar", this);

        ArrayList<Song> songs = new ArrayList<>();
        ArrayList<Album> albums = new ArrayList<>();

        Cursor cur = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null,
                MediaStore.Audio.Media.IS_MUSIC + "!= 0 AND " + MediaStore.Audio.Media.ARTIST_ID + "=?",
                new String[]{((Artist) parent).artistId + ""},
                MediaStore.Audio.Media.TITLE + " ASC");
        for (int i = 0; i < cur.getCount(); i++) {
            cur.moveToPosition(i);
            songs.add(new Song(
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.TITLE)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.ARTIST)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.ALBUM)),
                    cur.getInt(cur.getColumnIndex(MediaStore.Audio.Media.DURATION)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.DATA)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))));
        }
        cur.close();

        cur = getContentResolver().query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                null,
                MediaStore.Audio.Media.ARTIST_ID + "=?",
                new String[]{((Artist) parent).artistId + ""},
                MediaStore.Audio.Albums.FIRST_YEAR + " DESC, " + MediaStore.Audio.Media.ALBUM + " ASC");

        for (int i = 0; i < cur.getCount(); i++) {
            cur.moveToPosition(i);
            albums.add(new Album(
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Albums._ID)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Albums.ALBUM)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Albums.ARTIST)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Albums.LAST_YEAR)),
                    cur.getString(cur.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART))));
        }
        cur.close();

        Comparator<Song> songComparator = new Comparator<Song>() {
            @Override
            public int compare(Song o1, Song o2) {
                String o1c = o1.songName.toLowerCase(Locale.ENGLISH);
                String o2c = o2.songName.toLowerCase(Locale.ENGLISH);
                if (o1c.startsWith("the ")) {
                    o1c = o1c.substring(4);
                } else if (o1c.startsWith("a ")) {
                    o1c = o1c.substring(2);
                }
                if (o2c.startsWith("the ")) {
                    o2c = o2c.substring(4);
                } else if (o2c.startsWith("a ")) {
                    o2c = o2c.substring(2);
                }
                if (!o1c.matches("[a-z]") && o2c.matches("[a-z]")) {
                    return o1c.compareTo(o2c);
                }
                return o1c.compareTo(o2c);
            }
        };
        Collections.sort(songs, songComparator);

        ListView list = (ListView) findViewById(R.id.list);
        initializeArtistHeader(list, albums);
        ArtistPageAdapter adapter = new ArtistPageAdapter(this, songs, albums);
        list.setAdapter(adapter);
        list.setOnItemClickListener(adapter);
        list.setOnItemLongClickListener(adapter);
    }

    public void initializeArtistHeader(final View parent, final ArrayList<Album> albums) {
        final Context context = this;
        final View infoHeader = View.inflate(this, R.layout.artist_header_info, null);

        final Handler handler = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Fetch.ArtistBio bio = Fetch.fetchArtistBio(context, albums.get(0).artistName);
                if (bio != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            ((ImageView) infoHeader.findViewById(R.id.artist_image)).setImageBitmap(bio.art);

                            String bioText;
                            if (!bio.tags[0].equals("")) {
                                bioText = bio.tags[0].toUpperCase().charAt(0) + bio.tags[0].substring(1);
                                if (!bio.summary.equals("")) {
                                    bioText = bioText + " - " + bio.summary;
                                }
                            } else bioText = bio.summary;

                            ((TextView) infoHeader.findViewById(R.id.artist_bio)).setText(bioText);
                        }
                    });
                } else {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            //TODO This should probably fade out
                            ((ListView) parent).removeHeaderView(infoHeader);
                        }
                    });
                }
            }
        }).start();

        ((ListView) parent).addHeaderView(infoHeader, null, false);

        final View albumHeader = View.inflate(this, R.layout.artist_header_albums, null);
        final GridView albumGrid = (GridView) albumHeader.findViewById(R.id.albumGrid);
        AlbumGridAdapter gridAdapter = new AlbumGridAdapter(albums, context);
        albumGrid.setAdapter(gridAdapter);

        //updateArtistGridLayout(albumGrid, albums.size());
        albumCount = albums.size();

        ((ListView) parent).addHeaderView(albumHeader, null, false);

        updateArtistGridLayout((GridView) findViewById(R.id.albumGrid), albumCount);
        updateArtistHeader((ViewGroup) findViewById(R.id.artist_bio).getParent());
    }

    public void updateArtistGridLayout(GridView albumGrid, int albumCount) {
        final long screenWidth = getResources().getConfiguration().screenWidthDp;
        final float density = getResources().getDisplayMetrics().density;
        final long globalPadding = (long) (getResources().getDimension(R.dimen.global_padding) / density);
        final long gridPadding = (long) (getResources().getDimension(R.dimen.grid_padding) / density);
        final long extraHeight = 60;
        final long minWidth = (long) (getResources().getDimension(R.dimen.grid_width) / density);

        long availableWidth = screenWidth - 2 * (globalPadding + gridPadding);
        double numColumns = (availableWidth + gridPadding) / (minWidth + gridPadding);

        long columnWidth = (long) Math.floor(availableWidth / numColumns);
        long rowHeight = columnWidth + extraHeight;

        long numRows = (long) Math.ceil(albumCount / numColumns);

        long gridHeight = rowHeight * numRows + 2 * gridPadding;

        int height = (int) ((gridHeight * density));

        ViewGroup.LayoutParams albumParams = albumGrid.getLayoutParams();
        albumParams.height = height;
        albumGrid.setLayoutParams(albumParams);
    }

    public void updateArtistHeader(final ViewGroup bioHolder) {
        final TextView bioText = (TextView) bioHolder.findViewById(R.id.artist_bio);

        final long viewHeight = (long) (getResources().getDimension(R.dimen.artist_image_height));
        final long padding = (long) (getResources().getDimension(R.dimen.list_margin));

        final long availableHeight = (long) Math.floor(viewHeight - 2 * padding);

        long maxLines = (long) Math.floor(availableHeight / (bioText.getLineHeight()));
        bioText.setMaxLines((int) maxLines);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.miniplayer:
                Navigate.to(this, NowPlayingActivity.class);
                update();
                break;
            case R.id.playButton:
                PlayerService.togglePlay();
                update();
                break;
            case R.id.skipButton:
                PlayerService.skip();
                update();
                break;
        }
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    public void update() {
        if (type != -1) {
            if (PlayerService.isInitialized() && PlayerService.getNowPlaying() != null) {
                final TextView songTitle = (TextView) findViewById(R.id.textNowPlayingTitle);
                final TextView artistName = (TextView) findViewById(R.id.textNowPlayingDetail);

                songTitle.setText(PlayerService.getNowPlaying().songName);
                artistName.setText(PlayerService.getNowPlaying().artistName);

                if (!PlayerService.isPlaying()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ((ImageButton) findViewById(R.id.playButton)).setImageResource(R.drawable.ic_vector_play);
                        ((ImageButton) findViewById(R.id.playButton)).setImageTintList(ColorStateList.valueOf(Themes.getListText()));
                    } else {
                        if (Themes.isLight(this)) {
                            ((ImageButton) findViewById(R.id.playButton)).setImageResource(R.drawable.ic_play_miniplayer_light);
                        } else {
                            ((ImageButton) findViewById(R.id.playButton)).setImageResource(R.drawable.ic_play_miniplayer);
                        }
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ((ImageButton) findViewById(R.id.playButton)).setImageResource(R.drawable.ic_vector_pause);
                        ((ImageButton) findViewById(R.id.playButton)).setImageTintList(ColorStateList.valueOf(Themes.getListText()));
                    } else {
                        if (Themes.isLight(this)) {
                            ((ImageButton) findViewById(R.id.playButton)).setImageResource(R.drawable.ic_pause_miniplayer_light);
                        } else {
                            ((ImageButton) findViewById(R.id.playButton)).setImageResource(R.drawable.ic_pause_miniplayer);
                        }
                    }
                }

                if (PlayerService.getArt() != null) {
                    ((ImageView) findViewById(R.id.imageArtwork)).setImageBitmap(PlayerService.getArt());
                } else {
                    ((ImageView) findViewById(R.id.imageArtwork)).setImageResource(R.drawable.art_default);
                }

                FrameLayout.LayoutParams listLayoutParams = (FrameLayout.LayoutParams) (findViewById(R.id.list)).getLayoutParams();
                listLayoutParams.bottomMargin = getResources().getDimensionPixelSize(R.dimen.now_playing_ticker_height);
                (findViewById(R.id.list)).setLayoutParams(listLayoutParams);

                FrameLayout.LayoutParams playerLayoutParams = (FrameLayout.LayoutParams) (findViewById(R.id.miniplayer)).getLayoutParams();
                playerLayoutParams.height = getResources().getDimensionPixelSize(R.dimen.now_playing_ticker_height);
                (findViewById(R.id.miniplayer)).setLayoutParams(playerLayoutParams);
            } else {
                FrameLayout.LayoutParams listLayoutParams = (FrameLayout.LayoutParams) (findViewById(R.id.list)).getLayoutParams();
                listLayoutParams.bottomMargin = 0;
                (findViewById(R.id.list)).setLayoutParams(listLayoutParams);

                FrameLayout.LayoutParams playerLayoutParams = (FrameLayout.LayoutParams) (findViewById(R.id.miniplayer)).getLayoutParams();
                playerLayoutParams.height = 0;
                (findViewById(R.id.miniplayer)).setLayoutParams(playerLayoutParams);
            }
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Navigate.up(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Navigate.back(this);
    }

    @Override
    public void onPause() {
        if (ImageLoader.getInstance().isInited()) {
            ImageLoader.getInstance().clearMemoryCache();
        }
        super.onPause();
    }
}