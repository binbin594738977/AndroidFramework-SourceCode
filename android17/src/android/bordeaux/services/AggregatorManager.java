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


import android.bordeaux.services.StringString;
import android.content.Context;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

class AggregatorManager extends IAggregatorManager.Stub  {
    private final String TAG = "AggregatorMnager";
    private static HashMap<String, Aggregator> sFeatureMap;
    private static AggregatorManager mManager = null;

    private AggregatorManager() {
        sFeatureMap = new HashMap<String, Aggregator>();
    }

    public static AggregatorManager getInstance() {
        if (mManager == null )
            mManager = new AggregatorManager();
        return mManager;
    }

    public String[] getListOfFeatures() {
        String[] s = new String[sFeatureMap.size()];
        int i = 0;
        for (Map.Entry<String, Aggregator> x : sFeatureMap.entrySet()) {
           s[i] = x.getKey();
           i++;
        }
        return s;
    }

    public void registerAggregator(Aggregator agg, AggregatorManager m) {
        agg.setManager(m);
        String[] fl = agg.getListOfFeatures();
        for ( int i  = 0; i< fl.length; i ++)
            sFeatureMap.put(fl[i], agg);
    }

    public ArrayList<StringString> getData(String dataName) {
        return getList(getDataMap(dataName));
    }

    public Map<String, String> getDataMap(String dataName) {
        if (sFeatureMap.get(dataName) != null)
            return sFeatureMap.get(dataName).getFeatureValue(dataName);
        else
            Log.e(TAG, "There is no feature called " + dataName);
        return null;
    }

    private ArrayList<StringString> getList(final Map<String, String> sample) {
        ArrayList<StringString> StringString_sample = new ArrayList<StringString>();
        for (Map.Entry<String, String> x : sample.entrySet()) {
           StringString v = new StringString();
           v.key = x.getKey();
           v.value = x.getValue();
           StringString_sample.add(v);
        }
        return StringString_sample;
    }
}
