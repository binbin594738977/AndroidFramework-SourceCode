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

package com.android.test.runner;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Debug;
import android.os.Looper;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import org.junit.internal.TextListener;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * An {@link Instrumentation} that runs JUnit3 and JUnit4 tests against
 * an Android package (application).
 * <p/>
 * Currently experimental. Based on {@link android.test.InstrumentationTestRunner}.
 * <p/>
 * Will eventually support a superset of {@link android.test.InstrumentationTestRunner} features,
 * while maintaining command/output format compatibility with that class.
 *
 * <h3>Typical Usage</h3>
 * <p/>
 * Write JUnit3 style {@link junit.framework.TestCase}s and/or JUnit4 style
 * {@link org.junit.Test}s that perform tests against the classes in your package.
 * Make use of the {@link com.android.test.InjectContext} and
 * {@link com.android.test.InjectInstrumentation} annotations if needed.
 * <p/>
 * In an appropriate AndroidManifest.xml, define an instrumentation with android:name set to
 * {@link com.android.test.runner.AndroidJUnitRunner} and the appropriate android:targetPackage set.
 * <p/>
 * Execution options:
 * <p/>
 * <b>Running all tests:</b> adb shell am instrument -w
 * com.android.foo/com.android.test.runner.AndroidJUnitRunner
 * <p/>
 * <b>Running all tests in a class:</b> adb shell am instrument -w
 * -e class com.android.foo.FooTest
 * com.android.foo/com.android.test.runner.AndroidJUnitRunner
 * <p/>
 * <b>Running a single test:</b> adb shell am instrument -w
 * -e class com.android.foo.FooTest#testFoo
 * com.android.foo/com.android.test.runner.AndroidJUnitRunner
 * <p/>
 * <b>Running all tests in multiple classes:</b> adb shell am instrument -w
 * -e class com.android.foo.FooTest,com.android.foo.TooTest
 * com.android.foo/com.android.test.runner.AndroidJUnitRunner
 * <p/>
 * <b>To debug your tests, set a break point in your code and pass:</b>
 * -e debug true
 * <p/>
 * <b>Running a specific test size i.e. annotated with
 * {@link android.test.suitebuilder.annotation.SmallTest} or
 * {@link android.test.suitebuilder.annotation.MediumTest} or
 * {@link android.test.suitebuilder.annotation.LargeTest}:</b>
 * adb shell am instrument -w -e size [small|medium|large]
 * com.android.foo/android.test.InstrumentationTestRunner
 * <p/>
 * <b>Filter test run to tests with given annotation:</b> adb shell am instrument -w
 * -e annotation com.android.foo.MyAnnotation
 * com.android.foo/android.test.InstrumentationTestRunner
 * <p/>
 * If used with other options, the resulting test run will contain the intersection of the two
 * options.
 * e.g. "-e size large -e annotation com.android.foo.MyAnnotation" will run only tests with both
 * the {@link LargeTest} and "com.android.foo.MyAnnotation" annotations.
 * <p/>
 * <b>Filter test run to tests <i>without</i> given annotation:</b> adb shell am instrument -w
 * -e notAnnotation com.android.foo.MyAnnotation
 * com.android.foo/android.test.InstrumentationTestRunner
 * <p/>
 * As above, if used with other options, the resulting test run will contain the intersection of
 * the two options.
 * e.g. "-e size large -e notAnnotation com.android.foo.MyAnnotation" will run tests with
 * the {@link LargeTest} annotation that do NOT have the "com.android.foo.MyAnnotation" annotations.
 * <p/>
 * <b>To run in 'log only' mode</b>
 * -e log true
 * This option will load and iterate through all test classes and methods, but will bypass actual
 * test execution. Useful for quickly obtaining info on the tests to be executed by an
 * instrumentation command.
 * <p/>
 */
public class AndroidJUnitRunner extends Instrumentation {

    public static final String ARGUMENT_TEST_CLASS = "class";

    private static final String ARGUMENT_TEST_SIZE = "size";
    private static final String ARGUMENT_LOG_ONLY = "log";
    private static final String ARGUMENT_ANNOTATION = "annotation";
    private static final String ARGUMENT_NOT_ANNOTATION = "notAnnotation";

    /**
     * The following keys are used in the status bundle to provide structured reports to
     * an IInstrumentationWatcher.
     */

