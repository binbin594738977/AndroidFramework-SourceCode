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

import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * UiScrollable is a {@link UiCollection} and provides support for searching for items in a
 * scrollable UI elements. Used with horizontally or vertically scrollable UI.
 */
public class UiScrollable extends UiCollection {
    private static final String LOG_TAG = UiScrollable.class.getSimpleName();

    // More steps slows the swipe and prevents contents from being flung too far
    private static final int SCROLL_STEPS = 55;

    private static final int FLING_STEPS = 5;

    // Restrict a swipe's starting and ending points inside a 10% margin of the target
    private static final double DEFAULT_SWIPE_DEADZONE_PCT = 0.1;

    // Limits the number of swipes/scrolls performed during a search
    private static int mMaxSearchSwipes = 30;

    // Used in ScrollForward() and ScrollBackward() to determine swipe direction
    private boolean mIsVerticalList = true;

    private double mSwipeDeadZonePercentage = DEFAULT_SWIPE_DEADZONE_PCT;

    /**
     * UiScrollable is a {@link UiCollection} and as such requires a {@link UiSelector} to
     * identify the container UI element of the scrollable collection. Further operations on
     * the items in the container will require specifying UiSelector as an item selector.
     *
     * @param container a {@link UiSelector} selector
     */
    public UiScrollable(UiSelector container) {
        // wrap the container selector with container so that QueryController can handle
        // this type of enumeration search accordingly
        super(container);
    }

    /**
     * Set the direction of swipes when performing scroll search
     * @return reference to itself
     */
    public UiScrollable setAsVerticalList() {
        mIsVerticalList = true;
        return this;
    }

    /**
     * Set the direction of swipes when performing scroll search
     * @return reference to itself
     */
    public UiScrollable setAsHorizontalList() {
        mIsVerticalList = false;
        return this;
    }

    /**
     * Used privately when performing swipe searches to decide if an element has become
     * visible or not.
     *
     * @param selector
     * @return true if found else false
     */
    protected boolean exists(UiSelector selector) {
        if(getQueryController().findAccessibilityNodeInfo(selector) != null) {
            return true;
        }
        return false;
    }

    /**
     * Searches for child UI element within the constraints of this UiScrollable {@link UiSelector}
     * container. It looks for any child matching the <code>childPattern</code> argument within its
     * hierarchy with a matching content-description text. The returned UiObject will represent the
     * UI element matching the <code>childPattern</code> and not the sub element that matched the
     * content description.</p>
     * By default this operation will perform scroll search while attempting to find the UI element
     * See {@link #getChildByDescription(UiSelector, String, boolean)}
     *
     * @param childPattern {@link UiSelector} selector of the child pattern to match and return
     * @param text String of the identifying child contents of of the <code>childPattern</code>
     * @return {@link UiObject} pointing at and instance of <code>childPattern</code>
     * @throws UiObjectNotFoundException
     */
    @Override
    public UiObject getChildByDescription(UiSelector childPattern, String text)
            throws UiObjectNotFoundException {
        return getChildByDescription(childPattern, text, true);
    }

    /**
     * See {@link #getChildByDescription(UiSelector, String)}
     *
     * @param childPattern {@link UiSelector} selector of the child pattern to match and return
     * @param text String may be a partial match for the content-description of a child element.
     * @param allowScrollSearch set to true if scrolling is allowed
     * @return {@link UiObject} pointing at and instance of <code>childPattern</code>
     * @throws UiObjectNotFoundException
     */
    public UiObject getChildByDescription(UiSelector childPattern, String text,
            boolean allowScrollSearch) throws UiObjectNotFoundException {
        if (text != null) {
            if (allowScrollSearch) {
                scrollIntoView(new UiSelector().descriptionContains(text));
            }
            return super.getChildByDescription(childPattern, text);
        }
        throw new UiObjectNotFoundException("for description= \"" + text + "\"");
    }

    /**
     * Searches for child UI element within the constraints of this UiScrollable {@link UiSelector}
     * selector. It looks for any child matching the <code>childPattern</code> argument and
     * return the <code>instance</code> specified. The operation is performed only on the visible
     * items and no scrolling is performed in this case.
     *
     * @param childPattern {@link UiSelector} selector of the child pattern to match and return
     * @param instance int the desired matched instance of this <code>childPattern</code>
     * @return {@link UiObject} pointing at and instance of <code>childPattern</code>
     */
    @Override
    public UiObject getChildByInstance(UiSelector childPattern, int instance)
            throws UiObjectNotFoundException {
        UiSelector patternSelector = UiSelector.patternBuilder(getSelector(),
                UiSelector.patternBuilder(childPattern).instance(instance));
        return new UiObject(patternSelector);
    }

