/*
 * Copyright (C) 2017 The Android Open Source Project
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

package libcore.util;

/**
 * Utility methods associated with finding updateable time zone data files.
 */
public final class TimeZoneDataFiles {

    private static final String ANDROID_ROOT_ENV = "ANDROID_ROOT";
    private static final String ANDROID_DATA_ENV = "ANDROID_DATA";

    private TimeZoneDataFiles() {}

    /**
     * Returns two time zone file paths for the specified file name in an array in the order they
     * should be tried. See {@link #generateIcuDataPath()} for ICU files instead.
     * <ul>
     * <li>[0] - the location of the file in the /data partition (may not exist).</li>
     * <li>[1] - the location of the file in the /system partition (should exist).</li>
     * </ul>
     */
    // VisibleForTesting
    public static String[] getTimeZoneFilePaths(String fileName) {
        return new String[] {
                getDataTimeZoneFile(fileName),
                getSystemTimeZoneFile(fileName)
        };
    }

    private static String getDataTimeZoneFile(String fileName) {
        return System.getenv(ANDROID_DATA_ENV) + "/misc/zoneinfo/current/" + fileName;
    }

    // VisibleForTesting
    public static String getSystemTimeZoneFile(String fileName) {
        return System.getenv(ANDROID_ROOT_ENV) + "/usr/share/zoneinfo/" + fileName;
    }

    public static String generateIcuDataPath() {
        StringBuilder icuDataPathBuilder = new StringBuilder();
        // ICU should first look in ANDROID_DATA. This is used for (optional) timezone data.
        String dataIcuDataPath = getEnvironmentPath(ANDROID_DATA_ENV, "/misc/zoneinfo/current/icu");
        if (dataIcuDataPath != null) {
            icuDataPathBuilder.append(dataIcuDataPath);
        }

        // ICU should always look in ANDROID_ROOT.
        String systemIcuDataPath = getEnvironmentPath(ANDROID_ROOT_ENV, "/usr/icu");
        if (systemIcuDataPath != null) {
            if (icuDataPathBuilder.length() > 0) {
                icuDataPathBuilder.append(":");
            }
            icuDataPathBuilder.append(systemIcuDataPath);
        }
        return icuDataPathBuilder.toString();
    }

    /**
     * Creates a path by combining the value of an environment variable with a relative path.
     * Returns {@code null} if the environment variable is not set.
     */
    private static String getEnvironmentPath(String environmentVariable, String path) {
        String variable = System.getenv(environmentVariable);
        if (variable == null) {
            return null;
        }
        return variable + path;
    }
}
