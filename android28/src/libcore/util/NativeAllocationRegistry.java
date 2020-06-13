/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package libcore.util;

import dalvik.system.VMRuntime;
import sun.misc.Cleaner;

/**
 * A NativeAllocationRegistry is used to associate native allocations with
 * Java objects and register them with the runtime.
 * There are two primary benefits of registering native allocations associated
 * with Java objects:
 * <ol>
 *  <li>The runtime will account for the native allocations when scheduling
 *  garbage collection to run.</li>
 *  <li>The runtime will arrange for the native allocation to be automatically
 *  freed by a user-supplied function when the associated Java object becomes
 *  unreachable.</li>
 * </ol>
 * A separate NativeAllocationRegistry should be instantiated for each kind
 * of native allocation, where the kind of a native allocation consists of the
 * native function used to free the allocation and the estimated size of the
 * allocation. Once a NativeAllocationRegistry is instantiated, it can be
 * used to register any number of native allocations of that kind.
 * @hide
 */
public class NativeAllocationRegistry {

    private final ClassLoader classLoader;
    private final long freeFunction;
    private final long size;

    /**
     * Constructs a NativeAllocationRegistry for a particular kind of native
     * allocation.
     * The address of a native function that can be used to free this kind
     * native allocation should be provided using the
     * <code>freeFunction</code> argument. The native function should have the
     * type:
     * <pre>
     *    void f(void* nativePtr);
     * </pre>
     * <p>
     * The <code>classLoader</code> argument should be the class loader used
     * to load the native library that freeFunction belongs to. This is needed
     * to ensure the native library doesn't get unloaded before freeFunction
     * is called.
     * <p>
     * The <code>size</code> should be an estimate of the total number of
     * native bytes this kind of native allocation takes up. Different
     * NativeAllocationRegistrys must be used to register native allocations
     * with different estimated sizes, even if they use the same
     * <code>freeFunction</code>.
     * @param classLoader  ClassLoader that was used to load the native
     *                     library freeFunction belongs to.
     * @param freeFunction address of a native function used to free this
     *                     kind of native allocation
     * @param size         estimated size in bytes of this kind of native
     *                     allocation
     * @throws IllegalArgumentException If <code>size</code> is negative
     */
    public NativeAllocationRegistry(ClassLoader classLoader, long freeFunction, long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Invalid native allocation size: " + size);
        }