    /**
     * Searches for child UI element within the constraints of this UiScrollable {@link UiSelector}
     * container. It looks for any child matching the <code>childPattern</code> argument that has
     * a sub UI element anywhere within its sub hierarchy that has text attribute
     * <code>text</code>. The returned UiObject will point at the <code>childPattern</code>
     * instance that matched the search and not at the text matched sub element</p>
     * By default this operation will perform scroll search while attempting to find the UI
     * element.
     * See {@link #getChildByText(UiSelector, String, boolean)}
     *
     * @param childPattern {@link UiSelector} selector of the child pattern to match and return
     * @param text String of the identifying child contents of of the <code>childPattern</code>
     * @return {@link UiObject} pointing at and instance of <code>childPattern</code>
     * @throws UiObjectNotFoundException
     */
    @Override
    public UiObject getChildByText(UiSelector childPattern, String text)
            throws UiObjectNotFoundException {
        return getChildByText(childPattern, text, true);
    }

    /**
     * See {@link #getChildByText(UiSelector, String)}
     *
     * @param childPattern {@link UiSelector} selector of the child pattern to match and return
     * @param text String of the identifying child contents of of the <code>childPattern</code>
     * @param allowScrollSearch set to true if scrolling is allowed
     * @return {@link UiObject} pointing at and instance of <code>childPattern</code>
     * @throws UiObjectNotFoundException
     */
    public UiObject getChildByText(UiSelector childPattern, String text, boolean allowScrollSearch)
            throws UiObjectNotFoundException {

        if (text != null) {
            if (allowScrollSearch) {
                scrollIntoView(new UiSelector().text(text));
            }
            return super.getChildByText(childPattern, text);
        }
        throw new UiObjectNotFoundException("for text= \"" + text + "\"");
    }

    /**
     * Performs a swipe Up on the UI element until the requested content-description
     * is visible or until swipe attempts have been exhausted. See {@link #setMaxSearchSwipes(int)}
     *
     * @param text to look for anywhere within the contents of this scrollable.
     * @return true if item us found else false
     */
    public boolean scrollDescriptionIntoView(String text) throws UiObjectNotFoundException {
        return scrollIntoView(new UiSelector().description(text));
    }

    /**
     * Perform a scroll search for a UI element matching the {@link UiSelector} selector argument.
     * See {@link #scrollDescriptionIntoView(String)} and {@link #scrollTextIntoView(String)}.
     *
     * @param obj {@link UiObject}
     * @return true if the item was found and now is in view else false
     */
    public boolean scrollIntoView(UiObject obj) throws UiObjectNotFoundException {
        return scrollIntoView(obj.getSelector());
    }

