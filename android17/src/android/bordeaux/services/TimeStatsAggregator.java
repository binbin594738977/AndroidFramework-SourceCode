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

import java.util.Date;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;

class TimeStatsAggregator extends Aggregator {
    final String TAG = "TimeStatsAggregator";
    public static final String CURRENT_TIME = "Current Time";
    final String EARLY_MORNING = "EarlyMorning";
    final String MORNING = "Morning";
    final String NOON = "Noon";
    final String AFTERNOON = "AfterNoon";
    final String NIGHT = "Night";
    final String LATE_NIGHT = "LateNight";

    public String[] getListOfFeatures(){
        String [] list = new String[1];
        list[0] = CURRENT_TIME;
        return list;
    }

    public Map<String,String> getFeatureValue(String featureName) {
        HashMap<String,String> m = new HashMap<String,String>();
        if (featureName.equals(CURRENT_TIME))
            m.put(CURRENT_TIME, getCurrentTimeLabel());
        else
            Log.e(TAG, "There is no Time feature called " + featureName);
        return (Map) m;
    }

    private String getCurrentTimeLabel(){
        Date  d = new Date(System.currentTimeMillis());
        String t = "";  //TODO maybe learn thresholds
        int h = d.getHours();
        if ((h > 5) & (h <= 7) )
            t = EARLY_MORNING;
        else if ((h > 7) & (h <= 11) )
            t = MORNING;
        else if ((h > 11) & (h <= 15))
            t = NOON;
        else if ((h > 15) & (h <= 20))
            t = AFTERNOON;
        else if ((h > 20) & (h <= 24))
            t = NIGHT;
        else if ((h > 0) & (h <= 5))
            t = LATE_NIGHT;
        return t;
    }
}
