/*
 * Copyright (C) 2006, 2012 The Android Open Source Project
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

package com.android.internal.telephony;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.util.Log;

import com.android.internal.telephony.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.IccCardApplicationStatus.PersoSubState;
import com.android.internal.telephony.IccCardStatus.PinState;
import com.android.internal.telephony.cdma.RuimFileHandler;
import com.android.internal.telephony.cdma.RuimRecords;
import com.android.internal.telephony.gsm.SIMFileHandler;
import com.android.internal.telephony.gsm.SIMRecords;
import com.android.internal.telephony.ims.IsimFileHandler;
import com.android.internal.telephony.ims.IsimUiccRecords;

/**
 * {@hide}
 */
public class UiccCardApplication {
    private static final String LOG_TAG = "RIL_UiccCardApplication";
    private static final boolean DBG = true;

    private static final int EVENT_QUERY_FACILITY_FDN_DONE = 1;
    private static final int EVENT_CHANGE_FACILITY_FDN_DONE = 2;
    private static final int EVENT_QUERY_FACILITY_LOCK_DONE = 3;
    private static final int EVENT_CHANGE_FACILITY_LOCK_DONE = 4;

    private final Object  mLock = new Object();
    private UiccCard      mUiccCard; //parent
    private AppState      mAppState;
    private AppType       mAppType;
    private PersoSubState mPersoSubState;
    private String        mAid;
    private String        mAppLabel;
    private boolean       mPin1Replaced;
    private PinState      mPin1State;
    private PinState      mPin2State;
    private boolean       mIccFdnEnabled;
    private boolean       mDesiredFdnEnabled;
    private boolean       mIccLockEnabled;
    private boolean       mDesiredPinLocked;

    private CommandsInterface mCi;
    private Context mContext;
    private IccRecords mIccRecords;
    private IccFileHandler mIccFh;

    private boolean mDestroyed;//set to true once this App is commanded to be disposed of.

    private RegistrantList mReadyRegistrants = new RegistrantList();
    private RegistrantList mPinLockedRegistrants = new RegistrantList();
    private RegistrantList mNetworkLockedRegistrants = new RegistrantList();

    UiccCardApplication(UiccCard uiccCard,
                        IccCardApplicationStatus as,
                        Context c,
                        CommandsInterface ci) {
        if (DBG) log("Creating UiccApp: " + as);
        mUiccCard = uiccCard;
        mAppState = as.app_state;
        mAppType = as.app_type;
        mPersoSubState = as.perso_substate;
        mAid = as.aid;
        mAppLabel = as.app_label;
        mPin1Replaced = (as.pin1_replaced != 0);
        mPin1State = as.pin1;
        mPin2State = as.pin2;

        mContext = c;
        mCi = ci;

        mIccFh = createIccFileHandler(as.app_type);
        mIccRecords = createIccRecords(as.app_type, mContext, mCi);
        if (mAppState == AppState.APPSTATE_READY) {
            queryFdn();
            queryPin1State();
        }
    }

    void update (IccCardApplicationStatus as, Context c, CommandsInterface ci) {
        synchronized (mLock) {
            if (mDestroyed) {
                loge("Application updated after destroyed! Fix me!");
                return;
            }

            if (DBG) log(mAppType + " update. New " + as);
            mContext = c;
            mCi = ci;
            AppType oldAppType = mAppType;
            AppState oldAppState = mAppState;
            PersoSubState oldPersoSubState = mPersoSubState;
            mAppType = as.app_type;
            mAppState = as.app_state;
            mPersoSubState = as.perso_substate;
            mAid = as.aid;
            mAppLabel = as.app_label;
            mPin1Replaced = (as.pin1_replaced != 0);
            mPin1State = as.pin1;
            mPin2State = as.pin2;

            if (mAppType != oldAppType) {
                if (mIccFh != null) { mIccFh.dispose();}
                if (mIccRecords != null) { mIccRecords.dispose();}
                mIccFh = createIccFileHandler(as.app_type);
                mIccRecords = createIccRecords(as.app_type, c, ci);
            }

            if (mPersoSubState != oldPersoSubState &&
                    mPersoSubState == PersoSubState.PERSOSUBSTATE_SIM_NETWORK) {
                notifyNetworkLockedRegistrantsIfNeeded(null);
            }

            if (mAppState != oldAppState) {
                if (DBG) log(oldAppType + " changed state: " + oldAppState + " -> " + mAppState);
                // If the app state turns to APPSTATE_READY, then query FDN status,
                //as it might have failed in earlier attempt.
                if (mAppState == AppState.APPSTATE_READY) {
                    queryFdn();
                    queryPin1State();
                }
                notifyPinLockedRegistrantsIfNeeded(null);
                notifyReadyRegistrantsIfNeeded(null);
            }
        }
    }

