/*
 * Copyright (C) 2011 The Android Open Source Project
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


package android.bordeaux.learning;

import java.util.HashMap;
import java.util.Map;
import android.util.Log;

/**
 * A simple impelentation of histograms with sparse enteries using HashMap.
 * User can push examples or extract probabilites from this histogram.
 */
public class predictorHist {
    private HashMap<String, Integer> mCountHist;
    private int mSampleCount;
    String TAG = "PredicrtHist";

    public predictorHist() {
        mCountHist = new HashMap<String, Integer>();
        mSampleCount = 0;
    }

    // reset histogram
    public void ResetPredictorHist() {
        mCountHist.clear();
        mSampleCount = 0;
    }

    // getters
    public final HashMap<String, Integer> getHist() {
        return mCountHist;
    }

    public int getHistCounts() {
        return mSampleCount;
    }

    //setter
    public void set(HashMap<String, Integer> hist) {
        ResetPredictorHist();
        for (Map.Entry<String, Integer> x : hist.entrySet()) {
            mCountHist.put(x.getKey(), x.getValue());
            mSampleCount = mSampleCount + x.getValue();
        }
    }

    /**
     * pushes a new example to the histogram
     */
    public void pushSample( String fs) {
        int histValue = 1;
        if (mCountHist.get(fs) != null )
            histValue = histValue + mCountHist.get(fs);
        mCountHist.put(fs,histValue);
        mSampleCount++;
    }

    /**
     * return probabilty of an exmple using the histogram
     */
    public float getProbability(String fs) {
        float res = 0;
        if (mCountHist.get(fs) != null )
            res = ((float) mCountHist.get(fs)) / ((float)mSampleCount);
        return res;
    }
}
