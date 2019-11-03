/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2017-2019 The LineageOS Project
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

package org.lineageos.internal.phone;

import android.content.Context;
import android.os.Environment;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class SensitivePhoneNumbers {
    private final String LOG_TAG = this.getClass().getSimpleName();

    public static final String SENSIBLE_PHONENUMBERS_FILE_PATH = "etc/sensitive_pn.xml";
    private static final String ns = null;

    private static SensitivePhoneNumbers sInstance = null;
    private static boolean sNumbersLoaded;

    private HashMap<String, ArrayList<String>> mSensitiveNumbersMap = new HashMap<>();

    private SensitivePhoneNumbers() { }

    public static SensitivePhoneNumbers getInstance() {
        if (sInstance == null) {
            sInstance = new SensitivePhoneNumbers();
        }
        return sInstance;
    }

    private void loadSensiblePhoneNumbers() {
        if (sNumbersLoaded) {
            return;
        }

        FileReader sensiblePNReader;

        File sensiblePNFile = new File(Environment.getRootDirectory(),
                SENSIBLE_PHONENUMBERS_FILE_PATH);

        try {
            sensiblePNReader = new FileReader(sensiblePNFile);
        } catch (FileNotFoundException e) {
            Log.w(LOG_TAG, "Can not open " + sensiblePNFile.getAbsolutePath());
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(sensiblePNReader);
            parser.nextTag();

            readSensitivePNS(parser);

            sensiblePNReader.close();
        } catch (IOException | XmlPullParserException e) {
            Log.w(LOG_TAG, "Exception in spn-conf parser", e);
        }

        sNumbersLoaded = true;
    }

    private void readSensitivePNS(XmlPullParser parser)
                throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, "sensitivePNS");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (!"sensitivePN".equals(name)) {
                break;
            }
            SensitivePhoneNumber sensitivePN = SensitivePhoneNumber
                    .readSensitivePhoneNumbers(parser);
            String[] mccs = sensitivePN.getNetworkNumeric().split(",");
            ArrayList<String> sensitive_nums = sensitivePN.getPhoneNumbers();
            for (String mcc : mccs) {
                mSensitiveNumbersMap.put(mcc, sensitive_nums);
            }
        }
    }

    public boolean isSensitiveNumber(Context context, String numberToCheck, int subId) {
        loadSensiblePhoneNumbers();
        String nationalNumber = formatNumberToNational(context, numberToCheck);

        SubscriptionManager subManager = context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> list = subManager.getActiveSubscriptionInfoList();
        if (list != null) {
            // Test all subscriptions so an accidential use of a wrong sim also hides the number
            for (SubscriptionInfo subInfo : list) {
                String mcc = String.valueOf(subInfo.getMcc());
                if (isSensitiveNumber(nationalNumber, mcc)) {
                    return true;
                }
            }
        } else {
            // Fall back to check with the passed subId
            TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                subId = SubscriptionManager.getDefaultSubscriptionId();
            }
            String networkUsed = telephonyManager.getNetworkOperator(subId);
            if (!TextUtils.isEmpty(networkUsed)) {
                String networkMCC = networkUsed.substring(0, 3);
                return isSensitiveNumber(nationalNumber, networkMCC);
            }
        }

        return false;
    }

    private boolean isSensitiveNumber(String numberToCheck, String mcc) {
        if (!TextUtils.isEmpty(numberToCheck)) {
            if (mSensitiveNumbersMap.containsKey(mcc)) {
                for (String num : mSensitiveNumbersMap.get(mcc)) {
                    if (PhoneNumberUtils.compare(numberToCheck, num)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String formatNumberToNational(Context context, String number) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        String countryIso = context.getResources().getConfiguration().locale.getCountry();

        Phonenumber.PhoneNumber pn = null;
        try {
            pn = util.parse(number, countryIso);
        } catch (NumberParseException e) {
        }

        if (pn != null) {
            return util.format(pn, PhoneNumberFormat.NATIONAL);
        } else {
            return number;
        }
    }
}
