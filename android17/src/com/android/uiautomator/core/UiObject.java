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
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * A UiObject is a representation of a UI element. It is not in any way directly bound to a
 * UI element as an object reference. A UiObject holds information to help it
 * locate a matching UI element at runtime based on the {@link UiSelector} properties specified in
 * its constructor. Since a UiObject is a representative for a UI element, it can
 * be reused for different views with matching UI elements.
 */
public class UiObject {
    private static final String LOG_TAG = UiObject.class.getSimpleName();
    protected static final long WAIT_FOR_SELECTOR_TIMEOUT = 10 * 1000;
    protected static final long WAIT_FOR_SELECTOR_POLL = 1000;
    // set a default timeout to 5.5s, since ANR threshold is 5s
    protected static final long WAIT_FOR_WINDOW_TMEOUT = 5500;
    protected static final long WAIT_FOR_EVENT_TMEOUT = 3 * 1000;
    protected static final int SWIPE_MARGIN_LIMIT = 5;

    private final UiSelector mSelector;
    private final UiAutomatorBridge mUiAutomationBridge;

    /**
     * Constructs a UiObject to represent a specific UI element matched by the specified
     * {@link UiSelector} selector properties.
     *
     * @param selector
     */
    public UiObject(UiSelector selector) {
        mUiAutomationBridge = UiDevice.getInstance().getAutomatorBridge();
        mSelector = selector;
    }

    /**
     * Debugging helper. A test can dump the properties of a selector as a string
     * to its logs if needed. <code>getSelector().toString();</code>
     *
     * @return {@link UiSelector}
     */
    public final UiSelector getSelector() {
        return new UiSelector(mSelector);
    }

    /**
     * Retrieves the {@link QueryController} to translate a {@link UiSelector} selector
     * into an {@link AccessibilityNodeInfo}.
     *
     * @return {@link QueryController}
     */
    QueryController getQueryController() {
        return mUiAutomationBridge.getQueryController();
    }

    /**
     * Retrieves the {@link InteractionController} to perform finger actions such as tapping,
     * swiping or entering text.
     *
     * @return {@link InteractionController}
     */
    InteractionController getInteractionController() {
        return mUiAutomationBridge.getInteractionController();
    }

    /**
     * Creates a new UiObject representing a child UI element of the element currently represented
     * by this UiObject.
     *
     * @param selector for UI element to match
     * @return a new UiObject representing the matched UI element
     */
    public UiObject getChild(UiSelector selector) throws UiObjectNotFoundException {
        return new UiObject(getSelector().childSelector(selector));
    }

    /**
     * Creates a new UiObject representing a child UI element from the parent element currently
     * represented by this object. Essentially this is starting the search from the parent
     * element and can also be used to find sibling UI elements to the one currently represented
     * by this UiObject.
     *
     * @param selector for the UI element to match
     * @return a new UiObject representing the matched UI element
     * @throws UiObjectNotFoundException
     */
    public UiObject getFromParent(UiSelector selector) throws UiObjectNotFoundException {
        return new UiObject(getSelector().fromParent(selector));
    }

