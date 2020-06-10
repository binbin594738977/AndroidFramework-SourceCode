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
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import android.bordeaux.services.AggregatorManager;
import android.bordeaux.services.Aggregator;
import java.io.Serializable;

public class FeatureAssembly {
    private static final String TAG = "FeatureAssembly";
    private List<String> mPossibleFeatures;
    private HashSet<String> mUseFeatures;
    private AggregatorManager mAggregatorManager;

    public FeatureAssembly() {
        mAggregatorManager = AggregatorManager.getInstance();
        mPossibleFeatures = Arrays.asList(mAggregatorManager.getListOfFeatures());
        mUseFeatures = new HashSet<String>();
    }

    public boolean registerFeature(String s) {
        boolean res = mPossibleFeatures.contains(s);
        if (res){
            if (!mUseFeatures.contains(s))
                mUseFeatures.add(s);
        }
        return res;
    }

    public Set<String> getUsedFeatures() {
        return (Set) mUseFeatures;
    }

    public String augmentFeatureInputString(String s) {
        String fs = s;
        Iterator itr = mUseFeatures.iterator();
        while(itr.hasNext()) {
            String f = (String) itr.next();
            fs = fs + "+" + mAggregatorManager.getDataMap(f).get(f);
        }
        return fs;
    }
}
