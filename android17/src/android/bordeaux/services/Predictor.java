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

import android.os.IBinder;
import android.util.Log;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashSet;
import java.util.Iterator;
import java.io.Serializable;
import java.io.*;
import java.lang.Boolean;
import android.bordeaux.services.FeatureAssembly;
import android.bordeaux.learning.predictorHist;

/**
 * This is interface to implement Prediction based on histogram that
 * uses predictor_histogram from learnerning section
 */
public class Predictor extends IPredictor.Stub
        implements IBordeauxLearner {
    private ModelChangeCallback modelChangeCallback = null;
    private final String TAG = "Predictor";
    private final String SET_EXPIRE_TIME = "SetExpireTime";
    private final String USE_HISTORY = "Use History";
    private final String SET_FEATURE = "Set Feature";
    private long mExpireTime = 3 * 60;
    private long mLastSampleTime = 0;
    private boolean mUseHistoryFlag = false;
    private final String NEW_START = "New Start";

    static public class Model implements Serializable {
        public HashMap<String, Integer> countHistogram = new HashMap<String, Integer>();
        public HashSet<String> usedFeatures = new HashSet<String>();
        public int sampleCounts;
        public boolean useHistoryFlag;
        public long expireTime;
        public long lastSampleTime;
    }

    private predictorHist mPredictorHist = new predictorHist();
    private String mLastSample = NEW_START;
    public FeatureAssembly mFeatureAssembly = new FeatureAssembly();

    /**
     * Reset the Predictor
     */
    public void ResetPredictor(){
        printModel(getPredictionModel());
        mPredictorHist.ResetPredictorHist();
        mUseHistoryFlag = false;
        mLastSampleTime = 0;
        mLastSample = NEW_START;
        mFeatureAssembly = new FeatureAssembly();
        printModel(getPredictionModel());
        if (modelChangeCallback != null) {
            modelChangeCallback.modelChanged(this);
        }
    }

    /**
     * Augment input string with buildin features such as time, location
     */
    private String buildDataPoint(String sampleName) {
        String fs = mFeatureAssembly.augmentFeatureInputString(sampleName);
        if (mUseHistoryFlag) {
             if (((System.currentTimeMillis()- mLastSampleTime)/1000) > mExpireTime) {
                 mLastSample  = NEW_START;
             }
             fs = fs + "+" + mLastSample;
        }
        return fs;
    }

    /**
     * Input is a sampleName e.g.action name. This input is then augmented with requested build-in
     * features such as time and location to create sampleFeatures. The sampleFeatures is then
     * pushed to the histogram
     */
    public void pushNewSample(String sampleName) {
        String sampleFeatures = buildDataPoint(sampleName);
        mLastSample = sampleName;
        mLastSampleTime = System.currentTimeMillis();
        mPredictorHist.pushSample(sampleFeatures);
        if (modelChangeCallback != null) {
            modelChangeCallback.modelChanged(this);
        }
        //printModel(getPredictionModel());
    }

    /**
     * return probabilty of an exmple using the histogram
     */
    public float getSampleProbability(String sampleName) {
        String sampleFeatures = buildDataPoint(sampleName);
        return mPredictorHist.getProbability(sampleFeatures);
    }

    /**
     * Set parameters for 1) using History in probability estimations e.g. consider the last event
     * and 2) featureAssembly e.g. time and location.
     */
    public boolean setPredictorParameter(String s, String f) {
        boolean res = false;
        if (s.equals(USE_HISTORY)) {
            if (f.equals("true")){
                mUseHistoryFlag = true;
                res = true;
            }
            else if (f.equals("false")) {
                mUseHistoryFlag = false;
                res = true;
            }
        } else if (s.equals(SET_EXPIRE_TIME)) {
            mExpireTime = Long.valueOf(f);
            res = true;
        } else if (s.equals(SET_FEATURE)) {
            res = mFeatureAssembly.registerFeature(f);
        }
        if (!res)
            Log.e(TAG,"Setting parameter " + s + " with " + f + " is not valid");
        return res;
    }

    public Model getPredictionModel() {
        Model m = new Model();
        m.countHistogram.putAll(mPredictorHist.getHist());
        m.sampleCounts = mPredictorHist.getHistCounts();
        m.expireTime = mExpireTime;
        m.usedFeatures = (HashSet) mFeatureAssembly.getUsedFeatures();
        m.useHistoryFlag = mUseHistoryFlag;
        m.lastSampleTime = mLastSampleTime;
        return m;
    }

    public boolean loadModel(Model m) {
        //Log.i(TAG,"on loadModel");
        //printModel(m);
        mPredictorHist = new predictorHist();
        mPredictorHist.set(m.countHistogram);
        mExpireTime = m.expireTime;
        mUseHistoryFlag = m.useHistoryFlag;
        mLastSampleTime = m.lastSampleTime;
        mFeatureAssembly = new FeatureAssembly();
        boolean res = false;
        Iterator itr = m.usedFeatures.iterator();
        while(itr.hasNext()) {
            res = res & mFeatureAssembly.registerFeature((String) itr.next());
        }
        return res;
    }

    public void printModel(Model m) {
        Log.i(TAG,"histogram is : " + m.countHistogram.toString());
        Log.i(TAG,"number of counts in histogram is : " + m.sampleCounts);
        Log.i(TAG,"ExpireTime time is : " + m.expireTime);
        Log.i(TAG,"useHistoryFlag is : " + m.useHistoryFlag);
        Log.i(TAG,"used features are : " + m.usedFeatures.toString());
    }

    // Beginning of the IBordeauxLearner Interface implementation
    public byte [] getModel() {
        Model model = getPredictionModel();
        //Log.i(TAG,"on getModel");
        printModel(model);
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream objStream = new ObjectOutputStream(byteStream);
            objStream.writeObject(model);
            byte[] bytes = byteStream.toByteArray();
            //Log.i(TAG, "getModel: " + bytes);
            return bytes;
        } catch (IOException e) {
            throw new RuntimeException("Can't get model");
        }
    }

    public boolean setModel(final byte [] modelData) {
        //Log.i(TAG,"on setModel");
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(modelData);
            ObjectInputStream objStream = new ObjectInputStream(input);
            Model model = (Model) objStream.readObject();
            boolean res = loadModel(model);
            //Log.i(TAG, "LoadModel: " + modelData);
            return res;
        } catch (IOException e) {
            throw new RuntimeException("Can't load model");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Learning class not found");
        }
    }

    public IBinder getBinder() {
        return this;
    }

    public void setModelChangeCallback(ModelChangeCallback callback) {
        modelChangeCallback = callback;
    }
    // End of IBordeauxLearner Interface implemenation
}