    void dispose() {
        synchronized (mLock) {
            if (DBG) log(mAppType + " being Disposed");
            mDestroyed = true;
            if (mIccRecords != null) { mIccRecords.dispose();}
            if (mIccFh != null) { mIccFh.dispose();}
            mIccRecords = null;
            mIccFh = null;
        }
    }

    private IccRecords createIccRecords(AppType type, Context c, CommandsInterface ci) {
        if (type == AppType.APPTYPE_USIM || type == AppType.APPTYPE_SIM) {
            return new SIMRecords(this, c, ci);
        } else if (type == AppType.APPTYPE_RUIM || type == AppType.APPTYPE_CSIM){
            return new RuimRecords(this, c, ci);
        } else if (type == AppType.APPTYPE_ISIM) {
            return new IsimUiccRecords(this, c, ci);
        } else {
            // Unknown app type (maybe detection is still in progress)
            return null;
        }
    }

    private IccFileHandler createIccFileHandler(AppType type) {
        switch (type) {
            case APPTYPE_SIM:
                return new SIMFileHandler(this, mAid, mCi);
            case APPTYPE_RUIM:
                return new RuimFileHandler(this, mAid, mCi);
            case APPTYPE_USIM:
                return new UsimFileHandler(this, mAid, mCi);
            case APPTYPE_CSIM:
                return new CsimFileHandler(this, mAid, mCi);
            case APPTYPE_ISIM:
                return new IsimFileHandler(this, mAid, mCi);
            default:
                return null;
        }
    }

    /** Assumes mLock is held. */
    private void queryFdn() {
        //This shouldn't change run-time. So needs to be called only once.
        int serviceClassX;

        serviceClassX = CommandsInterface.SERVICE_CLASS_VOICE +
                        CommandsInterface.SERVICE_CLASS_DATA +
                        CommandsInterface.SERVICE_CLASS_FAX;
        mCi.queryFacilityLockForApp (
                CommandsInterface.CB_FACILITY_BA_FD, "", serviceClassX,
                mAid, mHandler.obtainMessage(EVENT_QUERY_FACILITY_FDN_DONE));
    }
    /**
     * Interpret EVENT_QUERY_FACILITY_LOCK_DONE
     * @param ar is asyncResult of Query_Facility_Locked
     */
    private void onQueryFdnEnabled(AsyncResult ar) {
        synchronized (mLock) {
            if (ar.exception != null) {
                if (DBG) log("Error in querying facility lock:" + ar.exception);
                return;
            }

            int[] ints = (int[])ar.result;
            if(ints.length != 0) {
                mIccFdnEnabled = (0!=ints[0]);
                if (DBG) log("Query facility lock : "  + mIccFdnEnabled);
            } else {
                loge("Bogus facility lock response");
            }
        }
    }

    private void onChangeFdnDone(AsyncResult ar) {
        synchronized (mLock) {
            if (ar.exception == null) {
                mIccFdnEnabled = mDesiredFdnEnabled;
                if (DBG) log("EVENT_CHANGE_FACILITY_FDN_DONE: " +
                        "mIccFdnEnabled=" + mIccFdnEnabled);
            } else {
                loge("Error change facility fdn with exception " + ar.exception);
            }
            Message response = (Message)ar.userObj;
            AsyncResult.forMessage(response).exception = ar.exception;
            response.sendToTarget();
        }
    }

    /** REMOVE when mIccLockEnabled is not needed, assumes mLock is held */
    private void queryPin1State() {
        int serviceClassX = CommandsInterface.SERVICE_CLASS_VOICE +
                CommandsInterface.SERVICE_CLASS_DATA +
                CommandsInterface.SERVICE_CLASS_FAX;
        mCi.queryFacilityLockForApp (
            CommandsInterface.CB_FACILITY_BA_SIM, "", serviceClassX,
            mAid, mHandler.obtainMessage(EVENT_QUERY_FACILITY_LOCK_DONE));
    }

