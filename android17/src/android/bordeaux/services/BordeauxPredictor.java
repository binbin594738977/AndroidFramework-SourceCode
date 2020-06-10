/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.bordeaux.services;

import android.bordeaux.services.IPredictor;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Predictor for the Learning framework.
 */
public class BordeauxPredictor {
    static final String TAG = "BordeauxPredictor";
    static final String PREDICTOR_NOTAVAILABLE = "Predictor is not available.";
    private Context mContext;
    private String mName;
    private IPredictor mPredictor;

    public boolean retrievePredictor() {
        if (mPredictor == null)
            mPredictor = BordeauxManagerService.getPredictor(mContext, mName);
        if (mPredictor == null) {
            Log.e(TAG, PREDICTOR_NOTAVAILABLE);
            return false;
        }
        return true;
    }

    public BordeauxPredictor(Context context) {
        mContext = context;
        mName = "defaultPredictor";
        mPredictor = BordeauxManagerService.getPredictor(context, mName);
    }

    public BordeauxPredictor(Context context, String name) {
        mContext = context;
        mName = name;
        mPredictor = BordeauxManagerService.getPredictor(context, mName);
    }

    public boolean reset() {
        if (!retrievePredictor()){
            Log.e(TAG, PREDICTOR_NOTAVAILABLE);
            return false;
        }
        try {
            mPredictor.ResetPredictor();
            return true;
        } catch (RemoteException e) {
        }
        return false;
    }

    public void pushSample(String s) {
        if (!retrievePredictor())
            throw new RuntimeException(PREDICTOR_NOTAVAILABLE);
        try {
            mPredictor.pushNewSample(s);
        } catch (RemoteException e) {
            Log.e(TAG,"Exception: pushing a new example");
            throw new RuntimeException(PREDICTOR_NOTAVAILABLE);
        }
    }

    public float getProbability(String s) {
        if (!retrievePredictor())
            throw new RuntimeException(PREDICTOR_NOTAVAILABLE);
        try {
            return mPredictor.getSampleProbability(s);
        } catch (RemoteException e) {
            Log.e(TAG,"Exception: getting sample probability");
            throw new RuntimeException(PREDICTOR_NOTAVAILABLE);
        }
    }

    public boolean setParameter(String key, String value) {
        if (!retrievePredictor())
            throw new RuntimeException(PREDICTOR_NOTAVAILABLE);
        try {
            return mPredictor.setPredictorParameter(key, value);
        } catch (RemoteException e) {
            Log.e(TAG,"Exception: setting predictor parameter");
            throw new RuntimeException(PREDICTOR_NOTAVAILABLE);
        }
    }
}
