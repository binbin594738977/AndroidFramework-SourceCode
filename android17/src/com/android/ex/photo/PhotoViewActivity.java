/*
 * Copyright (C) 2011 Google Inc.
 * Licensed to The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ex.photo;

import android.app.ActionBar;
import android.app.ActionBar.OnMenuVisibilityListener;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.View;

import com.android.ex.photo.PhotoViewPager.InterceptType;
import com.android.ex.photo.PhotoViewPager.OnInterceptTouchListener;
import com.android.ex.photo.adapters.PhotoPagerAdapter;
import com.android.ex.photo.fragments.PhotoViewFragment;
import com.android.ex.photo.loaders.PhotoPagerLoader;
import com.android.ex.photo.provider.PhotoContract;

import java.util.HashSet;
import java.util.Set;

/**
 * Activity to view the contents of an album.
 */
public class PhotoViewActivity extends Activity implements
        LoaderCallbacks<Cursor>, OnPageChangeListener, OnInterceptTouchListener,
        OnMenuVisibilityListener {

    /**
     * Listener to be invoked for screen events.
     */
    public static interface OnScreenListener {

        /**
         * The full screen state has changed.
         */
        public void onFullScreenChanged(boolean fullScreen);

        /**
         * A new view has been activated and the previous view de-activated.
         */
        public void onViewActivated();

        /**
         * Called when a right-to-left touch move intercept is about to occur.
         *
         * @param origX the raw x coordinate of the initial touch
         * @param origY the raw y coordinate of the initial touch
         * @return {@code true} if the touch should be intercepted.
         */
        public boolean onInterceptMoveLeft(float origX, float origY);

        /**
         * Called when a left-to-right touch move intercept is about to occur.
         *
         * @param origX the raw x coordinate of the initial touch
         * @param origY the raw y coordinate of the initial touch
         * @return {@code true} if the touch should be intercepted.
         */
        public boolean onInterceptMoveRight(float origX, float origY);
    }

    public static interface CursorChangedListener {
        /**
         * Called when the cursor that contains the photo list data
         * is updated. Note that there is no guarantee that the cursor
         * will be at the proper position.
         * @param cursor the cursor containing the photo list data
         */
        public void onCursorChanged(Cursor cursor);
    }

    private final static String STATE_ITEM_KEY =
            "com.google.android.apps.plus.PhotoViewFragment.ITEM";
    private final static String STATE_FULLSCREEN_KEY =
            "com.google.android.apps.plus.PhotoViewFragment.FULLSCREEN";

    private static final int LOADER_PHOTO_LIST = 1;

    /** Count used when the real photo count is unknown [but, may be determined] */
    public static final int ALBUM_COUNT_UNKNOWN = -1;

    /** Argument key for the dialog message */
    public static final String KEY_MESSAGE = "dialog_message";

    public static int sMemoryClass;

    /** The URI of the photos we're viewing; may be {@code null} */
    private String mPhotosUri;
    /** The index of the currently viewed photo */
    private int mPhotoIndex;
    /** The query projection to use; may be {@code null} */
    private String[] mProjection;
    /** The total number of photos; only valid if {@link #mIsEmpty} is {@code false}. */
    private int mAlbumCount = ALBUM_COUNT_UNKNOWN;
    /** {@code true} if the view is empty. Otherwise, {@code false}. */
    private boolean mIsEmpty;
    /** The main pager; provides left/right swipe between photos */
    private PhotoViewPager mViewPager;
    /** Adapter to create pager views */
    private PhotoPagerAdapter mAdapter;
    /** Whether or not we're in "full screen" mode */
    private boolean mFullScreen;
    /** The set of listeners wanting full screen state */
    private Set<OnScreenListener> mScreenListeners = new HashSet<OnScreenListener>();
    /** The set of listeners wanting full screen state */
    private Set<CursorChangedListener> mCursorListeners = new HashSet<CursorChangedListener>();
    /** When {@code true}, restart the loader when the activity becomes active */
    private boolean mRestartLoader;
    /** Whether or not this activity is paused */
    private boolean mIsPaused = true;
    private final Handler mHandler = new Handler();
    // TODO Find a better way to do this. We basically want the activity to display the
    // "loading..." progress until the fragment takes over and shows it's own "loading..."
    // progress [located in photo_header_view.xml]. We could potentially have all status displayed
    // by the activity, but, that gets tricky when it comes to screen rotation. For now, we
    // track the loading by this variable which is fragile and may cause phantom "loading..."
    // text.
    private long mActionBarHideDelayTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActivityManager mgr = (ActivityManager) getApplicationContext().
                getSystemService(Activity.ACTIVITY_SERVICE);
        sMemoryClass = mgr.getMemoryClass();

        Intent mIntent = getIntent();

        int currentItem = -1;
        if (savedInstanceState != null) {
            currentItem = savedInstanceState.getInt(STATE_ITEM_KEY, -1);
            mFullScreen = savedInstanceState.getBoolean(STATE_FULLSCREEN_KEY, false);
        }

        // uri of the photos to view; optional
        if (mIntent.hasExtra(Intents.EXTRA_PHOTOS_URI)) {
            mPhotosUri = mIntent.getStringExtra(Intents.EXTRA_PHOTOS_URI);
        }

        // projection for the query; optional
        // I.f not set, the default projection is used.
        // This projection must include the columns from the default projection.
        if (mIntent.hasExtra(Intents.EXTRA_PROJECTION)) {
            mProjection = mIntent.getStringArrayExtra(Intents.EXTRA_PROJECTION);
        } else {
            mProjection = null;
        }

        // Set the current item from the intent if wasn't in the saved instance
        if (mIntent.hasExtra(Intents.EXTRA_PHOTO_INDEX) && currentItem < 0) {
            currentItem = mIntent.getIntExtra(Intents.EXTRA_PHOTO_INDEX, -1);
        }
        mPhotoIndex = currentItem;

        setContentView(R.layout.photo_activity_view);

        // Create the adapter and add the view pager
        mAdapter = new PhotoPagerAdapter(this, getFragmentManager(), null);

        mViewPager = (PhotoViewPager) findViewById(R.id.photo_view_pager);
        mViewPager.setAdapter(mAdapter);
        mViewPager.setOnPageChangeListener(this);
        mViewPager.setOnInterceptTouchListener(this);

        // Kick off the loader
        getLoaderManager().initLoader(LOADER_PHOTO_LIST, null, this);

        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        mActionBarHideDelayTime = getResources().getInteger(
                R.integer.action_bar_delay_time_in_millis);
        actionBar.addOnMenuVisibilityListener(this);
        actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setFullScreen(mFullScreen, false);

        mIsPaused = false;
        if (mRestartLoader) {
            mRestartLoader = false;
            getLoaderManager().restartLoader(LOADER_PHOTO_LIST, null, this);
        }
    }

    @Override
    protected void onPause() {
        mIsPaused = true;

        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // If in full screen mode, toggle mode & eat the 'back'
        if (mFullScreen) {
            toggleFullScreen();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(STATE_ITEM_KEY, mViewPager.getCurrentItem());
        outState.putBoolean(STATE_FULLSCREEN_KEY, mFullScreen);
    }

    public void addScreenListener(OnScreenListener listener) {
        mScreenListeners.add(listener);
    }

    public void removeScreenListener(OnScreenListener listener) {
        mScreenListeners.remove(listener);
    }

    public synchronized void addCursorListener(CursorChangedListener listener) {
        mCursorListeners.add(listener);
    }

    public synchronized void removeCursorListener(CursorChangedListener listener) {
        mCursorListeners.remove(listener);
    }

    public boolean isFragmentFullScreen(Fragment fragment) {
        if (mViewPager == null || mAdapter == null || mAdapter.getCount() == 0) {
            return mFullScreen;
        }
        return mFullScreen || (mViewPager.getCurrentItem() != mAdapter.getItemPosition(fragment));
    }

    public void toggleFullScreen() {
        setFullScreen(!mFullScreen, true);
    }

    public void onPhotoRemoved(long photoId) {
        final Cursor data = mAdapter.getCursor();
        if (data == null) {
            // Huh?! How would this happen?
            return;
        }

        final int dataCount = data.getCount();
        if (dataCount <= 1) {
            finish();
            return;
        }

        getLoaderManager().restartLoader(LOADER_PHOTO_LIST, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == LOADER_PHOTO_LIST) {
            return new PhotoPagerLoader(this, Uri.parse(mPhotosUri), mProjection);
        }
        return null;
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
        final int id = loader.getId();
        if (id == LOADER_PHOTO_LIST) {
            if (data == null || data.getCount() == 0) {
                mIsEmpty = true;
            } else {
                mAlbumCount = data.getCount();

                // We're paused; don't do anything now, we'll get re-invoked
                // when the activity becomes active again
                // TODO(pwestbro): This shouldn't be necessary, as the loader manager should
                // restart the loader
                if (mIsPaused) {
                    mRestartLoader = true;
                    return;
                }
                mIsEmpty = false;

                // set the selected photo
                int itemIndex = mPhotoIndex;

                // Use an index of 0 if the index wasn't specified or couldn't be found
                if (itemIndex < 0) {
                    itemIndex = 0;
                }

                mAdapter.swapCursor(data);
                notifyCursorListeners(data);

                mViewPager.setCurrentItem(itemIndex, false);
                setViewActivated();
            }
            // Update the any action items
            updateActionItems();
        }
    }

    protected void updateActionItems() {
        // Do nothing, but allow extending classes to do work
    }

    private synchronized void notifyCursorListeners(Cursor data) {
        // tell all of the objects listening for cursor changes
        // that the cursor has changed
        for (CursorChangedListener listener : mCursorListeners) {
            listener.onCursorChanged(data);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // If the loader is reset, remove the reference in the adapter to this cursor
        // TODO(pwestbro): reenable this when b/7075236 is fixed
        // mAdapter.swapCursor(null);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        mPhotoIndex = position;
        setViewActivated();
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    public boolean isFragmentActive(Fragment fragment) {
        if (mViewPager == null || mAdapter == null) {
            return false;
        }
        return mViewPager.getCurrentItem() == mAdapter.getItemPosition(fragment);
    }

    public void onFragmentVisible(PhotoViewFragment fragment) {
        updateActionBar(fragment);
    }

    @Override
    public InterceptType onTouchIntercept(float origX, float origY) {
        boolean interceptLeft = false;
        boolean interceptRight = false;

        for (OnScreenListener listener : mScreenListeners) {
            if (!interceptLeft) {
                interceptLeft = listener.onInterceptMoveLeft(origX, origY);
            }
            if (!interceptRight) {
                interceptRight = listener.onInterceptMoveRight(origX, origY);
            }
            listener.onViewActivated();
        }

        if (interceptLeft) {
            if (interceptRight) {
                return InterceptType.BOTH;
            }
            return InterceptType.LEFT;
        } else if (interceptRight) {
            return InterceptType.RIGHT;
        }
        return InterceptType.NONE;
    }

    /**
     * Updates the title bar according to the value of {@link #mFullScreen}.
     */
    private void setFullScreen(boolean fullScreen, boolean setDelayedRunnable) {
        final boolean fullScreenChanged = (fullScreen != mFullScreen);
        mFullScreen = fullScreen;

        if (mFullScreen) {
            setLightsOutMode(true);
            cancelActionBarHideRunnable();
        } else {
            setLightsOutMode(false);
            if (setDelayedRunnable) {
                postActionBarHideRunnableWithDelay();
            }
        }

        if (fullScreenChanged) {
            for (OnScreenListener listener : mScreenListeners) {
                listener.onFullScreenChanged(mFullScreen);
            }
        }
    }

    private void postActionBarHideRunnableWithDelay() {
        mHandler.postDelayed(mActionBarHideRunnable,
                mActionBarHideDelayTime);
    }

    private void cancelActionBarHideRunnable() {
        mHandler.removeCallbacks(mActionBarHideRunnable);
    }

    private void setLightsOutMode(boolean enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            int flags = enabled
                    ? View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    : View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

            // using mViewPager since we have it and we need a view
            mViewPager.setSystemUiVisibility(flags);
        } else {
            final ActionBar actionBar = getActionBar();
            if (enabled) {
                actionBar.hide();
            } else {
                actionBar.show();
            }
            int flags = enabled
                    ? View.SYSTEM_UI_FLAG_LOW_PROFILE
                    : View.SYSTEM_UI_FLAG_VISIBLE;
            mViewPager.setSystemUiVisibility(flags);
        }
    }

    private Runnable mActionBarHideRunnable = new Runnable() {
        @Override
        public void run() {
            PhotoViewActivity.this.setLightsOutMode(true);
        }
    };

    public void setViewActivated() {
        for (OnScreenListener listener : mScreenListeners) {
            listener.onViewActivated();
        }
    }

    /**
     * Adjusts the activity title and subtitle to reflect the photo name and count.
     */
    protected void updateActionBar(PhotoViewFragment fragment) {
        final int position = mViewPager.getCurrentItem() + 1;
        final String title;
        final String subtitle;
        final boolean hasAlbumCount = mAlbumCount >= 0;

        final Cursor cursor = getCursorAtProperPosition();

        if (cursor != null) {
            final int photoNameIndex = cursor.getColumnIndex(PhotoContract.PhotoViewColumns.NAME);
            title = cursor.getString(photoNameIndex);
        } else {
            title = null;
        }

        if (mIsEmpty || !hasAlbumCount || position <= 0) {
            subtitle = null;
        } else {
            subtitle = getResources().getString(R.string.photo_view_count, position, mAlbumCount);
        }

        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE, ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setTitle(title);
        actionBar.setSubtitle(subtitle);
    }

    /**
     * Utility method that will return the cursor that contains the data
     * at the current position so that it refers to the current image on screen.
     * @return the cursor at the current position or
     * null if no cursor exists or if the {@link PhotoViewPager} is null.
     */
    public Cursor getCursorAtProperPosition() {
        if (mViewPager == null) {
            return null;
        }

        final int position = mViewPager.getCurrentItem();
        final Cursor cursor = mAdapter.getCursor();

        if (cursor == null) {
            return null;
        }

        cursor.moveToPosition(position);

        return cursor;
    }

    public Cursor getCursor() {
        return (mAdapter == null) ? null : mAdapter.getCursor();
    }

    @Override
    public void onMenuVisibilityChanged(boolean isVisible) {
        if (isVisible) {
            cancelActionBarHideRunnable();
        } else {
            postActionBarHideRunnableWithDelay();
        }
    }

    public void onNewPhotoLoaded() {
    }
}
