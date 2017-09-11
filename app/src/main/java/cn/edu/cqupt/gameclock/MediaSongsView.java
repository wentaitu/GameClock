package cn.edu.cqupt.gameclock;

import java.util.ArrayList;
import java.util.Arrays;

import android.content.Context;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;

public class MediaSongsView extends MediaListView implements OnItemClickListener {
  private final String[] songsColumns = new String[] {
    MediaColumns.TITLE,
  };

  final int[] songsResIDs = new int[] {
      R.id.media_value,
  };

  public MediaSongsView(Context context) {
    this(context, null);
  }

  public MediaSongsView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public MediaSongsView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    overrideSortOrder(MediaColumns.TITLE + " ASC");
  }

  public void query(Uri contentUri) {
    query(contentUri, null);
  }

  public void query(Uri contentUri, String selection) {
    super.query(contentUri, MediaColumns.TITLE, selection, R.layout.media_picker_row, songsColumns, songsResIDs);
  }

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    super.onItemClick(parent, view, position, id);

    MediaPlayer mPlayer = getMediaPlayer();
    if (mPlayer == null) {
      return;
    }
    mPlayer.reset();
    try {
      mPlayer.setDataSource(getContext(), getLastSelectedUri());
      mPlayer.prepare();
      mPlayer.start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void includeDefault() {
    final ArrayList<String> defaultColumns =
      new ArrayList<>(songsColumns.length + 1);
    defaultColumns.addAll(Arrays.asList(songsColumns));
    defaultColumns.add(BaseColumns._ID);
    final MatrixCursor defaultsCursor = new MatrixCursor(defaultColumns.toArray(
            new String[defaultColumns.size()]));
    RowBuilder row = defaultsCursor.newRow();
    row.add("Default");
    row.add(DEFAULT_TONE_INDEX);
    includeStaticCursor(defaultsCursor);
  }
}
