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

package com.android.uiautomator.core;

import com.android.internal.util.Predicate;

import android.accessibilityservice.UiTestAutomationBridge;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class UiAutomatorBridge extends UiTestAutomationBridge {

    private static final String LOGTAG = UiAutomatorBridge.class.getSimpleName();

    // This value has the greatest bearing on the appearance of test execution speeds.
    // This value is used as the minimum time to wait before considering the UI idle after
    // each action.
    private static final long QUIET_TIME_TO_BE_CONSIDERD_IDLE_STATE = 500;//ms

    // This value is used to wait for the UI to go busy after an action. This has little
    // bearing on the appearance of test execution speeds. This value is used as a maximum
    // time to wait for busy state where it is possible to occur much sooner.
    private static final long WAIT_TIME_FROM_IDLE_TO_BUSY_STATE = 500;//ms

    // This is the maximum time the automation will wait for the UI to go idle. Execution
    // will resume normally anyway. This is to prevent waiting forever on display updates
    // that may be related to spinning wheels or progress updates of sorts etc...
    private static final long TOTAL_TIME_TO_WAIT_FOR_IDLE_STATE = 1000 * 10;//ms

    // Poll time used to check for last accessibility event time
    private static final long BUSY_STATE_POLL_TIME = 50; //ms

    private final CopyOnWriteArrayList<AccessibilityEventListener> mListeners =
            new CopyOnWriteArrayList<AccessibilityEventListener>();

    private final Object mLock = new Object();

    private final InteractionController mInteractionController;

    private final QueryController mQueryController;

    private long mLastEventTime = 0;
    private long mLastOperationTime = 0;

    private volatile boolean mWaitingForEventDelivery;

    public static final long TIMEOUT_ASYNC_PROCESSING = 5000;

    private final LinkedBlockingQueue<AccessibilityEvent> mEventQueue =
        new LinkedBlockingQueue<AccessibilityEvent>(10);

    public interface AccessibilityEventListener {
        public void onAccessibilityEvent(AccessibilityEvent event);
    }

    UiAutomatorBridge() {
        mInteractionController = new InteractionController(this);
        mQueryController = new QueryController(this);
        connect();
    }

    InteractionController getInteractionController() {
        return mInteractionController;
    }

    QueryController getQueryController() {
        return mQueryController;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        super.onAccessibilityEvent(event);
        Log.d(LOGTAG, event.toString());
        if (mWaitingForEventDelivery) {
            try {
                AccessibilityEvent clone = AccessibilityEvent.obtain(event);
                mEventQueue.offer(clone, TIMEOUT_ASYNC_PROCESSING, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                /* ignore */
            }
            if (!mWaitingForEventDelivery) {
                mEventQueue.clear();
            }
        }
        mLastEventTime = SystemClock.uptimeMillis();
        notifyListeners(event);
    }


    void addAccessibilityEventListener(AccessibilityEventListener listener) {
        mListeners.add(listener);
    }

    private void notifyListeners(AccessibilityEvent event) {
        for (AccessibilityEventListener listener : mListeners) {
            listener.onAccessibilityEvent(event);
        }
    }

    @Override
    public void waitForIdle(long idleTimeout, long globalTimeout) {
        long start = SystemClock.uptimeMillis();
        while ((SystemClock.uptimeMillis() - start) < WAIT_TIME_FROM_IDLE_TO_BUSY_STATE) {
            if (getLastOperationTime() > getLastEventTime()) {
                SystemClock.sleep(BUSY_STATE_POLL_TIME);
            } else {
                break;
            }
        }
        super.waitForIdle(idleTimeout, globalTimeout);
    }

    public void waitForIdle() {
        waitForIdle(TOTAL_TIME_TO_WAIT_FOR_IDLE_STATE);
    }

    public void waitForIdle(long timeout) {
        waitForIdle(QUIET_TIME_TO_BE_CONSIDERD_IDLE_STATE, timeout);
    }

    private long getLastEventTime() {
        synchronized (mLock) {
            return mLastEventTime;
        }
    }

    private long getLastOperationTime() {
        synchronized (mLock) {
            return mLastOperationTime;
        }
    }

    void setOperationTime() {
        synchronized (mLock) {
            mLastOperationTime = SystemClock.uptimeMillis();
        }
    }

    void updateEventTime() {
        synchronized (mLock) {
            mLastEventTime = SystemClock.uptimeMillis();
        }
    }

    public AccessibilityEvent executeCommandAndWaitForAccessibilityEvent(Runnable command,
            Predicate<AccessibilityEvent> predicate, long timeoutMillis)
            throws TimeoutException, Exception {
        // Prepare to wait for an event.
        mWaitingForEventDelivery = true;
        // Execute the command.
        command.run();
        // Wait for the event.
        final long startTimeMillis = SystemClock.uptimeMillis();
        while (true) {
            // Check if timed out and if not wait.
            final long elapsedTimeMillis = SystemClock.uptimeMillis() - startTimeMillis;
            final long remainingTimeMillis = timeoutMillis - elapsedTimeMillis;
            if (remainingTimeMillis <= 0) {
                mWaitingForEventDelivery = false;
                mEventQueue.clear();
                throw new TimeoutException("Expected event not received within: "
                        + timeoutMillis + " ms.");
            }
            AccessibilityEvent event = null;
            try {
                event = mEventQueue.poll(remainingTimeMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                /* ignore */
            }
            if (event != null) {
                if (predicate.apply(event)) {
                    mWaitingForEventDelivery = false;
                    mEventQueue.clear();
                    return event;
                } else {
                    event.recycle();
                }
            }
        }
    }
}
