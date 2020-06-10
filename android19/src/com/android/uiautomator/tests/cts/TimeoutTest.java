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

package com.android.uiautomator.tests.cts;

import com.android.uiautomator.core.UiDevice;
import com.android.uiautomator.core.UiObject;
import com.android.uiautomator.core.UiObjectNotFoundException;
import com.android.uiautomator.core.UiScrollable;
import com.android.uiautomator.core.UiSelector;
import com.android.uiautomator.testrunner.UiAutomatorTestCase;

import android.content.ComponentName;
import android.content.Intent;
import android.os.RemoteException;

public class TimeoutTest extends UiAutomatorTestCase {

    private static final String TEST_APP_PKG = "com.android.uiautomator.tests.cts.testapp";

    private static final Intent START_MAIN_ACTIVITY = new Intent(Intent.ACTION_MAIN)
            .setComponent(new ComponentName(TEST_APP_PKG, TEST_APP_PKG + ".MainActivity"))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

    private final UiObject mActionButton = new UiObject(new UiSelector().text("Go"));
    private final UiObject mDelayField = new UiObject(new UiSelector().description("delay"));

    private static final int DEFAULT_WAIT_FOR_WINDOW_TIMEOUT = 5500;
    private static final int SHORT_WAIT_FOR_WINDOW_TIMEOUT = 1000;
    private static final int LONG_WAIT_FOR_WINDOW_TIMEOUT = 30000;
    private static final int THRESHOLD = 500;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Launch the test app
        getInstrumentation().getContext().startActivity(START_MAIN_ACTIVITY);

        // Navigate to the Timeout Test
        UiScrollable listView = new UiScrollable(new UiSelector()
                .className(android.widget.ListView.class.getName()));
        UiObject testItem = listView.getChildByText(new UiSelector()
                .className(android.widget.TextView.class.getName()), "Timeout Test");
        testItem.click();
    }

    public void testClickAndWaitForNewWindowShortTimeoutNotReached()
            throws UiObjectNotFoundException {

        // Trigger the app to start a new activity just before the short timeout expires
        mDelayField.setText(Integer.toString(SHORT_WAIT_FOR_WINDOW_TIMEOUT - THRESHOLD));
        assertTrue("clickAndWaitForNewWindow() timed out too early",
                mActionButton.clickAndWaitForNewWindow(SHORT_WAIT_FOR_WINDOW_TIMEOUT));

        // Wait until the new activity launches
        mActionButton.waitUntilGone(2 * THRESHOLD);
        getUiDevice().pressBack();
    }

    public void testClickAndWaitForNewWindowShortTimeoutReached()
            throws UiObjectNotFoundException {

        // Trigger the app to start a new activity just after the short timeout expires
        mDelayField.setText(Integer.toString(SHORT_WAIT_FOR_WINDOW_TIMEOUT + THRESHOLD));
        assertFalse("clickAndWaitForNewWindow() did not timeout",
                mActionButton.clickAndWaitForNewWindow(SHORT_WAIT_FOR_WINDOW_TIMEOUT));

        // Wait until the new activity launches
        mActionButton.waitUntilGone(2 * THRESHOLD);
        getUiDevice().pressBack();
    }

    public void testClickAndWaitForNewWindowDefaultTimeoutNotReached()
            throws UiObjectNotFoundException {

        // Trigger the app to start a new activity just before the default timeout expires
        mDelayField.setText(Integer.toString(DEFAULT_WAIT_FOR_WINDOW_TIMEOUT - THRESHOLD));
        assertTrue("clickAndWaitForNewWindow() timed out too early",
                mActionButton.clickAndWaitForNewWindow());

        // Wait until the new activity launches
        mActionButton.waitUntilGone(2 * THRESHOLD);
        getUiDevice().pressBack();
    }

    public void testClickAndWaitForNewWindowDefaultTimeoutReached()
            throws UiObjectNotFoundException {

        // Trigger the app to start a new activity just after the default timeout expires
        mDelayField.setText(Integer.toString(DEFAULT_WAIT_FOR_WINDOW_TIMEOUT + THRESHOLD));
        assertFalse("clickAndWaitForNewWindow() did not timeout",
                mActionButton.clickAndWaitForNewWindow());

        // Wait until the new activity launches
        mActionButton.waitUntilGone(2 * THRESHOLD);
        getUiDevice().pressBack();
    }

    public void testClickAndWaitForNewWindowLongTimeoutNotReached()
            throws UiObjectNotFoundException {

        // Trigger the app to start a new activity just before the short timeout expires
        mDelayField.setText(Integer.toString(LONG_WAIT_FOR_WINDOW_TIMEOUT - THRESHOLD));
        assertTrue("clickAndWaitForNewWindow() timed out too early",
                mActionButton.clickAndWaitForNewWindow(LONG_WAIT_FOR_WINDOW_TIMEOUT));

        // Wait until the new activity launches
        mActionButton.waitUntilGone(2 * THRESHOLD);
        getUiDevice().pressBack();
    }

    public void testClickAndWaitForNewWindowLongTimeoutReached()
            throws UiObjectNotFoundException {

        // Trigger the app to start a new activity just after the short timeout expires
        mDelayField.setText(Integer.toString(LONG_WAIT_FOR_WINDOW_TIMEOUT + THRESHOLD));
        assertFalse("clickAndWaitForNewWindow() did not timeout",
                mActionButton.clickAndWaitForNewWindow(LONG_WAIT_FOR_WINDOW_TIMEOUT));

        // Wait until the new activity launches
        mActionButton.waitUntilGone(2 * THRESHOLD);
        getUiDevice().pressBack();
    }

    @Override
    protected void tearDown() {
        // Return to the homescreen after each test
        getUiDevice().pressHome();
    }
}
