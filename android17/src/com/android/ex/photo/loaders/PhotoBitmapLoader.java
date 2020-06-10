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

package com.android.ex.photo.loaders;

import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.DisplayMetrics;

import com.android.ex.photo.fragments.PhotoViewFragment;
import com.android.ex.photo.util.ImageUtils;

/**
 * Loader for the bitmap of a photo.
 */
public class PhotoBitmapLoader extends AsyncTaskLoader<Bitmap> {
    private String mPhotoUri;

    private Bitmap mBitmap;

    public PhotoBitmapLoader(Context context, String photoUri) {
        super(context);
        mPhotoUri = photoUri;
    }

    public void setPhotoUri(String photoUri) {
        mPhotoUri = photoUri;
    }

    @Override
    public Bitmap loadInBackground() {
        Context context = getContext();

        if (context != null && mPhotoUri != null) {
            final ContentResolver resolver = context.getContentResolver();
            Bitmap bitmap = ImageUtils.createLocalBitmap(resolver, Uri.parse(mPhotoUri),
                    PhotoViewFragment.sPhotoSize);
            if (bitmap != null) {
                bitmap.setDensity(DisplayMetrics.DENSITY_MEDIUM);
            }
            return bitmap;
        }

        return null;
    }

    /**
     * Called when there is new data to deliver to the client.  The
     * super class will take care of delivering it; the implementation
     * here just adds a little more logic.
     */
    @Override
    public void deliverResult(Bitmap bitmap) {
        if (isReset()) {
            // An async query came in while the loader is stopped.  We
            // don't need the result.
            if (bitmap != null) {
                onReleaseResources(bitmap);
            }
        }
        Bitmap oldBitmap = mBitmap;
        mBitmap = bitmap;

        if (isStarted()) {
            // If the Loader is currently started, we can immediately
            // deliver its results.
            super.deliverResult(bitmap);
        }

        // At this point we can release the resources associated with
        // 'oldBitmap' if needed; now that the new result is delivered we
        // know that it is no longer in use.
        if (oldBitmap != null && oldBitmap != bitmap && !oldBitmap.isRecycled()) {
            onReleaseResources(oldBitmap);
        }
    }

    /**
     * Handles a request to start the Loader.
     */
    @Override
    protected void onStartLoading() {
        if (mBitmap != null) {
            // If we currently have a result available, deliver it
            // immediately.
            deliverResult(mBitmap);
        }

        if (takeContentChanged() || mBitmap == null) {
            // If the data has changed since the last time it was loaded
            // or is not currently available, start a load.
            forceLoad();
        }
    }

    /**
     * Handles a request to stop the Loader.
     */
    @Override protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    /**
     * Handles a request to cancel a load.
     */
    @Override
    public void onCanceled(Bitmap bitmap) {
        super.onCanceled(bitmap);

        // At this point we can release the resources associated with 'bitmap'
        // if needed.
        onReleaseResources(bitmap);
    }

    /**
     * Handles a request to completely reset the Loader.
     */
    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();

        // At this point we can release the resources associated with 'bitmap'
        // if needed.
        if (mBitmap != null) {
            onReleaseResources(mBitmap);
            mBitmap = null;
        }
    }

    /**
     * Helper function to take care of releasing resources associated
     * with an actively loaded data set.
     */
    protected void onReleaseResources(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
