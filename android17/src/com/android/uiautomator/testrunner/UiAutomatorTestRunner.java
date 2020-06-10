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

package com.android.uiautomator.testrunner;

import android.app.Activity;
import android.app.IInstrumentationWatcher;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.test.RepetitiveTest;
import android.util.Log;

import com.android.uiautomator.core.UiDevice;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestListener;
import junit.framework.TestResult;
import junit.runner.BaseTestRunner;
import junit.textui.ResultPrinter;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
public class UiAutomatorTestRunner {

    private static final String LOGTAG = UiAutomatorTestRunner.class.getSimpleName();
    private static final int EXIT_OK = 0;
    private static final int EXIT_EXCEPTION = -1;

    private boolean mDebug;
    private Bundle mParams = null;
    private UiDevice mUiDevice;
    private List<String> mTestClasses = null;
    private FakeInstrumentationWatcher mWatcher = new FakeInstrumentationWatcher();
    private IAutomationSupport mAutomationSupport = new IAutomationSupport() {
        @Override
        public void sendStatus(int resultCode, Bundle status) {
            mWatcher.instrumentationStatus(null, resultCode, status);
        }
    };
    private List<TestListener> mTestListeners = new ArrayList<TestListener>();

    public void run(List<String> testClasses, Bundle params, boolean debug) {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                Log.e(LOGTAG, "uncaught exception", ex);
                Bundle results = new Bundle();
                results.putString("shortMsg", ex.getClass().getName());
                results.putString("longMsg", ex.getMessage());
                mWatcher.instrumentationFinished(null, 0, results);
                // bailing on uncaught exception
                System.exit(EXIT_EXCEPTION);
            }
        });

        mTestClasses = testClasses;
        mParams = params;
        mDebug = debug;
        start();
        System.exit(EXIT_OK);
    }

    /**
     * Called after all test classes are in place, ready to test
     */
    protected void start() {
        TestCaseCollector collector = getTestCaseCollector(this.getClass().getClassLoader());
        try {
            collector.addTestClasses(mTestClasses);
        } catch (ClassNotFoundException e) {
            // will be caught by uncaught handler
            throw new RuntimeException(e.getMessage(), e);
        }
        if (mDebug) {
            Debug.waitForDebugger();
        }
        mUiDevice = UiDevice.getInstance();
        List<TestCase> testCases = collector.getTestCases();
        Bundle testRunOutput = new Bundle();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream writer = new PrintStream(byteArrayOutputStream);
        try {
            StringResultPrinter resultPrinter = new StringResultPrinter(writer);

            TestResult testRunResult = new TestResult();
            // add test listeners
            testRunResult.addListener(new WatcherResultPrinter(testCases.size()));
            testRunResult.addListener(resultPrinter);
            // add all custom listeners
            for (TestListener listener : mTestListeners) {
                testRunResult.addListener(listener);
            }
            long startTime = System.currentTimeMillis();

            // run tests for realz!
            for (TestCase testCase : testCases) {
                prepareTestCase(testCase);
                testCase.run(testRunResult);
            }
            long runTime = System.currentTimeMillis() - startTime;

            resultPrinter.print2(testRunResult, runTime);
        } catch (Throwable t) {
            // catch all exceptions so a more verbose error message can be outputted
            writer.println(String.format("Test run aborted due to unexpected exception: %s",
                            t.getMessage()));
            t.printStackTrace(writer);
        } finally {
            testRunOutput.putString(Instrumentation.REPORT_KEY_STREAMRESULT,
                    String.format("\nTest results for %s=%s",
                    getClass().getSimpleName(),
                    byteArrayOutputStream.toString()));
            writer.close();
            mAutomationSupport.sendStatus(Activity.RESULT_OK, testRunOutput);
        }
    }

    // copy & pasted from com.android.commands.am.Am
    private class FakeInstrumentationWatcher implements IInstrumentationWatcher {

        private boolean mRawMode = true;

        @Override
        public IBinder asBinder() {
            throw new UnsupportedOperationException("I'm just a fake!");
        }

        @Override
        public void instrumentationStatus(ComponentName name, int resultCode, Bundle results) {
            synchronized (this) {
                // pretty printer mode?
                String pretty = null;
                if (!mRawMode && results != null) {
                    pretty = results.getString(Instrumentation.REPORT_KEY_STREAMRESULT);
                }
                if (pretty != null) {
                    System.out.print(pretty);
                } else {
                    if (results != null) {
                        for (String key : results.keySet()) {
                            System.out.println("INSTRUMENTATION_STATUS: " + key + "="
                                    + results.get(key));
                        }
                    }
                    System.out.println("INSTRUMENTATION_STATUS_CODE: " + resultCode);
                }
                notifyAll();
            }
        }

        @Override
        public void instrumentationFinished(ComponentName name, int resultCode, Bundle results) {
            synchronized (this) {
                // pretty printer mode?
                String pretty = null;
                if (!mRawMode && results != null) {
                    pretty = results.getString(Instrumentation.REPORT_KEY_STREAMRESULT);
                }
                if (pretty != null) {
                    System.out.println(pretty);
                } else {
                    if (results != null) {
                        for (String key : results.keySet()) {
                            System.out.println("INSTRUMENTATION_RESULT: " + key + "="
                                    + results.get(key));
                        }
                    }
                    System.out.println("INSTRUMENTATION_CODE: " + resultCode);
                }
                notifyAll();
            }
        }
    }

    // Copy & pasted from InstrumentationTestRunner.WatcherResultPrinter
    private class WatcherResultPrinter implements TestListener {

        private static final String REPORT_KEY_NUM_TOTAL = "numtests";
        private static final String REPORT_KEY_NAME_CLASS = "class";
        private static final String REPORT_KEY_NUM_CURRENT = "current";
        private static final String REPORT_KEY_NAME_TEST = "test";
        private static final String REPORT_KEY_NUM_ITERATIONS = "numiterations";
        private static final String REPORT_VALUE_ID = "UiAutomatorTestRunner";
        private static final String REPORT_KEY_STACK = "stack";

        private static final int REPORT_VALUE_RESULT_START = 1;
        private static final int REPORT_VALUE_RESULT_ERROR = -1;
        private static final int REPORT_VALUE_RESULT_FAILURE = -2;

        private final Bundle mResultTemplate;
        Bundle mTestResult;
        int mTestNum = 0;
        int mTestResultCode = 0;
        String mTestClass = null;

        public WatcherResultPrinter(int numTests) {
            mResultTemplate = new Bundle();
            mResultTemplate.putString(Instrumentation.REPORT_KEY_IDENTIFIER, REPORT_VALUE_ID);
            mResultTemplate.putInt(REPORT_KEY_NUM_TOTAL, numTests);
        }

        /**
         * send a status for the start of a each test, so long tests can be seen
         * as "running"
         */
        @Override
        public void startTest(Test test) {
            String testClass = test.getClass().getName();
            String testName = ((TestCase) test).getName();
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

            Method testMethod = null;
            try {
                testMethod = test.getClass().getMethod(testName);
                // Report total number of iterations, if test is repetitive
                if (testMethod.isAnnotationPresent(RepetitiveTest.class)) {
                    int numIterations = testMethod.getAnnotation(RepetitiveTest.class)
                            .numIterations();
                    mTestResult.putInt(REPORT_KEY_NUM_ITERATIONS, numIterations);
                }
            } catch (NoSuchMethodException e) {
                // ignore- the test with given name does not exist. Will be
                // handled during test
                // execution
            }

            mAutomationSupport.sendStatus(REPORT_VALUE_RESULT_START, mTestResult);
            mTestResultCode = 0;
        }

        @Override
        public void addError(Test test, Throwable t) {
            mTestResult.putString(REPORT_KEY_STACK, BaseTestRunner.getFilteredTrace(t));
            mTestResultCode = REPORT_VALUE_RESULT_ERROR;
            // pretty printing
            mTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT,
                String.format("\nError in %s:\n%s",
                    ((TestCase)test).getName(), BaseTestRunner.getFilteredTrace(t)));
        }

        @Override
        public void addFailure(Test test, AssertionFailedError t) {
            mTestResult.putString(REPORT_KEY_STACK, BaseTestRunner.getFilteredTrace(t));
            mTestResultCode = REPORT_VALUE_RESULT_FAILURE;
            // pretty printing
            mTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT,
                String.format("\nFailure in %s:\n%s",
                    ((TestCase)test).getName(), BaseTestRunner.getFilteredTrace(t)));
        }

        @Override
        public void endTest(Test test) {
            if (mTestResultCode == 0) {
                mTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT, ".");
            }
            mAutomationSupport.sendStatus(mTestResultCode, mTestResult);
        }

    }

    // copy pasted from InstrumentationTestRunner
    private class StringResultPrinter extends ResultPrinter {

        public StringResultPrinter(PrintStream writer) {
            super(writer);
        }

        synchronized void print2(TestResult result, long runTime) {
            printHeader(runTime);
            printFooter(result);
        }
    }

    protected TestCaseCollector getTestCaseCollector(ClassLoader classLoader) {
        return new TestCaseCollector(classLoader, new UiAutomatorTestCaseFilter());
    }

    protected void addTestListener(TestListener listener) {
        if (!mTestListeners.contains(listener)) {
            mTestListeners.add(listener);
        }
    }

    protected void removeTestListener(TestListener listener) {
        mTestListeners.remove(listener);
    }

    /**
     * subclass may override this method to perform further preparation
     *
     * @param testCase
     */
    protected void prepareTestCase(TestCase testCase) {
        ((UiAutomatorTestCase)testCase).setAutomationSupport(mAutomationSupport);
        ((UiAutomatorTestCase)testCase).setUiDevice(mUiDevice);
        ((UiAutomatorTestCase)testCase).setParams(mParams);
    }
}