        this.classLoader = classLoader;
        this.freeFunction = freeFunction;
        this.size = size;
    }

    /**
     * Registers a new native allocation and associated Java object with the
     * runtime.
     * This NativeAllocationRegistry's <code>freeFunction</code> will
     * automatically be called with <code>nativePtr</code> as its sole
     * argument when <code>referent</code> becomes unreachable. If you
     * maintain copies of <code>nativePtr</code> outside
     * <code>referent</code>, you must not access these after
     * <code>referent</code> becomes unreachable, because they may be dangling
     * pointers.
     * <p>
     * The returned Runnable can be used to free the native allocation before
     * <code>referent</code> becomes unreachable. The runnable will have no
     * effect if the native allocation has already been freed by the runtime
     * or by using the runnable.
     * <p>
     * WARNING: This unconditionally takes ownership, i.e. deallocation
     * responsibility of nativePtr. nativePtr will be DEALLOCATED IMMEDIATELY
     * if the registration attempt throws an exception (other than one reporting
     * a programming error).
     *
     * @param referent      Non-null java object to associate the native allocation with
     * @param nativePtr     Non-zero address of the native allocation
     * @return runnable to explicitly free native allocation
     * @throws IllegalArgumentException if either referent or nativePtr is null.
     * @throws OutOfMemoryError  if there is not enough space on the Java heap
     *                           in which to register the allocation. In this
     *                           case, <code>freeFunction</code> will be
     *                           called with <code>nativePtr</code> as its
     *                           argument before the OutOfMemoryError is
     *                           thrown.
     */
    public Runnable registerNativeAllocation(Object referent, long nativePtr) {
        if (referent == null) {
            throw new IllegalArgumentException("referent is null");
        }
        if (nativePtr == 0) {
            throw new IllegalArgumentException("nativePtr is null");
        }

        CleanerThunk thunk;
        CleanerRunner result;
        try {
            thunk = new CleanerThunk();
            Cleaner cleaner = Cleaner.create(referent, thunk);
            result = new CleanerRunner(cleaner);
            registerNativeAllocation(this.size);
        } catch (VirtualMachineError vme /* probably OutOfMemoryError */) {
            applyFreeFunction(freeFunction, nativePtr);
            throw vme;
        } // Other exceptions are impossible.
        // Enable the cleaner only after we can no longer throw anything, including OOME.
        thunk.setNativePtr(nativePtr);
        return result;
    }

    /**
     * Interface for custom native allocation allocators used by
     * {@link #registerNativeAllocation(Object, Allocator) registerNativeAllocation(Object, Allocator)}.
     */
    public interface Allocator {
        /**
         * Allocate a native allocation and return its address.
         */
        long allocate();
    }

    /**
     * Registers and allocates a new native allocation and associated Java
     * object with the runtime.
     * This can be used for registering large allocations where the underlying
     * native allocation shouldn't be performed until it's clear there is
     * enough space on the Java heap to register the allocation.
     * <p>
     * If the allocator returns null, the allocation is not registered and a
     * null Runnable is returned.
     *
     * @param referent      Non-null java object to associate the native allocation with
     * @param allocator     used to perform the underlying native allocation.
     * @return runnable to explicitly free native allocation
     * @throws IllegalArgumentException if referent is null.
     * @throws OutOfMemoryError  if there is not enough space on the Java heap
     *                           in which to register the allocation. In this
     *                           case, the allocator will not be run.
     */
    public Runnable registerNativeAllocation(Object referent, Allocator allocator) {
        if (referent == null) {
            throw new IllegalArgumentException("referent is null");
        }

        // Create the cleaner before running the allocator so that
        // VMRuntime.registerNativeFree is eventually called if the allocate
        // method throws an exception.
        CleanerThunk thunk = new CleanerThunk();
        Cleaner cleaner = Cleaner.create(referent, thunk);
        CleanerRunner result = new CleanerRunner(cleaner);
        long nativePtr = allocator.allocate();
        if (nativePtr == 0) {
            cleaner.clean();
            return null;
        }
        registerNativeAllocation(this.size);
        thunk.setNativePtr(nativePtr);
        return result;
    }

    private class CleanerThunk implements Runnable {
        private long nativePtr;

        public CleanerThunk() {
            this.nativePtr = 0;
        }

        public void run() {
            if (nativePtr != 0) {
                applyFreeFunction(freeFunction, nativePtr);
                registerNativeFree(size);
            }
        }

        public void setNativePtr(long nativePtr) {
            this.nativePtr = nativePtr;
        }
    }

    private static class CleanerRunner implements Runnable {
        private final Cleaner cleaner;

        public CleanerRunner(Cleaner cleaner) {
            this.cleaner = cleaner;
        }

        public void run() {
            cleaner.clean();
        }
    }

    // TODO: Change the runtime to support passing the size as a long instead
    // of an int. For now, we clamp the size to fit.
    private static void registerNativeAllocation(long size) {
        VMRuntime.getRuntime().registerNativeAllocation((int)Math.min(size, Integer.MAX_VALUE));
    }

    private static void registerNativeFree(long size) {
        VMRuntime.getRuntime().registerNativeFree((int)Math.min(size, Integer.MAX_VALUE));
    }

    /**
     * Calls <code>freeFunction</code>(<code>nativePtr</code>).
     * Provided as a convenience in the case where you wish to manually free a
     * native allocation using a <code>freeFunction</code> without using a
     * NativeAllocationRegistry.
     */
    public static native void applyFreeFunction(long freeFunction, long nativePtr);
}

