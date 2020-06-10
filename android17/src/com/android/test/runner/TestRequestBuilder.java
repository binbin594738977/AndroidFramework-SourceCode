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

import android.app.Instrumentation;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Suppress;
import android.util.Log;

import com.android.test.runner.ClassPathScanner.ChainedClassNameFilter;
import com.android.test.runner.ClassPathScanner.ExcludePackageNameFilter;
import com.android.test.runner.ClassPathScanner.ExternalClassNameFilter;

import org.junit.runner.Computer;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runners.model.InitializationError;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Builds a {@link Request} from test classes in given apk paths, filtered on provided set of
 * restrictions.
 */
public class TestRequestBuilder {

    private static final String LOG_TAG = "TestRequestBuilder";

    private String[] mApkPaths;
    private TestLoader mTestLoader;
    private Filter mFilter = new AnnotationExclusionFilter(Suppress.class);
    private PrintStream mWriter;
    private boolean mSkipExecution = false;

    /**
     * Filter that only runs tests whose method or class has been annotated with given filter.
     */
    private static class AnnotationInclusionFilter extends Filter {

        private final Class<? extends Annotation> mAnnotationClass;

        AnnotationInclusionFilter(Class<? extends Annotation> annotation) {
            mAnnotationClass = annotation;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean shouldRun(Description description) {
            if (description.isTest()) {
                return description.getAnnotation(mAnnotationClass) != null ||
                        description.getTestClass().isAnnotationPresent(mAnnotationClass);
            } else {
                // don't filter out any test classes/suites, because their methods may have correct
                // annotation
                return true;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String describe() {
            return String.format("annotation %s", mAnnotationClass.getName());
        }
    }

    /**
     * Filter out tests whose method or class has been annotated with given filter.
     */
    private static class AnnotationExclusionFilter extends Filter {

        private final Class<? extends Annotation> mAnnotationClass;

        AnnotationExclusionFilter(Class<? extends Annotation> annotation) {
            mAnnotationClass = annotation;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean shouldRun(Description description) {
            if (description.getTestClass().isAnnotationPresent(mAnnotationClass) ||
                    description.getAnnotation(mAnnotationClass) != null) {
                return false;
            } else {
                return true;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String describe() {
            return String.format("not annotation %s", mAnnotationClass.getName());
        }
    }

    public TestRequestBuilder(PrintStream writer, String... apkPaths) {
        mApkPaths = apkPaths;
        mTestLoader = new TestLoader(writer);
    }

    /**
     * Add a test class to be executed. All test methods in this class will be executed.
     *
     * @param className
     */
    public void addTestClass(String className) {
        mTestLoader.loadClass(className);
    }

    /**
     * Adds a test method to run.
     * <p/>
     * Currently only supports one test method to be run.
     */
    public void addTestMethod(String testClassName, String testMethodName) {
        Class<?> clazz = mTestLoader.loadClass(testClassName);
        if (clazz != null) {
            mFilter = mFilter.intersect(Filter.matchMethodDescription(
                    Description.createTestDescription(clazz, testMethodName)));
        }
    }

    /**
     * Run only tests with given size
     * @param testSize
     */
    public void addTestSizeFilter(String testSize) {
        if ("small".equals(testSize)) {
            mFilter = mFilter.intersect(new AnnotationInclusionFilter(SmallTest.class));
        } else if ("medium".equals(testSize)) {
            mFilter = mFilter.intersect(new AnnotationInclusionFilter(MediumTest.class));
        } else if ("large".equals(testSize)) {
            mFilter = mFilter.intersect(new AnnotationInclusionFilter(LargeTest.class));
        } else {
            Log.e(LOG_TAG, String.format("Unrecognized test size '%s'", testSize));
        }
    }

    /**
     * Only run tests annotated with given annotation class.
     *
     * @param annotation the full class name of annotation
     */
    public void addAnnotationInclusionFilter(String annotation) {
        Class<? extends Annotation> annotationClass = loadAnnotationClass(annotation);
        if (annotationClass != null) {
            mFilter = mFilter.intersect(new AnnotationInclusionFilter(annotationClass));
        }
    }

    /**
     * Skip tests annotated with given annotation class.
     *
     * @param notAnnotation the full class name of annotation
     */
    public void addAnnotationExclusionFilter(String notAnnotation) {
        Class<? extends Annotation> annotationClass = loadAnnotationClass(notAnnotation);
        if (annotationClass != null) {
            mFilter = mFilter.intersect(new AnnotationExclusionFilter(annotationClass));
        }
    }

    /**
     * Build a request that will generate test started and test ended events, but will skip actual
     * test execution.
     */
    public void setSkipExecution(boolean b) {
        mSkipExecution = b;
    }

    /**
     * Builds the {@link TestRequest} based on current contents of added classes and methods.
     * <p/>
     * If no classes have been explicitly added, will scan the classpath for all tests.
     *
     */
    public TestRequest build(Instrumentation instr) {
        if (mTestLoader.isEmpty()) {
            // no class restrictions have been specified. Load all classes
            loadClassesFromClassPath();
        }

        Request request = classes(instr, mSkipExecution, new Computer(),
                mTestLoader.getLoadedClasses().toArray(new Class[0]));
        return new TestRequest(mTestLoader.getLoadFailures(), request.filterWith(mFilter));
    }

    /**
     * Create a <code>Request</code> that, when processed, will run all the tests
     * in a set of classes.
     *
     * @param instr the {@link Instrumentation} to inject into any tests that require it
     * @param computer Helps construct Runners from classes
     * @param classes the classes containing the tests
     * @return a <code>Request</code> that will cause all tests in the classes to be run
     */
    private static Request classes(Instrumentation instr, boolean skipExecution,
            Computer computer, Class<?>... classes) {
        try {
            AndroidRunnerBuilder builder = new AndroidRunnerBuilder(true, instr, skipExecution);
            Runner suite = computer.getSuite(builder, classes);
            return Request.runner(suite);
        } catch (InitializationError e) {
            throw new RuntimeException(
                    "Suite constructor, called as above, should always complete");
        }
    }

    private void loadClassesFromClassPath() {
        Collection<String> classNames = getClassNamesFromClassPath();
        for (String className : classNames) {
            mTestLoader.loadIfTest(className);
        }
    }

    private Collection<String> getClassNamesFromClassPath() {
        Log.i(LOG_TAG, String.format("Scanning classpath to find tests in apks %s",
                Arrays.toString(mApkPaths)));
        ClassPathScanner scanner = new ClassPathScanner(mApkPaths);
        try {
            // exclude inner classes, and classes from junit and this lib namespace
            return scanner.getClassPathEntries(new ChainedClassNameFilter(
                    new ExcludePackageNameFilter("junit"),
                    new ExcludePackageNameFilter("org.junit"),
                    new ExcludePackageNameFilter("org.hamcrest"),
                    new ExternalClassNameFilter(),
                    new ExcludePackageNameFilter("com.android.test.runner.junit3")));
        } catch (IOException e) {
            mWriter.println("failed to scan classes");
            Log.e(LOG_TAG, "Failed to scan classes", e);
        }
        return Collections.emptyList();
    }

    /**
     * Factory method for {@link ClassPathScanner}.
     * <p/>
     * Exposed so unit tests can mock.
     */
    ClassPathScanner createClassPathScanner(String... apkPaths) {
        return new ClassPathScanner(apkPaths);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Annotation> loadAnnotationClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (Class<? extends Annotation>)clazz;
        } catch (ClassNotFoundException e) {
            Log.e(LOG_TAG, String.format("Could not find annotation class: %s", className));
        } catch (ClassCastException e) {
            Log.e(LOG_TAG, String.format("Class %s is not an annotation", className));
        }
        return null;
    }
}