    /**
     * Perform a scroll search for a UI element matching the {@link UiSelector} selector argument.
     * See {@link #scrollDescriptionIntoView(String)} and {@link #scrollTextIntoView(String)}.
     *
     * @param selector {@link UiSelector} selector
     * @return true if the item was found and now is in view else false
     */
    public boolean scrollIntoView(UiSelector selector) throws UiObjectNotFoundException {
        // if we happen to be on top of the text we want then return here
        if (exists(getSelector().childSelector(selector))) {
            return (true);
        } else {
            // we will need to reset the search from the beginning to start search
            scrollToBeginning(mMaxSearchSwipes);
            if (exists(getSelector().childSelector(selector))) {
                return (true);
            }
            for (int x = 0; x < mMaxSearchSwipes; x++) {
                if(!scrollForward()) {
                    return false;
                }

                if(exists(getSelector().childSelector(selector))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Performs a swipe up on the UI element until the requested text is visible
     * or until swipe attempts have been exhausted. See {@link #setMaxSearchSwipes(int)}
     *
     * @param text to look for
     * @return true if item us found else false
     */
    public boolean scrollTextIntoView(String text) throws UiObjectNotFoundException {
        return scrollIntoView(new UiSelector().text(text));
    }

    /**
     * {@link #getChildByDescription(String, boolean)} and {@link #getChildByText(String, boolean)}
     * use an arguments that specifies if scrolling is allowed while searching for the UI element.
     * The number of scrolls allowed to perform a search can be modified by this method.
     * The current value can be read by calling {@link #getMaxSearchSwipes()}
     *
     * @param swipes is the number of search swipes until abort
     * @return reference to itself
     */
    public UiScrollable setMaxSearchSwipes(int swipes) {
        mMaxSearchSwipes = swipes;
        return this;
    }

    /**
     * {@link #getChildByDescription(String, boolean)} and {@link #getChildByText(String, boolean)}
     * use an arguments that specifies if scrolling is allowed while searching for the UI element.
     * The number of scrolls currently allowed to perform a search can be read by this method.
     * See {@link #setMaxSearchSwipes(int)}
     *
     * @return max value of the number of swipes currently allowed during a scroll search
     */
    public int getMaxSearchSwipes() {
        return mMaxSearchSwipes;
    }

    /**
     * A convenience version of {@link UiScrollable#scrollForward(int)}, performs a fling
     *
     * @return true if scrolled and false if can't scroll anymore
     */
    public boolean flingForward() throws UiObjectNotFoundException {
        return scrollForward(FLING_STEPS);
    }

    /**
     * A convenience version of {@link UiScrollable#scrollForward(int)}, performs a regular scroll
     *
     * @return true if scrolled and false if can't scroll anymore
     */
    public boolean scrollForward() throws UiObjectNotFoundException {
        return scrollForward(SCROLL_STEPS);
    }

    /**
     * Perform a scroll forward. If this list is set to vertical (see {@link #setAsVerticalList()}
     * default) then the swipes will be executed from the bottom to top. If this list is set
     * to horizontal (see {@link #setAsHorizontalList()}) then the swipes will be executed from
     * the right to left. Caution is required on devices configured with right to left languages
     * like Arabic and Hebrew.
     *
     * @param steps use steps to control the speed, so that it may be a scroll, or fling
     * @return true if scrolled and false if can't scroll anymore
     */
    public boolean scrollForward(int steps) throws UiObjectNotFoundException {
        Log.d(LOG_TAG, "scrollForward() on selector = " + getSelector());
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        Rect rect = new Rect();;
        node.getBoundsInScreen(rect);

        int downX = 0;
        int downY = 0;
        int upX = 0;
        int upY = 0;

        // scrolling is by default assumed vertically unless the object is explicitly
        // set otherwise by setAsHorizontalContainer()
        if(mIsVerticalList) {
            int swipeAreaAdjust = (int)(rect.height() * getSwipeDeadZonePercentage());
            // scroll vertically: swipe down -> up
            downX = rect.centerX();
            downY = rect.bottom - swipeAreaAdjust;
            upX = rect.centerX();
            upY = rect.top + swipeAreaAdjust;
        } else {
            int swipeAreaAdjust = (int)(rect.width() * getSwipeDeadZonePercentage());
            // scroll horizontally: swipe right -> left
            // TODO: Assuming device is not in right to left language
            downX = rect.right - swipeAreaAdjust;
            downY = rect.centerY();
            upX = rect.left + swipeAreaAdjust;
            upY = rect.centerY();
        }
        return getInteractionController().scrollSwipe(downX, downY, upX, upY, steps);
    }

    /**
     * See {@link UiScrollable#scrollBackward(int)}
     *
     * @return true if scrolled and false if can't scroll anymore
     */
    public boolean flingBackward() throws UiObjectNotFoundException {
        return scrollBackward(FLING_STEPS);
    }

    /**
     * See {@link UiScrollable#scrollBackward(int)}
     *
     * @return true if scrolled and false if can't scroll anymore
     */
    public boolean scrollBackward() throws UiObjectNotFoundException {
        return scrollBackward(SCROLL_STEPS);
    }

    /**
     * Perform a scroll backward. If this list is set to vertical (see {@link #setAsVerticalList()}
     * default) then the swipes will be executed from the top to bottom. If this list is set
     * to horizontal (see {@link #setAsHorizontalList()}) then the swipes will be executed from
     * the left to right. Caution is required on devices configured with right to left languages
     * like Arabic and Hebrew.
     *
     * @param steps use steps to control the speed, so that it may be a scroll, or fling
     * @return true if scrolled and false if can't scroll anymore
     */
    public boolean scrollBackward(int steps) throws UiObjectNotFoundException {
        Log.d(LOG_TAG, "scrollBackward() on selector = " + getSelector());
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if (node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        Rect rect = new Rect();;
        node.getBoundsInScreen(rect);

        int downX = 0;
        int downY = 0;
        int upX = 0;
        int upY = 0;

        // scrolling is by default assumed vertically unless the object is explicitly
        // set otherwise by setAsHorizontalContainer()
        if(mIsVerticalList) {
            int swipeAreaAdjust = (int)(rect.height() * getSwipeDeadZonePercentage());
            Log.d(LOG_TAG, "scrollToBegining() using vertical scroll");
            // scroll vertically: swipe up -> down
            downX = rect.centerX();
            downY = rect.top + swipeAreaAdjust;
            upX = rect.centerX();
            upY = rect.bottom - swipeAreaAdjust;
        } else {
            int swipeAreaAdjust = (int)(rect.width() * getSwipeDeadZonePercentage());
            Log.d(LOG_TAG, "scrollToBegining() using hotizontal scroll");
            // scroll horizontally: swipe left -> right
            // TODO: Assuming device is not in right to left language
            downX = rect.left + swipeAreaAdjust;
            downY = rect.centerY();
            upX = rect.right - swipeAreaAdjust;
            upY = rect.centerY();
        }
        return getInteractionController().scrollSwipe(downX, downY, upX, upY, steps);
    }

    /**
     * Scrolls to the beginning of a scrollable UI element. The beginning could be the top most
     * in case of vertical lists or the left most in case of horizontal lists. Caution is required
     * on devices configured with right to left languages like Arabic and Hebrew.
     *
     * @param steps use steps to control the speed, so that it may be a scroll, or fling
     * @return true on scrolled else false
     */
    public boolean scrollToBeginning(int maxSwipes, int steps) throws UiObjectNotFoundException {
        Log.d(LOG_TAG, "scrollToBeginning() on selector = " + getSelector());
        // protect against potential hanging and return after preset attempts
        for(int x = 0; x < maxSwipes; x++) {
            if(!scrollBackward(steps)) {
                break;
            }
        }
        return true;
    }

    /**
     * See {@link UiScrollable#scrollToBeginning(int, int)}
     *
     * @param maxSwipes
     * @return true on scrolled else false
     */
    public boolean scrollToBeginning(int maxSwipes) throws UiObjectNotFoundException {
        return scrollToBeginning(maxSwipes, SCROLL_STEPS);
    }

    /**
     * See {@link UiScrollable#scrollToBeginning(int, int)}
     *
     * @param maxSwipes
     * @return true on scrolled else false
     */
    public boolean flingToBeginning(int maxSwipes) throws UiObjectNotFoundException {
        return scrollToBeginning(maxSwipes, FLING_STEPS);
    }

    /**
     * Scrolls to the end of a scrollable UI element. The end could be the bottom most
     * in case of vertical controls or the right most for horizontal controls. Caution
     * is required on devices configured with right to left languages like Arabic and Hebrew.
     *
     * @param steps use steps to control the speed, so that it may be a scroll, or fling
     * @return true on scrolled else false
     */
    public boolean scrollToEnd(int maxSwipes, int steps) throws UiObjectNotFoundException {
        // protect against potential hanging and return after preset attempts
        for(int x = 0; x < maxSwipes; x++) {
            if(!scrollForward(steps)) {
                break;
            }
        }
        return true;
    }

    /**
     * See {@link UiScrollable#scrollToEnd(int, int)
     *
     * @param maxSwipes
     * @return true on scrolled else false
     */
    public boolean scrollToEnd(int maxSwipes) throws UiObjectNotFoundException {
        return scrollToEnd(maxSwipes, SCROLL_STEPS);
    }

    /**
     * See {@link UiScrollable#scrollToEnd(int, int)}
     *
     * @param maxSwipes
     * @return true on scrolled else false
     */
    public boolean flingToEnd(int maxSwipes) throws UiObjectNotFoundException {
        return scrollToEnd(maxSwipes, FLING_STEPS);
    }

    /**
     * Returns the percentage of a widget's size that's considered as no touch zone when swiping
     *
     * Dead zones are set as percentage of a widget's total width or height where
     * swipe start point cannot start or swipe end point cannot end. It is like a margin
     * around the actual dimensions of the widget. Swipes will always be start and
     * end inside this margin.
     *
     * This is important when the widget being swiped may not respond to the swipe if
     * started at a point too near to the edge. The default is 10% from either edge.
     *
     * @return a value between 0 and 1
     */
    public double getSwipeDeadZonePercentage() {
        return mSwipeDeadZonePercentage;
    }

    /**
     * Sets the percentage of a widget's size that's considered as no touch zone when swiping
     *
     * Dead zones are set as percentage of a widget's total width or height where
     * swipe start point cannot start or swipe end point cannot end. It is like a margin
     * around the actual dimensions of the widget. Swipes will always start and
     * end inside this margin.
     *
     * This is important when the widget being swiped may not respond to the swipe if
     * started at a point too near to the edge. The default is 10% from either edge
     *
     * @param swipeDeadZonePercentage is a value between 0 and 1
     * @return reference to itself
     */
    public UiScrollable setSwipeDeadZonePercentage(double swipeDeadZonePercentage) {
        mSwipeDeadZonePercentage = swipeDeadZonePercentage;
        return this;
    }
}
