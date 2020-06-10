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
import android.os.Environment;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.commands.uiautomator.Launcher.Command;
import com.android.uiautomator.core.AccessibilityNodeInfoDumper;

import java.io.File;

/**
 * Implementation of the dump subcommand
 *
 * This creates an XML dump of current UI hierarchy
 */
public class DumpCommand extends Command {

    private static final File DEFAULT_DUMP_FILE = new File(
            Environment.getLegacyExternalStorageDirectory(), "window_dump.xml");

    public DumpCommand() {
        super("dump");
    }

    @Override
    public String shortHelp() {
        return "creates an XML dump of current UI hierarchy";
    }

    @Override
    public String detailedOptions() {
        return "    dump [file]\n"
            + "      [file]: the location where the dumped XML should be stored, default is\n      "
            + DEFAULT_DUMP_FILE.getAbsolutePath() + "\n";
    }

    @Override
    public void run(String[] args) {
        File dumpFile = DEFAULT_DUMP_FILE;
        if (args.length > 0) {
            dumpFile = new File(args[0]);
        }
        UiTestAutomationBridge bridge = new UiTestAutomationBridge();
        bridge.connect();
        // It appears that the bridge needs time to be ready. Making calls to the
        // bridge immediately after connecting seems to cause exceptions. So let's also
        // do a wait for idle in case the app is busy.
        bridge.waitForIdle(1000, 1000 * 10);
        AccessibilityNodeInfo info = bridge.getRootAccessibilityNodeInfoInActiveWindow();
        if (info == null) {
            System.err.println("ERROR: null root node returned by UiTestAutomationBridge.");
            return;
        }
        AccessibilityNodeInfoDumper.dumpWindowToFile(info, dumpFile);
        bridge.disconnect();
        System.out.println(
                String.format("UI hierchary dumped to: %s", dumpFile.getAbsolutePath()));
    }

}
