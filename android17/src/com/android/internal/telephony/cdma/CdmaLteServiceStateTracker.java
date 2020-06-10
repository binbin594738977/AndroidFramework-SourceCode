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

package com.android.internal.telephony.cdma;

import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.EventLogTags;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.IccCard;

import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellIdentityLte;
import android.telephony.SignalStrength;
import android.telephony.ServiceState;
import android.telephony.cdma.CdmaCellLocation;
import android.text.TextUtils;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;

import android.text.TextUtils;
import android.util.Log;
import android.util.EventLog;

import com.android.internal.telephony.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.gsm.GsmDataConnectionTracker;
import com.android.internal.telephony.IccCardConstants;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class CdmaLteServiceStateTracker extends CdmaServiceStateTracker {
    private CDMALTEPhone mCdmaLtePhone;
    private final CellInfoLte mCellInfoLte;

    private ServiceState  mLteSS;  // The last LTE state from Voice Registration

    private CellIdentityLte mNewCellIdentityLte = new CellIdentityLte();
    private CellIdentityLte mLasteCellIdentityLte = new CellIdentityLte();

    public CdmaLteServiceStateTracker(CDMALTEPhone phone) {
        super(phone, new CellInfoLte());
        mCdmaLtePhone = phone;
        mCellInfoLte = (CellInfoLte) mCellInfo;

        mLteSS = new ServiceState();
        ((CellInfoLte)mCellInfo).setCellSignalStrength(new CellSignalStrengthLte());
        ((CellInfoLte)mCellInfo).setCellIdentity(new CellIdentityLte());

        if (DBG) log("CdmaLteServiceStateTracker Constructors");
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        int[] ints;
        String[] strings;
        switch (msg.what) {
        case EVENT_POLL_STATE_GPRS:
            if (DBG) log("handleMessage EVENT_POLL_STATE_GPRS");
            ar = (AsyncResult)msg.obj;
            handlePollStateResult(msg.what, ar);
            break;
        case EVENT_RUIM_RECORDS_LOADED:
            RuimRecords ruim = (RuimRecords)mIccRecords;
            if ((ruim != null) && ruim.isProvisioned()) {
                mMdn = ruim.getMdn();
                mMin = ruim.getMin();
                parseSidNid(ruim.getSid(), ruim.getNid());
                mPrlVersion = ruim.getPrlVersion();;
                mIsMinInfoReady = true;
                updateOtaspState();
            }
            // SID/NID/PRL is loaded. Poll service state
            // again to update to the roaming state with
            // the latest variables.
            pollState();
            break;
        default:
            super.handleMessage(msg);
        }
    }

    /**
     * Set the cdmaSS for EVENT_POLL_STATE_REGISTRATION_CDMA
     */
    @Override
    protected void setCdmaTechnology(int radioTechnology) {
        // Called on voice registration state response.
        // Just record new CDMA radio technology
        newSS.setRadioTechnology(radioTechnology);
    }

    /**
     * Handle the result of one of the pollState()-related requests
     */
    @Override
    protected void handlePollStateResultMessage(int what, AsyncResult ar) {
        if (what == EVENT_POLL_STATE_GPRS) {
            String states[] = (String[])ar.result;
            if (DBG) {
                log("handlePollStateResultMessage: EVENT_POLL_STATE_GPRS states.length=" +
                        states.length + " states=" + states);
            }

            int type = 0;
            int regState = -1;
            if (states.length > 0) {
                try {
                    regState = Integer.parseInt(states[0]);

                    // states[3] (if present) is the current radio technology
                    if (states.length >= 4 && states[3] != null) {
                        type = Integer.parseInt(states[3]);
                    }
                } catch (NumberFormatException ex) {
                    loge("handlePollStateResultMessage: error parsing GprsRegistrationState: "
                                    + ex);
                }
                if (states.length >= 10) {
                    int mcc;
                    int mnc;
                    int tac;
                    int pci;
                    int eci;
                    int csgid;
                    String operatorNumeric = null;

                    try {
                        operatorNumeric = mLteSS.getOperatorNumeric();
                        mcc = Integer.parseInt(operatorNumeric.substring(0,3));
                    } catch (Exception e) {
                        try {
                            operatorNumeric = ss.getOperatorNumeric();
                            mcc = Integer.parseInt(operatorNumeric.substring(0,3));
                        } catch (Exception ex) {
                            loge("handlePollStateResultMessage: bad mcc operatorNumeric=" +
                                    operatorNumeric + " ex=" + ex);
                            operatorNumeric = "";
                            mcc = Integer.MAX_VALUE;
                        }
                    }
                    try {
                        mnc = Integer.parseInt(operatorNumeric.substring(3));
                    } catch (Exception e) {
                        loge("handlePollStateResultMessage: bad mnc operatorNumeric=" +
                                operatorNumeric + " e=" + e);
                        mnc = Integer.MAX_VALUE;
                    }
                    try {
                        tac = Integer.parseInt(states[6], 16);
                    } catch (Exception e) {
                        loge("handlePollStateResultMessage: bad tac states[6]=" +
                                states[6] + " e=" + e);
                        tac = Integer.MAX_VALUE;
                    }
                    try {
                        pci = Integer.parseInt(states[7], 16);
                    } catch (Exception e) {
                        loge("handlePollStateResultMessage: bad pci states[7]=" +
                                states[7] + " e=" + e);
                        pci = Integer.MAX_VALUE;
                    }
                    try {
                        eci = Integer.parseInt(states[8], 16);
                    } catch (Exception e) {
                        loge("handlePollStateResultMessage: bad eci states[8]=" +
                                states[8] + " e=" + e);
                        eci = Integer.MAX_VALUE;
                    }
                    try {
                        csgid = Integer.parseInt(states[9], 16);
                    } catch (Exception e) {
                        // FIX: Always bad so don't pollute the logs
                        // loge("handlePollStateResultMessage: bad csgid states[9]=" +
                        //        states[9] + " e=" + e);
                        csgid = Integer.MAX_VALUE;
                    }
                    mNewCellIdentityLte = new CellIdentityLte(mcc, mnc, eci, pci, tac);
                    if (DBG) {
                        log("handlePollStateResultMessage: mNewLteCellIdentity=" +
                                mNewCellIdentityLte);
                    }
                }
            }

            mLteSS.setRadioTechnology(type);
            mLteSS.setState(regCodeToServiceState(regState));
        } else {
            super.handlePollStateResultMessage(what, ar);
        }
    }

    @Override
    protected void pollState() {
        pollingContext = new int[1];
        pollingContext[0] = 0;

        switch (cm.getRadioState()) {
            case RADIO_UNAVAILABLE:
                newSS.setStateOutOfService();
                newCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                mGotCountryCode = false;

                pollStateDone();
                break;

            case RADIO_OFF:
                newSS.setStateOff();
                newCellLoc.setStateInvalid();
                setSignalStrengthDefaultValues();
                mGotCountryCode = false;

                pollStateDone();
                break;

            default:
                // Issue all poll-related commands at once, then count
                // down the responses which are allowed to arrive
                // out-of-order.

                pollingContext[0]++;
                // RIL_REQUEST_OPERATOR is necessary for CDMA
                cm.getOperator(obtainMessage(EVENT_POLL_STATE_OPERATOR_CDMA, pollingContext));

                pollingContext[0]++;
                // RIL_REQUEST_VOICE_REGISTRATION_STATE is necessary for CDMA
                cm.getVoiceRegistrationState(obtainMessage(EVENT_POLL_STATE_REGISTRATION_CDMA,
                        pollingContext));

                pollingContext[0]++;
                // RIL_REQUEST_DATA_REGISTRATION_STATE
                cm.getDataRegistrationState(obtainMessage(EVENT_POLL_STATE_GPRS,
                                            pollingContext));
                break;
        }
    }

    @Override
    protected void pollStateDone() {
        // determine data RadioTechnology from both LET and CDMA SS
        if (mLteSS.getState() == ServiceState.STATE_IN_SERVICE) {
            //in LTE service
            mNewRilRadioTechnology = mLteSS.getRilRadioTechnology();
            mNewDataConnectionState = mLteSS.getState();
            newSS.setRadioTechnology(mNewRilRadioTechnology);
            log("pollStateDone LTE/eHRPD STATE_IN_SERVICE mNewRilRadioTechnology = " +
                    mNewRilRadioTechnology);
        } else {
            // LTE out of service, get CDMA Service State
            mNewRilRadioTechnology = newSS.getRilRadioTechnology();
            mNewDataConnectionState = radioTechnologyToDataServiceState(mNewRilRadioTechnology);
            log("pollStateDone CDMA STATE_IN_SERVICE mNewRilRadioTechnology = " +
                    mNewRilRadioTechnology + " mNewDataConnectionState = " +
                    mNewDataConnectionState);
        }

        // TODO: Add proper support for LTE Only, we should be looking at
        //       the preferred network mode, to know when newSS state should
        //       be coming from mLteSs state. This was needed to pass a VZW
        //       LTE Only test.
        //
        // If CDMA service is OOS, double check if the device is running with LTE only
        // mode. If that is the case, derive the service state from LTE side.
        // To set in LTE only mode, sqlite3 /data/data/com.android.providers.settings/
        // databases/settings.db "update secure set value='11' where name='preferred_network_mode'"
        if (newSS.getState() == ServiceState.STATE_OUT_OF_SERVICE) {
            int networkMode = android.provider.Settings.Global.getInt(phone.getContext()
                                  .getContentResolver(),
                                  android.provider.Settings.Global.PREFERRED_NETWORK_MODE,
                                  RILConstants.PREFERRED_NETWORK_MODE);
            if (networkMode == RILConstants.NETWORK_MODE_LTE_ONLY) {
                if (DBG) log("pollState: LTE Only mode");
                newSS.setState(mLteSS.getState());
            }
        }

        if (DBG) log("pollStateDone: oldSS=[" + ss + "] newSS=[" + newSS + "]");

        boolean hasRegistered = ss.getState() != ServiceState.STATE_IN_SERVICE
                && newSS.getState() == ServiceState.STATE_IN_SERVICE;

        boolean hasDeregistered = ss.getState() == ServiceState.STATE_IN_SERVICE
                && newSS.getState() != ServiceState.STATE_IN_SERVICE;

        boolean hasCdmaDataConnectionAttached =
            mDataConnectionState != ServiceState.STATE_IN_SERVICE
                && mNewDataConnectionState == ServiceState.STATE_IN_SERVICE;

        boolean hasCdmaDataConnectionDetached =
            mDataConnectionState == ServiceState.STATE_IN_SERVICE
                && mNewDataConnectionState != ServiceState.STATE_IN_SERVICE;

        boolean hasCdmaDataConnectionChanged =
            mDataConnectionState != mNewDataConnectionState;

        boolean hasRadioTechnologyChanged = mRilRadioTechnology != mNewRilRadioTechnology;

        boolean hasChanged = !newSS.equals(ss);

        boolean hasRoamingOn = !ss.getRoaming() && newSS.getRoaming();

        boolean hasRoamingOff = ss.getRoaming() && !newSS.getRoaming();

        boolean hasLocationChanged = !newCellLoc.equals(cellLoc);

        boolean has4gHandoff =
                mNewDataConnectionState == ServiceState.STATE_IN_SERVICE &&
                (((mRilRadioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_LTE) &&
                  (mNewRilRadioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD)) ||
                 ((mRilRadioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD) &&
                  (mNewRilRadioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_LTE)));

        boolean hasMultiApnSupport =
                (((mNewRilRadioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_LTE) ||
                  (mNewRilRadioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD)) &&
                 ((mRilRadioTechnology != ServiceState.RIL_RADIO_TECHNOLOGY_LTE) &&
                  (mRilRadioTechnology != ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD)));

        boolean hasLostMultiApnSupport =
            ((mNewRilRadioTechnology >= ServiceState.RIL_RADIO_TECHNOLOGY_IS95A) &&
             (mNewRilRadioTechnology <= ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A));

        if (DBG) {
            log("pollStateDone:"
                + " hasRegistered=" + hasRegistered
                + " hasDeegistered=" + hasDeregistered
                + " hasCdmaDataConnectionAttached=" + hasCdmaDataConnectionAttached
                + " hasCdmaDataConnectionDetached=" + hasCdmaDataConnectionDetached
                + " hasCdmaDataConnectionChanged=" + hasCdmaDataConnectionChanged
                + " hasRadioTechnologyChanged = " + hasRadioTechnologyChanged
                + " hasChanged=" + hasChanged
                + " hasRoamingOn=" + hasRoamingOn
                + " hasRoamingOff=" + hasRoamingOff
                + " hasLocationChanged=" + hasLocationChanged
                + " has4gHandoff = " + has4gHandoff
                + " hasMultiApnSupport=" + hasMultiApnSupport
                + " hasLostMultiApnSupport=" + hasLostMultiApnSupport);
        }
        // Add an event log when connection state changes
        if (ss.getState() != newSS.getState()
                || mDataConnectionState != mNewDataConnectionState) {
            EventLog.writeEvent(EventLogTags.CDMA_SERVICE_STATE_CHANGE, ss.getState(),
                    mDataConnectionState, newSS.getState(), mNewDataConnectionState);
        }

        ServiceState tss;
        tss = ss;
        ss = newSS;
        newSS = tss;
        // clean slate for next time
        newSS.setStateOutOfService();
        mLteSS.setStateOutOfService();

        if ((hasMultiApnSupport)
                && (phone.mDataConnectionTracker instanceof CdmaDataConnectionTracker)) {
            if (DBG) log("GsmDataConnectionTracker Created");
            phone.mDataConnectionTracker.dispose();
            phone.mDataConnectionTracker = new GsmDataConnectionTracker(mCdmaLtePhone);
        }

        if ((hasLostMultiApnSupport)
                && (phone.mDataConnectionTracker instanceof GsmDataConnectionTracker)) {
            if (DBG)log("GsmDataConnectionTracker disposed");
            phone.mDataConnectionTracker.dispose();
            phone.mDataConnectionTracker = new CdmaDataConnectionTracker(phone);
        }

        CdmaCellLocation tcl = cellLoc;
        cellLoc = newCellLoc;
        newCellLoc = tcl;

        mDataConnectionState = mNewDataConnectionState;
        mRilRadioTechnology = mNewRilRadioTechnology;
        mNewRilRadioTechnology = 0;

        newSS.setStateOutOfService(); // clean slate for next time

        if (hasRadioTechnologyChanged) {
            phone.setSystemProperty(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE,
                    ServiceState.rilRadioTechnologyToString(mRilRadioTechnology));
        }

        if (hasRegistered) {
            mNetworkAttachedRegistrants.notifyRegistrants();
        }

        if (hasChanged) {
            if (phone.isEriFileLoaded()) {
                String eriText;
                // Now the CDMAPhone sees the new ServiceState so it can get the
                // new ERI text
                if (ss.getState() == ServiceState.STATE_IN_SERVICE) {
                    eriText = phone.getCdmaEriText();
                } else if (ss.getState() == ServiceState.STATE_POWER_OFF) {
                    eriText = (mIccRecords != null) ? mIccRecords.getServiceProviderName() : null;
                    if (TextUtils.isEmpty(eriText)) {
                        // Sets operator alpha property by retrieving from
                        // build-time system property
                        eriText = SystemProperties.get("ro.cdma.home.operator.alpha");
                    }
                } else {
                    // Note that ServiceState.STATE_OUT_OF_SERVICE is valid used
                    // for mRegistrationState 0,2,3 and 4
                    eriText = phone.getContext()
                            .getText(com.android.internal.R.string.roamingTextSearching).toString();
                }
                ss.setOperatorAlphaLong(eriText);
            }

            if (mUiccApplcation != null && mUiccApplcation.getState() == AppState.APPSTATE_READY &&
                    mIccRecords != null) {
                // SIM is found on the device. If ERI roaming is OFF, and SID/NID matches
                // one configfured in SIM, use operator name  from CSIM record.
                boolean showSpn =
                    ((RuimRecords)mIccRecords).getCsimSpnDisplayCondition();
                int iconIndex = ss.getCdmaEriIconIndex();

                if (showSpn && (iconIndex == EriInfo.ROAMING_INDICATOR_OFF) &&
                    isInHomeSidNid(ss.getSystemId(), ss.getNetworkId()) &&
                    mIccRecords != null) {
                    ss.setOperatorAlphaLong(mIccRecords.getServiceProviderName());
                }
            }

            String operatorNumeric;

            phone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA,
                    ss.getOperatorAlphaLong());

            String prevOperatorNumeric =
                    SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC, "");
            operatorNumeric = ss.getOperatorNumeric();
            phone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC, operatorNumeric);

            if (operatorNumeric == null) {
                if (DBG) log("operatorNumeric is null");
                phone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY, "");
                mGotCountryCode = false;
            } else {
                String isoCountryCode = "";
                String mcc = operatorNumeric.substring(0, 3);
                try {
                    isoCountryCode = MccTable.countryCodeForMcc(Integer.parseInt(operatorNumeric
                            .substring(0, 3)));
                } catch (NumberFormatException ex) {
                    loge("countryCodeForMcc error" + ex);
                } catch (StringIndexOutOfBoundsException ex) {
                    loge("countryCodeForMcc error" + ex);
                }

                phone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY,
                        isoCountryCode);
                mGotCountryCode = true;

                if (shouldFixTimeZoneNow(phone, operatorNumeric, prevOperatorNumeric,
                        mNeedFixZone)) {
                    fixTimeZone(isoCountryCode);
                }
            }

            phone.setSystemProperty(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING,
                    ss.getRoaming() ? "true" : "false");

            updateSpnDisplay();
            phone.notifyServiceStateChanged(ss);
        }

        if (hasCdmaDataConnectionAttached || has4gHandoff) {
            mAttachedRegistrants.notifyRegistrants();
        }

        if (hasCdmaDataConnectionDetached) {
            mDetachedRegistrants.notifyRegistrants();
        }

        if ((hasCdmaDataConnectionChanged || hasRadioTechnologyChanged)) {
            phone.notifyDataConnection(null);
        }

        if (hasRoamingOn) {
            mRoamingOnRegistrants.notifyRegistrants();
        }

        if (hasRoamingOff) {
            mRoamingOffRegistrants.notifyRegistrants();
        }

        if (hasLocationChanged) {
            phone.notifyLocationChanged();
        }

        ArrayList<CellInfo> arrayCi = new ArrayList<CellInfo>();
        synchronized(mCellInfo) {
            CellInfoLte cil = (CellInfoLte)mCellInfo;

            boolean cidChanged = ! mNewCellIdentityLte.equals(mLasteCellIdentityLte);
            if (hasRegistered || hasDeregistered || cidChanged) {
                // TODO: Handle the absence of LteCellIdentity
                long timeStamp = SystemClock.elapsedRealtime() * 1000;
                boolean registered = ss.getState() == ServiceState.STATE_IN_SERVICE;
                mLasteCellIdentityLte = mNewCellIdentityLte;

                cil.setRegisterd(registered);
                cil.setCellIdentity(mLasteCellIdentityLte);
                if (DBG) {
                    log("pollStateDone: hasRegistered=" + hasRegistered +
                            " hasDeregistered=" + hasDeregistered +
                            " cidChanged=" + cidChanged +
                            " mCellInfo=" + mCellInfo);
                }
                arrayCi.add(mCellInfo);
            }
            mPhoneBase.notifyCellInfo(arrayCi);
        }
    }

    @Override
    protected boolean onSignalStrengthResult(AsyncResult ar, boolean isGsm) {
        if (mRilRadioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_LTE) {
            isGsm = true;
        }
        boolean ssChanged = super.onSignalStrengthResult(ar, isGsm);

        synchronized (mCellInfo) {
            if (mRilRadioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_LTE) {
                mCellInfoLte.setTimeStamp(SystemClock.elapsedRealtime() * 1000);
                mCellInfoLte.setTimeStampType(CellInfo.TIMESTAMP_TYPE_JAVA_RIL);
                mCellInfoLte.getCellSignalStrength()
                                .initialize(mSignalStrength,SignalStrength.INVALID);
            }
            if (mCellInfoLte.getCellIdentity() != null) {
                ArrayList<CellInfo> arrayCi = new ArrayList<CellInfo>();
                arrayCi.add(mCellInfoLte);
                mPhoneBase.notifyCellInfo(arrayCi);
            }
        }
        return ssChanged;
    }

    @Override
    public boolean isConcurrentVoiceAndDataAllowed() {
        // Note: it needs to be confirmed which CDMA network types
        // can support voice and data calls concurrently.
        // For the time-being, the return value will be false.
        return (mRilRadioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_LTE);
    }

    /**
     * Check whether the specified SID and NID pair appears in the HOME SID/NID list
     * read from NV or SIM.
     *
     * @return true if provided sid/nid pair belongs to operator's home network.
     */
    private boolean isInHomeSidNid(int sid, int nid) {
        // if SID/NID is not available, assume this is home network.
        if (isSidsAllZeros()) return true;

        // length of SID/NID shold be same
        if (mHomeSystemId.length != mHomeNetworkId.length) return true;

        if (sid == 0) return true;

        for (int i = 0; i < mHomeSystemId.length; i++) {
            // Use SID only if NID is a reserved value.
            // SID 0 and NID 0 and 65535 are reserved. (C.0005 2.6.5.2)
            if ((mHomeSystemId[i] == sid) &&
                ((mHomeNetworkId[i] == 0) || (mHomeNetworkId[i] == 65535) ||
                 (nid == 0) || (nid == 65535) || (mHomeNetworkId[i] == nid))) {
                return true;
            }
        }
        // SID/NID are not in the list. So device is not in home network
        return false;
    }

    /**
     * @return all available cell information, the returned List maybe empty but never null.
     */
    @Override
    public List<CellInfo> getAllCellInfo() {
        ArrayList<CellInfo> arrayList = new ArrayList<CellInfo>();
        CellInfo ci;
        synchronized(mCellInfo) {
            arrayList.add(mCellInfoLte);
        }
        if (DBG) log ("getAllCellInfo: arrayList=" + arrayList);
        return arrayList;
    }

    @Override
    protected void log(String s) {
        Log.d(LOG_TAG, "[CdmaLteSST] " + s);
    }

    @Override
    protected void loge(String s) {
        Log.e(LOG_TAG, "[CdmaLteSST] " + s);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CdmaLteServiceStateTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mCdmaLtePhone=" + mCdmaLtePhone);
        pw.println(" mLteSS=" + mLteSS);
    }
}
