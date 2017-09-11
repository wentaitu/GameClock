package cn.edu.cqupt.gameclock;

import cn.edu.cqupt.gameclock.MediaListView.OnItemPickListener;

import android.app.Activity;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Message;
import android.provider.MediaStore.Audio.Albums;
import android.provider.MediaStore.Audio.Artists;
import android.provider.MediaStore.Audio.Media;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.ViewFlipper;
import android.widget.TabHost.OnTabChangeListener;

/**
 * Created by wentai on 17-8-21.
 */

//获取手机中所有音乐文件
public class MediaPickerDialog extends AlertDialog {
  public interface OnMediaPickListener {
    void onMediaPick(String name, Uri media);
  }

  private final String ARTISTS_TAB = "artists";
  private final String ALBUMS_TAB = "albums";

  private String selectedName;
  private Uri selectedUri;
  private OnMediaPickListener pickListener;
  private MediaPlayer mediaPlayer;

  public MediaPickerDialog(final Activity context) {
    super(context);
    mediaPlayer = new MediaPlayer();

    final View body_view = View.inflate(context, R.layout.media_picker_dialog,
            null);
    setView(body_view);

    TabHost tabs = (TabHost) body_view.findViewById(R.id.media_tabs);
    tabs.setup();

    final String INTERNAL_TAB = "internal";
    final String ALL_SONGS_TAB = "songs";

    tabs.addTab(tabs.newTabSpec(INTERNAL_TAB).setContent(R.id.media_picker_internal).setIndicator(context.getString(R.string.internal)));
    tabs.addTab(tabs.newTabSpec(ARTISTS_TAB).setContent(R.id.media_picker_artists).setIndicator(context.getString(R.string.artists)));
    tabs.addTab(tabs.newTabSpec(ALBUMS_TAB).setContent(R.id.media_picker_albums).setIndicator(context.getString(R.string.albums)));
    tabs.addTab(tabs.newTabSpec(ALL_SONGS_TAB).setContent(R.id.media_picker_songs).setIndicator(context.getString(R.string.songs)));

    final TextView lastSelected = (TextView) body_view.findViewById(R.id.media_picker_status);
    final OnItemPickListener listener = new OnItemPickListener() {
      @Override
      public void onItemPick(Uri uri, String name) {
        selectedUri = uri;
        selectedName = name;
        lastSelected.setText(context.getString(R.string.selected_media, name));
      }
    };

    final MediaSongsView internalList = (MediaSongsView) body_view.findViewById(R.id.media_picker_internal);
    internalList.setCursorManager(context);
    internalList.includeDefault();
    internalList.query(Media.INTERNAL_CONTENT_URI);
    internalList.setMediaPlayer(mediaPlayer);
    internalList.setMediaPickListener(listener);

    final MediaSongsView songsList = (MediaSongsView) body_view.findViewById(R.id.media_picker_songs);
    songsList.setCursorManager(context);
    songsList.query(Media.EXTERNAL_CONTENT_URI);
    songsList.setMediaPlayer(mediaPlayer);
    songsList.setMediaPickListener(listener);

    final ViewFlipper artistsFlipper = (ViewFlipper) body_view.findViewById(R.id.media_picker_artists);
    final MediaArtistsView artistsList = new MediaArtistsView(context);
    artistsList.setCursorManager(context);
    artistsList.addToFlipper(artistsFlipper);
    artistsList.query(Artists.EXTERNAL_CONTENT_URI);
    artistsList.setMediaPlayer(mediaPlayer);
    artistsList.setMediaPickListener(listener);

    final ViewFlipper albumsFlipper = (ViewFlipper) body_view.findViewById(R.id.media_picker_albums);
    final MediaAlbumsView albumsList = new MediaAlbumsView(context);
    albumsList.setCursorManager(context);
    albumsList.addToFlipper(albumsFlipper);
    albumsList.query(Albums.EXTERNAL_CONTENT_URI);
    albumsList.setMediaPlayer(mediaPlayer);
    albumsList.setMediaPickListener(listener);

    tabs.setOnTabChangedListener(new OnTabChangeListener() {
      @Override
      public void onTabChanged(String tabId) {
        if (tabId.equals(ARTISTS_TAB)) {
          artistsFlipper.setDisplayedChild(0);
        } else if (tabId.equals(ALBUMS_TAB)) {
          albumsFlipper.setDisplayedChild(0);
        }
      }
    });

    super.setButton(BUTTON_POSITIVE, getContext().getString(R.string.ok),
      new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          if (selectedUri == null || pickListener == null) {
            cancel();
            return;
          }
          pickListener.onMediaPick(selectedName, selectedUri);
        }
    });

    super.setButton(BUTTON_NEGATIVE, getContext().getString(R.string.cancel),
        new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            selectedName = null;
            selectedUri = null;
            lastSelected.setText("");
            cancel();
          }
      });
  }

  public void setPickListener(OnMediaPickListener listener) {
    this.pickListener = listener;
  }

  @Override
  protected void onStop() {
    super.onStop();
    mediaPlayer.stop();
  }

  @Override
  protected void finalize() throws Throwable {
    mediaPlayer.release();
    super.finalize();
  }

  @Override
  public final void setButton(int whichButton, CharSequence text, Message msg) {}
  @Override
  public final void setButton(int whichButton, CharSequence text, OnClickListener listener) {}
}
