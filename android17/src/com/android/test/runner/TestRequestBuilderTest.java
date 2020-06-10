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
import android.test.suitebuilder.annotation.SmallTest;

import com.android.test.InjectInstrumentation;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Unit tests for {@link TestRequestBuilder}.
 */
public class TestRequestBuilderTest {

    public static class SampleTest {

        @SmallTest
        @Test
        public void testSmall() {
        }

        @Test
        public void testOther() {
        }
    }

    @SmallTest
    public static class SampleClassSize {

        @Test
        public void testSmall() {
        }

        @Test
        public void testSmallToo() {
        }
    }

    @InjectInstrumentation
    public Instrumentation mInstr;

    /**
     * Test initial condition for size filtering - that all tests run when no filter is attached
     */
    @Test
    public void testNoSize() {
        TestRequestBuilder b = new TestRequestBuilder(new PrintStream(new ByteArrayOutputStream()));
        b.addTestClass(SampleTest.class.getName());
        TestRequest request = b.build(mInstr);
        JUnitCore testRunner = new JUnitCore();
        Result result = testRunner.run(request.getRequest());
        Assert.assertEquals(2, result.getRunCount());
    }

    /**
     * Test that size annotation filtering works
     */
    @Test
    public void testSize() {
        TestRequestBuilder b = new TestRequestBuilder(new PrintStream(new ByteArrayOutputStream()));
        b.addTestClass(SampleTest.class.getName());
        b.addTestSizeFilter("small");
        TestRequest request = b.build(mInstr);
        JUnitCore testRunner = new JUnitCore();
        Result result = testRunner.run(request.getRequest());
        Assert.assertEquals(1, result.getRunCount());
    }

    /**
     * Test that size annotation filtering by class works
     */
    @Test
    public void testSize_class() {
        TestRequestBuilder b = new TestRequestBuilder(new PrintStream(new ByteArrayOutputStream()));
        b.addTestClass(SampleTest.class.getName());
        b.addTestClass(SampleClassSize.class.getName());
        b.addTestSizeFilter("small");
        TestRequest request = b.build(mInstr);
        JUnitCore testRunner = new JUnitCore();
        Result result = testRunner.run(request.getRequest());
        Assert.assertEquals(3, result.getRunCount());
    }

    /**
     * Test that annotation filtering by class works
     */
    @Test
    public void testAddAnnotationInclusionFilter() {
        TestRequestBuilder b = new TestRequestBuilder(new PrintStream(new ByteArrayOutputStream()));
        b.addAnnotationInclusionFilter(SmallTest.class.getName());
        b.addTestClass(SampleTest.class.getName());
        b.addTestClass(SampleClassSize.class.getName());
        TestRequest request = b.build(mInstr);
        JUnitCore testRunner = new JUnitCore();
        Result result = testRunner.run(request.getRequest());
        Assert.assertEquals(3, result.getRunCount());
    }

    /**
     * Test that annotation filtering by class works
     */
    @Test
    public void testAddAnnotationExclusionFilter() {
        TestRequestBuilder b = new TestRequestBuilder(new PrintStream(new ByteArrayOutputStream()));
        b.addAnnotationExclusionFilter(SmallTest.class.getName());
        b.addTestClass(SampleTest.class.getName());
        b.addTestClass(SampleClassSize.class.getName());
        TestRequest request = b.build(mInstr);
        JUnitCore testRunner = new JUnitCore();
        Result result = testRunner.run(request.getRequest());
        Assert.assertEquals(1, result.getRunCount());
    }
}