    /** REMOVE when mIccLockEnabled is not needed*/
    private void onQueryFacilityLock(AsyncResult ar) {
        synchronized (mLock) {
            if(ar.exception != null) {
                if (DBG) log("Error in querying facility lock:" + ar.exception);
                return;
            }

            int[] ints = (int[])ar.result;
            if(ints.length != 0) {
                if (DBG) log("Query facility lock : "  + ints[0]);

                mIccLockEnabled = (ints[0] != 0);

                if (mIccLockEnabled) {
                    mPinLockedRegistrants.notifyRegistrants();
                }

                // Sanity check: we expect mPin1State to match mIccLockEnabled.
                // When mPin1State is DISABLED mIccLockEanbled should be false.
                // When mPin1State is ENABLED mIccLockEnabled should be true.
                //
                // Here we validate these assumptions to assist in identifying which ril/radio's
                // have not correctly implemented GET_SIM_STATUS
                switch (mPin1State) {
                    case PINSTATE_DISABLED:
                        if (mIccLockEnabled) {
                            loge("QUERY_FACILITY_LOCK:enabled GET_SIM_STATUS.Pin1:disabled."
                                    + " Fixme");
                        }
                        break;
                    case PINSTATE_ENABLED_NOT_VERIFIED:
                    case PINSTATE_ENABLED_VERIFIED:
                    case PINSTATE_ENABLED_BLOCKED:
                    case PINSTATE_ENABLED_PERM_BLOCKED:
                        if (!mIccLockEnabled) {
                            loge("QUERY_FACILITY_LOCK:disabled GET_SIM_STATUS.Pin1:enabled."
                                    + " Fixme");
                        }
                }
            } else {
                loge("Bogus facility lock response");
            }
        }
    }

    /** REMOVE when mIccLockEnabled is not needed */
    private void onChangeFacilityLock(AsyncResult ar) {
        synchronized (mLock) {
            if (ar.exception == null) {
                mIccLockEnabled = mDesiredPinLocked;
                if (DBG) log( "EVENT_CHANGE_FACILITY_LOCK_DONE: mIccLockEnabled= "
                        + mIccLockEnabled);
            } else {
                loge("Error change facility lock with exception " + ar.exception);
            }
            AsyncResult.forMessage(((Message)ar.userObj)).exception = ar.exception;
            ((Message)ar.userObj).sendToTarget();
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg){
            AsyncResult ar;

            if (mDestroyed) {
                loge("Received message " + msg + "[" + msg.what
                        + "] while being destroyed. Ignoring.");
                return;
            }

            switch (msg.what) {
                case EVENT_QUERY_FACILITY_FDN_DONE:
                    ar = (AsyncResult)msg.obj;
                    onQueryFdnEnabled(ar);
                    break;
                case EVENT_CHANGE_FACILITY_FDN_DONE:
                    ar = (AsyncResult)msg.obj;
                    onChangeFdnDone(ar);
                    break;
                case EVENT_QUERY_FACILITY_LOCK_DONE:
                    ar = (AsyncResult)msg.obj;
                    onQueryFacilityLock(ar);
                    break;
                case EVENT_CHANGE_FACILITY_LOCK_DONE:
                    ar = (AsyncResult)msg.obj;
                    onChangeFacilityLock(ar);
                    break;
                default:
                    loge("Unknown Event " + msg.what);
            }
        }
    };

