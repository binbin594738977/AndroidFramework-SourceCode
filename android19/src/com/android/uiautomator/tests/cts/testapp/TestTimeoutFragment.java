/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.uiautomator.tests.cts.testapp;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

public class TestTimeoutFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View v = inflater.inflate(R.layout.test_timeout_fragment, container, false);

        Button goButton = (Button)v.findViewById(R.id.go_btn);
        goButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doAction(v);
            }
        });

        return v;
    }

    public void doAction(View v) {
        String delayText = ((EditText)getView().findViewById(R.id.delay)).getText().toString();
        final int delayValue = delayText.isEmpty() ? 0 : Integer.parseInt(delayText);

        new StartActivityDelayed(delayValue).execute();
    }

    private class StartActivityDelayed extends AsyncTask<Void, Void, Void> {
        private final long mDelay;

        public StartActivityDelayed(int delay) {
            mDelay = delay;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                Thread.sleep(mDelay);
            } catch (InterruptedException e) { }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            getActivity().startActivity(new Intent(getActivity(), LaunchedActivity.class));
        }
    }
}
