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

package com.android.commands.uiautomator;

import android.accessibilityservice.UiTestAutomationBridge;
import android.view.accessibility.AccessibilityEvent;

import com.android.commands.uiautomator.Launcher.Command;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Implementation of the events subcommand
 *
 * Prints out accessibility events until process is stopped.
 */
public class EventsCommand extends Command {

    private Object mQuitLock = new Object();

    public EventsCommand() {
        super("events");
    }

    @Override
    public String shortHelp() {
        return "prints out accessibility events until terminated";
    }

    @Override
    public String detailedOptions() {
        return null;
    }

    @Override
    public void run(String[] args) {
        final UiTestAutomationBridge bridge = new UiTestAutomationBridge() {
            @Override
            public void onAccessibilityEvent(AccessibilityEvent event) {
                SimpleDateFormat formatter = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
                System.out.println(String.format("%s %s",
                        formatter.format(new Date()), event.toString()));
            }
        };
        bridge.connect();
        // there's really no way to stop, essentially we just block indefinitely here and wait
        // for user to press Ctrl+C
        synchronized (mQuitLock) {
            try {
                mQuitLock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