    public void registerForReady(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mReadyRegistrants.add(r);
            notifyReadyRegistrantsIfNeeded(r);
        }
    }

    public void unregisterForReady(Handler h) {
        synchronized (mLock) {
            mReadyRegistrants.remove(h);
        }
    }

    /**
     * Notifies handler of any transition into State.isPinLocked()
     */
    public void registerForLocked(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mPinLockedRegistrants.add(r);
            notifyPinLockedRegistrantsIfNeeded(r);
        }
    }

    public void unregisterForLocked(Handler h) {
        synchronized (mLock) {
            mPinLockedRegistrants.remove(h);
        }
    }

    /**
     * Notifies handler of any transition into State.NETWORK_LOCKED
     */
    public void registerForNetworkLocked(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mNetworkLockedRegistrants.add(r);
            notifyNetworkLockedRegistrantsIfNeeded(r);
        }
    }

    public void unregisterForNetworkLocked(Handler h) {
        synchronized (mLock) {
            mNetworkLockedRegistrants.remove(h);
        }
    }

    /**
     * Notifies specified registrant, assume mLock is held.
     *
     * @param r Registrant to be notified. If null - all registrants will be notified
     */
    private void notifyReadyRegistrantsIfNeeded(Registrant r) {
        if (mDestroyed) {
            return;
        }
        if (mAppState == AppState.APPSTATE_READY) {
            if (mPin1State == PinState.PINSTATE_ENABLED_NOT_VERIFIED ||
                    mPin1State == PinState.PINSTATE_ENABLED_BLOCKED ||
                    mPin1State == PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                loge("Sanity check failed! APPSTATE is ready while PIN1 is not verified!!!");
                // Don't notify if application is in insane state
                return;
            }
            if (r == null) {
                if (DBG) log("Notifying registrants: READY");
                mReadyRegistrants.notifyRegistrants();
            } else {
                if (DBG) log("Notifying 1 registrant: READY");
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    /**
     * Notifies specified registrant, assume mLock is held.
     *
     * @param r Registrant to be notified. If null - all registrants will be notified
     */
    private void notifyPinLockedRegistrantsIfNeeded(Registrant r) {
        if (mDestroyed) {
            return;
        }

        if (mAppState == AppState.APPSTATE_PIN ||
                mAppState == AppState.APPSTATE_PUK) {
            if (mPin1State == PinState.PINSTATE_ENABLED_VERIFIED ||
                    mPin1State == PinState.PINSTATE_DISABLED) {
                loge("Sanity check failed! APPSTATE is locked while PIN1 is not!!!");
                //Don't notify if application is in insane state
                return;
            }
            if (r == null) {
                if (DBG) log("Notifying registrants: LOCKED");
                mPinLockedRegistrants.notifyRegistrants();
            } else {
                if (DBG) log("Notifying 1 registrant: LOCKED");
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    /**
     * Notifies specified registrant, assume mLock is held.
     *
     * @param r Registrant to be notified. If null - all registrants will be notified
     */
    private void notifyNetworkLockedRegistrantsIfNeeded(Registrant r) {
        if (mDestroyed) {
            return;
        }

        if (mAppState == AppState.APPSTATE_SUBSCRIPTION_PERSO &&
                mPersoSubState == PersoSubState.PERSOSUBSTATE_SIM_NETWORK) {
            if (r == null) {
                if (DBG) log("Notifying registrants: NETWORK_LOCKED");
                mNetworkLockedRegistrants.notifyRegistrants();
            } else {
                if (DBG) log("Notifying 1 registrant: NETWORK_LOCED");
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    public AppState getState() {
        synchronized (mLock) {
            return mAppState;
        }
    }

    public AppType getType() {
        synchronized (mLock) {
            return mAppType;
        }
    }

    public PersoSubState getPersoSubState() {
        synchronized (mLock) {
            return mPersoSubState;
        }
    }

    public String getAid() {
        synchronized (mLock) {
            return mAid;
        }
    }

    public PinState getPin1State() {
        synchronized (mLock) {
            if (mPin1Replaced) {
                return mUiccCard.getUniversalPinState();
            }
            return mPin1State;
        }
    }

    public IccFileHandler getIccFileHandler() {
        synchronized (mLock) {
            return mIccFh;
        }
    }

    public IccRecords getIccRecords() {
        synchronized (mLock) {
            return mIccRecords;
        }
    }

    /**
     * Supply the ICC PIN to the ICC
     *
     * When the operation is complete, onComplete will be sent to its
     * Handler.
     *
     * onComplete.obj will be an AsyncResult
     *
     * ((AsyncResult)onComplete.obj).exception == null on success
     * ((AsyncResult)onComplete.obj).exception != null on fail
     *
     * If the supplied PIN is incorrect:
     * ((AsyncResult)onComplete.obj).exception != null
     * && ((AsyncResult)onComplete.obj).exception
     *       instanceof com.android.internal.telephony.gsm.CommandException)
     * && ((CommandException)(((AsyncResult)onComplete.obj).exception))
     *          .getCommandError() == CommandException.Error.PASSWORD_INCORRECT
     *
     *
     */
    public void supplyPin (String pin, Message onComplete) {
        synchronized (mLock) {
            mCi.supplyIccPin(pin, onComplete);
        }
    }

    public void supplyPuk (String puk, String newPin, Message onComplete) {
        synchronized (mLock) {
            mCi.supplyIccPuk(puk, newPin, onComplete);
        }
    }

    public void supplyPin2 (String pin2, Message onComplete) {
        synchronized (mLock) {
            mCi.supplyIccPin2(pin2, onComplete);
        }
    }

    public void supplyPuk2 (String puk2, String newPin2, Message onComplete) {
        synchronized (mLock) {
            mCi.supplyIccPuk2(puk2, newPin2, onComplete);
        }
    }

    public void supplyNetworkDepersonalization (String pin, Message onComplete) {
        synchronized (mLock) {
            if (DBG) log("supplyNetworkDepersonalization");
            mCi.supplyNetworkDepersonalization(pin, onComplete);
        }
    }

    /**
     * Check whether ICC pin lock is enabled
     * This is a sync call which returns the cached pin enabled state
     *
     * @return true for ICC locked enabled
     *         false for ICC locked disabled
     */
    public boolean getIccLockEnabled() {
        return mIccLockEnabled;
        /* STOPSHIP: Remove line above and all code associated with setting
           mIccLockEanbled once all RIL correctly sends the pin1 state.
        // Use getPin1State to take into account pin1Replaced flag
        PinState pinState = getPin1State();
        return pinState == PinState.PINSTATE_ENABLED_NOT_VERIFIED ||
               pinState == PinState.PINSTATE_ENABLED_VERIFIED ||
               pinState == PinState.PINSTATE_ENABLED_BLOCKED ||
               pinState == PinState.PINSTATE_ENABLED_PERM_BLOCKED;*/
     }

    /**
     * Check whether ICC fdn (fixed dialing number) is enabled
     * This is a sync call which returns the cached pin enabled state
     *
     * @return true for ICC fdn enabled
     *         false for ICC fdn disabled
     */
    public boolean getIccFdnEnabled() {
        synchronized (mLock) {
            return mIccFdnEnabled;
        }
    }

    /**
     * Set the ICC pin lock enabled or disabled
     * When the operation is complete, onComplete will be sent to its handler
     *
     * @param enabled "true" for locked "false" for unlocked.
     * @param password needed to change the ICC pin state, aka. Pin1
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    public void setIccLockEnabled (boolean enabled,
            String password, Message onComplete) {
        synchronized (mLock) {
            int serviceClassX;
            serviceClassX = CommandsInterface.SERVICE_CLASS_VOICE +
                    CommandsInterface.SERVICE_CLASS_DATA +
                    CommandsInterface.SERVICE_CLASS_FAX;

            mDesiredPinLocked = enabled;

            mCi.setFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_SIM,
                    enabled, password, serviceClassX, mAid,
                    mHandler.obtainMessage(EVENT_CHANGE_FACILITY_LOCK_DONE, onComplete));
        }
    }

    /**
     * Set the ICC fdn enabled or disabled
     * When the operation is complete, onComplete will be sent to its handler
     *
     * @param enabled "true" for locked "false" for unlocked.
     * @param password needed to change the ICC fdn enable, aka Pin2
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    public void setIccFdnEnabled (boolean enabled,
            String password, Message onComplete) {
        synchronized (mLock) {
            int serviceClassX;
            serviceClassX = CommandsInterface.SERVICE_CLASS_VOICE +
                    CommandsInterface.SERVICE_CLASS_DATA +
                    CommandsInterface.SERVICE_CLASS_FAX +
                    CommandsInterface.SERVICE_CLASS_SMS;

            mDesiredFdnEnabled = enabled;

            mCi.setFacilityLockForApp(CommandsInterface.CB_FACILITY_BA_FD,
                    enabled, password, serviceClassX, mAid,
                    mHandler.obtainMessage(EVENT_CHANGE_FACILITY_FDN_DONE, onComplete));
        }
    }

    /**
     * Change the ICC password used in ICC pin lock
     * When the operation is complete, onComplete will be sent to its handler
     *
     * @param oldPassword is the old password
     * @param newPassword is the new password
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    public void changeIccLockPassword(String oldPassword, String newPassword,
            Message onComplete) {
        synchronized (mLock) {
            if (DBG) log("changeIccLockPassword");
            mCi.changeIccPinForApp(oldPassword, newPassword, mAid,
                    onComplete);
        }
    }

    /**
     * Change the ICC password used in ICC fdn enable
     * When the operation is complete, onComplete will be sent to its handler
     *
     * @param oldPassword is the old password
     * @param newPassword is the new password
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    public void changeIccFdnPassword(String oldPassword, String newPassword,
            Message onComplete) {
        synchronized (mLock) {
            if (DBG) log("changeIccFdnPassword");
            mCi.changeIccPin2ForApp(oldPassword, newPassword, mAid,
                    onComplete);
        }
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private void loge(String msg) {
        Log.e(LOG_TAG, msg);
    }
}