    /**
     * Counts the child UI elements immediately under the UI element currently represented by
     * this UiObject.
     *
     * @return the count of child UI elements.
     * @throws UiObjectNotFoundException
     */
    public int getChildCount() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return node.getChildCount();
    }

    /**
     * Uses the member UiSelector properties to find a matching UI element reported in
     * the accessibility hierarchy.
     *
     * @param selector {@link UiSelector}
     * @param timeout in milliseconds
     * @return AccessibilityNodeInfo if found else null
     */
    protected AccessibilityNodeInfo findAccessibilityNodeInfo(long timeout) {
        AccessibilityNodeInfo node = null;
        if(UiDevice.getInstance().isInWatcherContext()) {
            // we will NOT run watchers or do any sort of polling if the
            // reason we're here is because of a watcher is executing. Watchers
            // will not have other watchers run for them so they should not block
            // while they poll for items to become present. We disable polling for them.
            node = getQueryController().findAccessibilityNodeInfo(getSelector());
        } else {
            long startMills = SystemClock.uptimeMillis();
            long currentMills = 0;
            while (currentMills <= timeout) {
                node = getQueryController().findAccessibilityNodeInfo(getSelector());
                if (node != null) {
                    break;
                } else {
                    UiDevice.getInstance().runWatchers();
                }
                currentMills = SystemClock.uptimeMillis() - startMills;
                if(timeout > 0) {
                    SystemClock.sleep(WAIT_FOR_SELECTOR_POLL);
                }
            }
        }
        return node;
    }

    /**
     * Perform the action on the UI element that is represented by this UiObject. Also see
     * {@link #scrollToBeginning(int)}, {@link #scrollToEnd(int)}, {@link #scrollBackward()},
     * {@link #scrollForward()}.
     *
     * @param steps indicates the number of injected move steps into the system. Steps are
     * injected about 5ms apart. So a 100 steps may take about 1/2 second to complete.
     * @return true of successful
     * @throws UiObjectNotFoundException
     */
    public boolean swipeUp(int steps) throws UiObjectNotFoundException {
        Rect rect = getVisibleBounds();
        if(rect.height() <= SWIPE_MARGIN_LIMIT * 2)
            return false; // too small to swipe
        return getInteractionController().swipe(rect.centerX(),
                rect.bottom - SWIPE_MARGIN_LIMIT, rect.centerX(), rect.top + SWIPE_MARGIN_LIMIT,
                steps);
    }

    /**
     * Perform the action on the UI element that is represented by this object, Also see
     * {@link #scrollToBeginning(int)}, {@link #scrollToEnd(int)}, {@link #scrollBackward()},
     * {@link #scrollForward()}. This method will perform the swipe gesture over any
     * surface. The targeted UI element does not need to have the attribute
     * <code>scrollable</code> set to <code>true</code> for this operation to be performed.
     *
     * @param steps indicates the number of injected move steps into the system. Steps are
     * injected about 5ms apart. So a 100 steps may take about 1/2 second to complete.
     * @return true if successful
     * @throws UiObjectNotFoundException
     */
    public boolean swipeDown(int steps) throws UiObjectNotFoundException {
        Rect rect = getVisibleBounds();
        if(rect.height() <= SWIPE_MARGIN_LIMIT * 2)
            return false; // too small to swipe
        return getInteractionController().swipe(rect.centerX(),
                rect.top + SWIPE_MARGIN_LIMIT, rect.centerX(),
                rect.bottom - SWIPE_MARGIN_LIMIT, steps);
    }

    /**
     * Perform the action on the UI element that is represented by this object. Also see
     * {@link #scrollToBeginning(int)}, {@link #scrollToEnd(int)}, {@link #scrollBackward()},
     * {@link #scrollForward()}. This method will perform the swipe gesture over any
     * surface. The targeted UI element does not need to have the attribute
     * <code>scrollable</code> set to <code>true</code> for this operation to be performed.
     *
     * @param steps indicates the number of injected move steps into the system. Steps are
     * injected about 5ms apart. So a 100 steps may take about 1/2 second to complete.
     * @return true if successful
     * @throws UiObjectNotFoundException
     */
    public boolean swipeLeft(int steps) throws UiObjectNotFoundException {
        Rect rect = getVisibleBounds();
        if(rect.width() <= SWIPE_MARGIN_LIMIT * 2)
            return false; // too small to swipe
        return getInteractionController().swipe(rect.right - SWIPE_MARGIN_LIMIT,
                rect.centerY(), rect.left + SWIPE_MARGIN_LIMIT, rect.centerY(), steps);
    }

    /**
     * Perform the action on the UI element that is represented by this object. Also see
     * {@link #scrollToBeginning(int)}, {@link #scrollToEnd(int)}, {@link #scrollBackward()},
     * {@link #scrollForward()}. This method will perform the swipe gesture over any
     * surface. The targeted UI element does not need to have the attribute
     * <code>scrollable</code> set to <code>true</code> for this operation to be performed.
     *
     * @param steps indicates the number of injected move steps into the system. Steps are
     * injected about 5ms apart. So a 100 steps may take about 1/2 second to complete.
     * @return true if successful
     * @throws UiObjectNotFoundException
     */
    public boolean swipeRight(int steps) throws UiObjectNotFoundException {
        Rect rect = getVisibleBounds();
        if(rect.width() <= SWIPE_MARGIN_LIMIT * 2)
            return false; // too small to swipe
        return getInteractionController().swipe(rect.left + SWIPE_MARGIN_LIMIT,
                rect.centerY(), rect.right - SWIPE_MARGIN_LIMIT, rect.centerY(), steps);
    }

    /**
     * Finds the visible bounds of a partially visible UI element
     *
     * @param node
     * @return null if node is null, else a Rect containing visible bounds
     */
    private Rect getVisibleBounds(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }

        // targeted node's bounds
        Rect nodeRect = AccessibilityNodeInfoHelper.getVisibleBoundsInScreen(node);

        // is the targeted node within a scrollable container?
        AccessibilityNodeInfo scrollableParentNode = getScrollableParent(node);
        if(scrollableParentNode == null) {
            // nothing to adjust for so return the node's Rect as is
            return nodeRect;
        }

        // Scrollable parent's visible bounds
        Rect parentRect = AccessibilityNodeInfoHelper
                .getVisibleBoundsInScreen(scrollableParentNode);
        // adjust for partial clipping of targeted by parent node if required
        nodeRect.intersect(parentRect);
        return nodeRect;
    }

    /**
     * Walk the hierarchy up to find a scrollable parent. A scrollable parent
     * indicates that this node may be in a content where it is partially
     * visible due to scrolling. its clickable center maybe invisible and
     * adjustments should be made to the click coordinates.
     *
     * @param node
     * @return
     */
    private AccessibilityNodeInfo getScrollableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo parent = node;
        while(parent != null) {
            parent = parent.getParent();
            if (parent != null && parent.isScrollable()) {
                return parent;
            }
        }
        return null;
    }

    /**
     * Performs a click at the center of the visible bounds of the UI element represented
     * by this UiObject.
     *
     * @return true id successful else false
     * @throws UiObjectNotFoundException
     */
    public boolean click() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        Rect rect = getVisibleBounds(node);
        return getInteractionController().clickAndWaitForEvents(rect.centerX(), rect.centerY(),
                WAIT_FOR_EVENT_TMEOUT, false, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED +
                AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    /**
     * See {@link #clickAndWaitForNewWindow(long)}
     * This method is intended to reliably wait for window transitions that would typically take
     * longer than the usual default timeouts.
     *
     * @return true if the event was triggered, else false
     * @throws UiObjectNotFoundException
     */
    public boolean clickAndWaitForNewWindow() throws UiObjectNotFoundException {
        return clickAndWaitForNewWindow(WAIT_FOR_WINDOW_TMEOUT);
    }

    /**
     * Performs a click at the center of the visible bounds of the UI element represented
     * by this UiObject and waits for window transitions.
     *
     * This method differ from {@link UiObject#click()} only in that this method waits for a
     * a new window transition as a result of the click. Some examples of a window transition:
     * <li>launching a new activity</li>
     * <li>bringing up a pop-up menu</li>
     * <li>bringing up a dialog</li>
     *
     * @param timeout timeout before giving up on waiting for a new window
     * @return true if the event was triggered, else false
     * @throws UiObjectNotFoundException
     */
    public boolean clickAndWaitForNewWindow(long timeout) throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        Rect rect = getVisibleBounds(node);
        return getInteractionController().clickAndWaitForNewWindow(
                rect.centerX(), rect.centerY(), timeout);
    }

    /**
     * Clicks the top and left corner of the UI element
     *
     * @return true on success
     * @throws Exception
     */
    public boolean clickTopLeft() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        Rect rect = getVisibleBounds(node);
        return getInteractionController().click(rect.left + 5, rect.top + 5);
    }

    /**
     * Long clicks bottom and right corner of the UI element
     *
     * @return true if operation was successful
     * @throws UiObjectNotFoundException
     */
    public boolean longClickBottomRight() throws UiObjectNotFoundException  {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        Rect rect = getVisibleBounds(node);
        return getInteractionController().longTap(rect.right - 5, rect.bottom - 5);
    }

    /**
     * Clicks the bottom and right corner of the UI element
     *
     * @return true on success
     * @throws Exception
     */
    public boolean clickBottomRight() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        Rect rect = getVisibleBounds(node);
        return getInteractionController().click(rect.right - 5, rect.bottom - 5);
    }

    /**
     * Long clicks the center of the visible bounds of the UI element
     *
     * @return true if operation was successful
     * @throws UiObjectNotFoundException
     */
    public boolean longClick() throws UiObjectNotFoundException  {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        Rect rect = getVisibleBounds(node);
        return getInteractionController().longTap(rect.centerX(), rect.centerY());
    }

    /**
     * Long clicks on the top and left corner of the UI element
     *
     * @return true if operation was successful
     * @throws UiObjectNotFoundException
     */
    public boolean longClickTopLeft() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        Rect rect = getVisibleBounds(node);
        return getInteractionController().longTap(rect.left + 5, rect.top + 5);
    }

    /**
     * Reads the <code>text</code> property of the UI element
     *
     * @return text value of the current node represented by this UiObject
     * @throws UiObjectNotFoundException if no match could be found
     */
    public String getText() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        String retVal = safeStringReturn(node.getText());
        Log.d(LOG_TAG, String.format("getText() = %s", retVal));
        return retVal;
    }

    /**
     * Reads the <code>content_desc</code> property of the UI element
     *
     * @return value of node attribute "content_desc"
     * @throws UiObjectNotFoundException
     */
    public String getContentDescription() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return safeStringReturn(node.getContentDescription());
    }

    /**
     * Sets the text in an editable field, after clearing the field's content.
     *
     * The {@link UiSelector} selector of this object must reference a UI element that is editable.
     *
     * When you call this method, the method first simulates a {@link #click()} on
     * editable field to set focus. The method then clears the field's contents
     * and injects your specified text into the field.
     *
     * If you want to capture the original contents of the field, call {@link #getText()} first.
     * You can then modify the text and use this method to update the field.
     *
     * @param text string to set
     * @return true if operation is successful
     * @throws UiObjectNotFoundException
     */
    public boolean setText(String text) throws UiObjectNotFoundException {
        clearTextField();
        return getInteractionController().sendText(text);
    }

    /**
     * Clears the existing text contents in an editable field.
     *
     * The {@link UiSelector} of this object must reference a UI element that is editable.
     *
     * When you call this method, the method first sets focus at the start edge of the field.
     * The method then simulates a long-press to select the existing text, and deletes the
     * selected text.
     *
     * If a "Select-All" option is displayed, the method will automatically attempt to use it
     * to ensure full text selection.
     *
     * Note that it is possible that not all the text in the field is selected; for example,
     * if the text contains separators such as spaces, slashes, at symbol etc.
     * Also, not all editable fields support the long-press functionality.
     *
     * @throws UiObjectNotFoundException
     */
    public void clearTextField() throws UiObjectNotFoundException {
        // long click left + center
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        Rect rect = getVisibleBounds(node);
        getInteractionController().longTap(rect.left + 20, rect.centerY());
        // check if the edit menu is open
        UiObject selectAll = new UiObject(new UiSelector().descriptionContains("Select all"));
        if(selectAll.waitForExists(50))
            selectAll.click();
        // wait for the selection
        SystemClock.sleep(250);
        // delete it
        getInteractionController().sendKey(KeyEvent.KEYCODE_DEL, 0);
    }

    /**
     * Check if the UI element's <code>checked</code> property is currently true
     *
     * @return true if it is else false
     */
    public boolean isChecked() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return node.isChecked();
    }

    /**
     * Check if the UI element's <code>selected</code> property is currently true
     *
     * @return true if it is else false
     * @throws UiObjectNotFoundException
     */
    public boolean isSelected() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return node.isSelected();
    }

    /**
     * Check if the UI element's <code>checkable</code> property is currently true
     *
     * @return true if it is else false
     * @throws UiObjectNotFoundException
     */
    public boolean isCheckable() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return node.isCheckable();
    }

    /**
     * Check if the UI element's <code>enabled</code> property is currently true
     *
     * @return true if it is else false
     * @throws UiObjectNotFoundException
     */
    public boolean isEnabled() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return node.isEnabled();
    }

    /**
     * Check if the UI element's <code>clickable</code> property is currently true
     *
     * @return true if it is else false
     * @throws UiObjectNotFoundException
     */
    public boolean isClickable() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return node.isClickable();
    }

    /**
     * Check if the UI element's <code>focused</code> property is currently true
     *
     * @return true if it is else false
     * @throws UiObjectNotFoundException
     */
    public boolean isFocused() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return node.isFocused();
    }

    /**
     * Check if the UI element's <code>focusable</code> property is currently true
     *
     * @return true if it is else false
     * @throws UiObjectNotFoundException
     */
    public boolean isFocusable() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return node.isFocusable();
    }

    /**
     * Check if the UI element's <code>scrollable</code> property is currently true
     *
     * @return true if it is else false
     * @throws UiObjectNotFoundException
     */
    public boolean isScrollable() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return node.isScrollable();
    }

    /**
     * Check if the UI element's <code>long-clickable</code> property is currently true
     *
     * @return true if it is else false
     * @throws UiObjectNotFoundException
     */
    public boolean isLongClickable() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return node.isLongClickable();
    }

    /**
     * Reads the UI element's <code>package</code> property
     *
     * @return true if it is else false
     * @throws UiObjectNotFoundException
     */
    public String getPackageName() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return safeStringReturn(node.getPackageName());
    }

    /**
     * Returns the visible bounds of the UI element.
     *
     * If a portion of the UI element is visible, only the bounds of the visible portion are
     * reported.
     *
     * @return Rect
     * @throws UiObjectNotFoundException
     * @see {@link #getBound()}
     */
    public Rect getVisibleBounds() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        return getVisibleBounds(node);
    }

    /**
     * Returns the UI element's <code>bounds</code> property. See {@link #getVisibleBounds()}
     *
     * @return Rect
     * @throws UiObjectNotFoundException
     */
    public Rect getBounds() throws UiObjectNotFoundException {
        AccessibilityNodeInfo node = findAccessibilityNodeInfo(WAIT_FOR_SELECTOR_TIMEOUT);
        if(node == null) {
            throw new UiObjectNotFoundException(getSelector().toString());
        }
        Rect nodeRect = new Rect();
        node.getBoundsInScreen(nodeRect);

        return nodeRect;
    }

    /**
     * Waits a specified length of time for a UI element to become visible.
     *
     * This method waits until the UI element becomes visible on the display, or
     * until the timeout has elapsed. You can use this method in situations where
     * the content that you want to select is not immediately displayed.
     *
     * @param timeout the amount of time to wait (in milliseconds)
     * @return true if the UI element is displayed, else false if timeout elapsed while waiting
     */
    public boolean waitForExists(long timeout) {
        if(findAccessibilityNodeInfo(timeout) != null) {
            return true;
        }
        return false;
    }

    /**
     * Waits a specified length of time for a UI element to become undetectable.
     *
     * This method waits until a UI element is no longer matchable, or until the
     * timeout has elapsed.
     *
     * A UI element becomes undetectable when the {@link UiSelector} of the object is
     * unable to find a match because the element has either changed its state or is no
     * longer displayed.
     *
     * You can use this method when attempting to wait for some long operation
     * to compete, such as downloading a large file or connecting to a remote server.
     *
     * @param timeout time to wait (in milliseconds)
     * @return true if the element is gone before timeout elapsed, else false if timeout elapsed
     * but a matching element is still found.
     */
    public boolean waitUntilGone(long timeout) {
        long startMills = SystemClock.uptimeMillis();
        long currentMills = 0;
        while (currentMills <= timeout) {
            if(findAccessibilityNodeInfo(0) == null)
                return true;
            currentMills = SystemClock.uptimeMillis() - startMills;
            if(timeout > 0)
                SystemClock.sleep(WAIT_FOR_SELECTOR_POLL);
        }
        return false;
    }

    /**
     * Check if UI element exists.
     *
     * This methods performs a {@link #waitForExists(long)} with zero timeout. This
     * basically returns immediately whether the UI element represented by this UiObject
     * exists or not. If you need to wait longer for this UI element, then see
     * {@link #waitForExists(long)}.
     *
     * @return true if the UI element represented by this UiObject does exist
     */
    public boolean exists() {
        return waitForExists(0);
    }

    private String safeStringReturn(CharSequence cs) {
        if(cs == null)
            return "";
        return cs.toString();
    }
}
