/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.internal.telephony.ims;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.internal.telephony.AdnRecord;
import com.android.internal.telephony.AdnRecordCache;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccFileHandler;
import com.android.internal.telephony.IccRecords;
import com.android.internal.telephony.UiccCardApplication;
import com.android.internal.telephony.gsm.SimTlv;
import com.android.internal.telephony.gsm.SpnOverride;
//import com.android.internal.telephony.gsm.VoiceMailConstants;

import java.nio.charset.Charset;
import java.util.ArrayList;

import static com.android.internal.telephony.IccConstants.EF_DOMAIN;
import static com.android.internal.telephony.IccConstants.EF_IMPI;
import static com.android.internal.telephony.IccConstants.EF_IMPU;

/**
 * {@hide}
 */
public final class IsimUiccRecords extends IccRecords implements IsimRecords {
    protected static final String LOG_TAG = "GSM";

    private static final boolean DBG = true;
    private static final boolean DUMP_RECORDS = false;   // Note: PII is logged when this is true

    private static final int EVENT_APP_READY = 1;

    // ISIM EF records (see 3GPP TS 31.103)
    private String mIsimImpi;               // IMS private user identity
    private String mIsimDomain;             // IMS home network domain name
    private String[] mIsimImpu;             // IMS public user identity(s)

    private static final int TAG_ISIM_VALUE = 0x80;     // From 3GPP TS 31.103

    public IsimUiccRecords(UiccCardApplication app, Context c, CommandsInterface ci) {
        super(app, c, ci);

        recordsRequested = false;  // No load request is made till SIM ready

        // recordsToLoad is set to 0 because no requests are made yet
        recordsToLoad = 0;

        mParentApp.registerForReady(this, EVENT_APP_READY, null);
    }

    @Override
    public void dispose() {
        log("Disposing " + this);
        //Unregister for all events
        mParentApp.unregisterForReady(this);
        resetRecords();
        super.dispose();
    }

    // ***** Overridden from Handler
    public void handleMessage(Message msg) {
        if (mDestroyed.get()) {
            Log.e(LOG_TAG, "Received message " + msg +
                    "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }

        try {
            switch (msg.what) {
                case EVENT_APP_READY:
                    onReady();
                    break;

                default:
                    super.handleMessage(msg);   // IccRecords handles generic record load responses

            }
        } catch (RuntimeException exc) {
            // I don't want these exceptions to be fatal
            Log.w(LOG_TAG, "Exception parsing SIM record", exc);
        }
    }

    protected void fetchIsimRecords() {
        recordsRequested = true;

        mFh.loadEFTransparent(EF_IMPI, obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, new EfIsimImpiLoaded()));
        recordsToLoad++;

        mFh.loadEFLinearFixedAll(EF_IMPU, obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, new EfIsimImpuLoaded()));
        recordsToLoad++;

        mFh.loadEFTransparent(EF_DOMAIN, obtainMessage(
                IccRecords.EVENT_GET_ICC_RECORD_DONE, new EfIsimDomainLoaded()));
        recordsToLoad++;

        log("fetchIsimRecords " + recordsToLoad);
    }

    protected void resetRecords() {
        // recordsRequested is set to false indicating that the SIM
        // read requests made so far are not valid. This is set to
        // true only when fresh set of read requests are made.
        recordsRequested = false;
    }

    private class EfIsimImpiLoaded implements IccRecords.IccRecordLoaded {
        public String getEfName() {
            return "EF_ISIM_IMPI";
        }
        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            mIsimImpi = isimTlvToString(data);
            if (DUMP_RECORDS) log("EF_IMPI=" + mIsimImpi);
        }
    }

    private class EfIsimImpuLoaded implements IccRecords.IccRecordLoaded {
        public String getEfName() {
            return "EF_ISIM_IMPU";
        }
        public void onRecordLoaded(AsyncResult ar) {
            ArrayList<byte[]> impuList = (ArrayList<byte[]>) ar.result;
            if (DBG) log("EF_IMPU record count: " + impuList.size());
            mIsimImpu = new String[impuList.size()];
            int i = 0;
            for (byte[] identity : impuList) {
                String impu = isimTlvToString(identity);
                if (DUMP_RECORDS) log("EF_IMPU[" + i + "]=" + impu);
                mIsimImpu[i++] = impu;
            }
        }
    }

    private class EfIsimDomainLoaded implements IccRecords.IccRecordLoaded {
        public String getEfName() {
            return "EF_ISIM_DOMAIN";
        }
        public void onRecordLoaded(AsyncResult ar) {
            byte[] data = (byte[]) ar.result;
            mIsimDomain = isimTlvToString(data);
            if (DUMP_RECORDS) log("EF_DOMAIN=" + mIsimDomain);
        }
    }

    /**
     * ISIM records for IMS are stored inside a Tag-Length-Value record as a UTF-8 string
     * with tag value 0x80.
     * @param record the byte array containing the IMS data string
     * @return the decoded String value, or null if the record can't be decoded
     */
    private static String isimTlvToString(byte[] record) {
        SimTlv tlv = new SimTlv(record, 0, record.length);
        do {
            if (tlv.getTag() == TAG_ISIM_VALUE) {
                return new String(tlv.getData(), Charset.forName("UTF-8"));
            }
        } while (tlv.nextObject());

        Log.e(LOG_TAG, "[ISIM] can't find TLV tag in ISIM record, returning null");
        return null;
    }

    @Override
    protected void onRecordLoaded() {
        // One record loaded successfully or failed, In either case
        // we need to update the recordsToLoad count
        recordsToLoad -= 1;

        if (recordsToLoad == 0 && recordsRequested == true) {
            onAllRecordsLoaded();
        } else if (recordsToLoad < 0) {
            loge("recordsToLoad <0, programmer error suspected");
            recordsToLoad = 0;
        }
    }

    @Override
    protected void onAllRecordsLoaded() {
        recordsLoadedRegistrants.notifyRegistrants(
                new AsyncResult(null, null, null));
    }

    /**
     * Return the IMS private user identity (IMPI).
     * Returns null if the IMPI hasn't been loaded or isn't present on the ISIM.
     * @return the IMS private user identity string, or null if not available
     */
    @Override
    public String getIsimImpi() {
        return mIsimImpi;
    }

    /**
     * Return the IMS home network domain name.
     * Returns null if the IMS domain hasn't been loaded or isn't present on the ISIM.
     * @return the IMS home network domain name, or null if not available
     */
    @Override
    public String getIsimDomain() {
        return mIsimDomain;
    }

    /**
     * Return an array of IMS public user identities (IMPU).
     * Returns null if the IMPU hasn't been loaded or isn't present on the ISIM.
     * @return an array of IMS public user identity strings, or null if not available
     */
    @Override
    public String[] getIsimImpu() {
        return (mIsimImpu != null) ? mIsimImpu.clone() : null;
    }

    @Override
    public int getDisplayRule(String plmn) {
        // Not applicable to Isim
        return 0;
    }

    @Override
    public void onReady() {
        fetchIsimRecords();
    }

    @Override
    public void onRefresh(boolean fileChanged, int[] fileList) {
        // We do not handle it in Isim
    }

    @Override
    public void setVoiceMailNumber(String alphaTag, String voiceNumber,
            Message onComplete) {
        // Not applicable to Isim
    }

    @Override
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        // Not applicable to Isim
    }

    @Override
    protected void log(String s) {
        if (DBG) Log.d(LOG_TAG, "[ISIM] " + s);
    }

    @Override
    protected void loge(String s) {
        if (DBG) Log.e(LOG_TAG, "[ISIM] " + s);
    }
}
