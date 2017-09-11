package cn.edu.cqupt.gameclock.activity;

import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;
import android.view.MenuItem;

import cn.edu.cqupt.gameclock.AppSettings;
import cn.edu.cqupt.gameclock.R;

/**
 * Created by wentai on 17-8-21.
 */

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppSettings.setTheme(getBaseContext(), AboutActivity.this);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        setTitle(getString(R.string.about));

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
