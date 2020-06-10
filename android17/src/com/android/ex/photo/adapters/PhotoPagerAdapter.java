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

package com.android.ex.photo.adapters;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.database.Cursor;

import com.android.ex.photo.Intents;
import com.android.ex.photo.Intents.PhotoViewIntentBuilder;
import com.android.ex.photo.fragments.PhotoViewFragment;
import com.android.ex.photo.provider.PhotoContract;

/**
 * Pager adapter for the photo view
 */
public class PhotoPagerAdapter extends BaseCursorPagerAdapter {
    private int mContentUriIndex;
    private int mThumbnailUriIndex;

    public PhotoPagerAdapter(Context context, FragmentManager fm, Cursor c) {
        super(context, fm, c);
    }

    @Override
    public Fragment getItem(Context context, Cursor cursor, int position) {
        final String photoUri = cursor.getString(mContentUriIndex);
        final String thumbnailUri = cursor.getString(mThumbnailUriIndex);

        // create new PhotoViewFragment
        final PhotoViewIntentBuilder builder =
                Intents.newPhotoViewFragmentIntentBuilder(mContext);
        builder
            .setResolvedPhotoUri(photoUri)
            .setThumbnailUri(thumbnailUri);

        return new PhotoViewFragment(builder.build(), position, this);
    }

    @Override
    public Cursor swapCursor(Cursor newCursor) {
        if (newCursor != null) {
            mContentUriIndex =
                    newCursor.getColumnIndex(PhotoContract.PhotoViewColumns.CONTENT_URI);
            mThumbnailUriIndex =
                    newCursor.getColumnIndex(PhotoContract.PhotoViewColumns.THUMBNAIL_URI);
        } else {
            mContentUriIndex = -1;
            mThumbnailUriIndex = -1;
        }

        return super.swapCursor(newCursor);
    }

    public String getPhotoUri(Cursor cursor) {
        return cursor.getString(mContentUriIndex);
    }
}