    /**
     * This value, if stored with key {@link android.app.Instrumentation#REPORT_KEY_IDENTIFIER},
     * identifies InstrumentationTestRunner as the source of the report.  This is sent with all
     * status messages.
     */
    public static final String REPORT_VALUE_ID = "InstrumentationTestRunner";
    /**
     * If included in the status or final bundle sent to an IInstrumentationWatcher, this key
     * identifies the total number of tests that are being run.  This is sent with all status
     * messages.
     */
    public static final String REPORT_KEY_NUM_TOTAL = "numtests";
    /**
     * If included in the status or final bundle sent to an IInstrumentationWatcher, this key
     * identifies the sequence number of the current test.  This is sent with any status message
     * describing a specific test being started or completed.
     */
    public static final String REPORT_KEY_NUM_CURRENT = "current";
    /**
     * If included in the status or final bundle sent to an IInstrumentationWatcher, this key
     * identifies the name of the current test class.  This is sent with any status message
     * describing a specific test being started or completed.
     */
    public static final String REPORT_KEY_NAME_CLASS = "class";
    /**
     * If included in the status or final bundle sent to an IInstrumentationWatcher, this key
     * identifies the name of the current test.  This is sent with any status message
     * describing a specific test being started or completed.
     */
    public static final String REPORT_KEY_NAME_TEST = "test";

    /**
     * The test is starting.
     */
    public static final int REPORT_VALUE_RESULT_START = 1;
    /**
     * The test completed successfully.
     */
    public static final int REPORT_VALUE_RESULT_OK = 0;
    /**
     * The test completed with an error.
     */
    public static final int REPORT_VALUE_RESULT_ERROR = -1;
    /**
     * The test completed with a failure.
     */
    public static final int REPORT_VALUE_RESULT_FAILURE = -2;
    /**
     * The test was ignored.
     */
    public static final int REPORT_VALUE_RESULT_IGNORED = -3;
    /**
     * If included in the status bundle sent to an IInstrumentationWatcher, this key
     * identifies a stack trace describing an error or failure.  This is sent with any status
     * message describing a specific test being completed.
     */
    public static final String REPORT_KEY_STACK = "stack";

    private static final String LOG_TAG = "InstrumentationTestRunner";

    private final Bundle mResults = new Bundle();
    private Bundle mArguments;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mArguments = arguments;

        start();
    }

    /**
     * Get the Bundle object that contains the arguments passed to the instrumentation
     *
     * @return the Bundle object
     * @hide
     */
    public Bundle getArguments(){
        return mArguments;
    }

    private boolean getBooleanArgument(Bundle arguments, String tag) {
        String tagString = arguments.getString(tag);
        return tagString != null && Boolean.parseBoolean(tagString);
    }

    /**
     * Initialize the current thread as a looper.
     * <p/>
     * Exposed for unit testing.
     */
    void prepareLooper() {
        Looper.prepare();
    }

    @Override
    public void onStart() {
        prepareLooper();

        if (getBooleanArgument(getArguments(), "debug")) {
            Debug.waitForDebugger();
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream writer = new PrintStream(byteArrayOutputStream);
        try {
            JUnitCore testRunner = new JUnitCore();
            testRunner.addListener(new TextListener(writer));
            WatcherResultPrinter detailedResultPrinter = new WatcherResultPrinter();
            testRunner.addListener(detailedResultPrinter);

            TestRequest testRequest = buildRequest(getArguments(), writer);
            Result result = testRunner.run(testRequest.getRequest());
            result.getFailures().addAll(testRequest.getFailures());
            Log.i(LOG_TAG, String.format("Test run complete. %d tests, %d failed, %d ignored",
                    result.getRunCount(), result.getFailureCount(), result.getIgnoreCount()));
        } catch (Throwable t) {
            // catch all exceptions so a more verbose error message can be displayed
            writer.println(String.format(
                    "Test run aborted due to unexpected exception: %s",
                    t.getMessage()));
            t.printStackTrace(writer);

        } finally {
            writer.close();
            mResults.putString(Instrumentation.REPORT_KEY_STREAMRESULT,
                    String.format("\n%s",
                            byteArrayOutputStream.toString()));
            finish(Activity.RESULT_OK, mResults);
        }

    }

    /**
     * Builds a {@link TestRequest} based on given input arguments.
     * <p/>
     * Exposed for unit testing.
     */
    TestRequest buildRequest(Bundle arguments, PrintStream writer) {
        // only load tests for current aka testContext
        // Note that this represents a change from InstrumentationTestRunner where
        // getTargetContext().getPackageCodePath() was also scanned
        TestRequestBuilder builder = createTestRequestBuilder(writer,
                getContext().getPackageCodePath());

        String testClassName = arguments.getString(ARGUMENT_TEST_CLASS);
        if (testClassName != null) {
            for (String className : testClassName.split(",")) {
                parseTestClass(className, builder);
            }
        }

        String testSize = arguments.getString(ARGUMENT_TEST_SIZE);
        if (testSize != null) {
            builder.addTestSizeFilter(testSize);
        }

        String annotation = arguments.getString(ARGUMENT_ANNOTATION);
        if (annotation != null) {
            builder.addAnnotationInclusionFilter(annotation);
        }

        String notAnnotation = arguments.getString(ARGUMENT_NOT_ANNOTATION);
        if (notAnnotation != null) {
            builder.addAnnotationExclusionFilter(notAnnotation);
        }

        boolean logOnly = getBooleanArgument(arguments, ARGUMENT_LOG_ONLY);
        if (logOnly) {
            builder.setSkipExecution(true);
        }
        return builder.build(this);
    }

    /**
     * Factory method for {@link TestRequestBuilder}.
     * <p/>
     * Exposed for unit testing.
     */
    TestRequestBuilder createTestRequestBuilder(PrintStream writer, String... packageCodePaths) {
        return new TestRequestBuilder(writer, packageCodePaths);
    }

    /**
     * Parse and load the given test class and, optionally, method
     *
     * @param testClassName - full package name of test class and optionally method to add.
     *        Expected format: com.android.TestClass#testMethod
     * @param testSuiteBuilder - builder to add tests to
     */
    private void parseTestClass(String testClassName, TestRequestBuilder testRequestBuilder) {
        int methodSeparatorIndex = testClassName.indexOf('#');

        if (methodSeparatorIndex > 0) {
            String testMethodName = testClassName.substring(methodSeparatorIndex + 1);
            testClassName = testClassName.substring(0, methodSeparatorIndex);
            testRequestBuilder.addTestMethod(testClassName, testMethodName);
        } else {
            testRequestBuilder.addTestClass(testClassName);
        }
    }

    /**
     * This class sends status reports back to the IInstrumentationWatcher
     */
    private class WatcherResultPrinter extends RunListener {
        private final Bundle mResultTemplate;
        Bundle mTestResult;
        int mTestNum = 0;
        int mTestResultCode = 0;
        String mTestClass = null;

        public WatcherResultPrinter() {
            mResultTemplate = new Bundle();
        }

        @Override
        public void testRunStarted(Description description) throws Exception {
            mResultTemplate.putString(Instrumentation.REPORT_KEY_IDENTIFIER, REPORT_VALUE_ID);
            mResultTemplate.putInt(REPORT_KEY_NUM_TOTAL, description.testCount());
        }

        @Override
        public void testRunFinished(Result result) throws Exception {
            // TODO: implement this
        }

        /**
         * send a status for the start of a each test, so long tests can be seen
         * as "running"
         */
        @Override
        public void testStarted(Description description) throws Exception {
            String testClass = description.getClassName();
            String testName = description.getMethodName();
            mTestResult = new Bundle(mResultTemplate);
            mTestResult.putString(REPORT_KEY_NAME_CLASS, testClass);
            mTestResult.putString(REPORT_KEY_NAME_TEST, testName);
            mTestResult.putInt(REPORT_KEY_NUM_CURRENT, ++mTestNum);
            // pretty printing
            if (testClass != null && !testClass.equals(mTestClass)) {
                mTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT,
                        String.format("\n%s:", testClass));
                mTestClass = testClass;
            } else {
                mTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT, "");
            }

            sendStatus(REPORT_VALUE_RESULT_START, mTestResult);
            mTestResultCode = 0;
        }

        @Override
        public void testFinished(Description description) throws Exception {
            if (mTestResultCode == 0) {
                mTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT, ".");
            }
            sendStatus(mTestResultCode, mTestResult);
        }

        @Override
        public void testFailure(Failure failure) throws Exception {
            mTestResultCode = REPORT_VALUE_RESULT_ERROR;
            reportFailure(failure);
        }


        @Override
        public void testAssumptionFailure(Failure failure) {
            mTestResultCode = REPORT_VALUE_RESULT_FAILURE;
            reportFailure(failure);
        }

        private void reportFailure(Failure failure) {
            mTestResult.putString(REPORT_KEY_STACK, failure.getTrace());
            // pretty printing
            mTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT,
                    String.format("\nError in %s:\n%s",
                            failure.getDescription().getDisplayName(), failure.getTrace()));
        }

        @Override
        public void testIgnored(Description description) throws Exception {
            testStarted(description);
            mTestResultCode = REPORT_VALUE_RESULT_IGNORED;
            testFinished(description);
        }
    }
}
