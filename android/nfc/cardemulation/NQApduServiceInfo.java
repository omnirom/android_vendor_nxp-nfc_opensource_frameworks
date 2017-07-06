/*
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2015 NXP Semiconductors
 * The original Work has been changed by NXP Semiconductors.
 * Copyright (C) 2013 The Android Open Source Project
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

package android.nfc.cardemulation;

import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.NQAidGroup;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.cardemulation.HostApduService;
import android.nfc.cardemulation.OffHostApduService;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import android.graphics.Bitmap;
import com.nxp.nfc.NxpConstants;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @hide
 */
public class NQApduServiceInfo extends ApduServiceInfo implements Parcelable {
    static final String TAG = "NQApduServiceInfo";

    //name of secure element
    static final String SECURE_ELEMENT_ESE = "eSE";
    static final String SECURE_ELEMENT_UICC = "UICC";
    static final String SECURE_ELEMENT_UICC2 = "UICC2";
    //index of secure element
    public static final int SECURE_ELEMENT_ROUTE_ESE = 1;
    public static final int SECURE_ELEMENT_ROUTE_UICC = 2;
    public static final int SECURE_ELEMENT_ROUTE_UICC2 = 0x4;

    //power state value
    static final int POWER_STATE_SWITCH_ON = 1;
    static final int POWER_STATE_SWITCH_OFF = 2;
    static final int POWER_STATE_BATTERY_OFF = 4;

    /**
     * The name of the meta-data element that contains
     * nxp extended SE information about off host service.
     */
    static final String NXP_NFC_EXT_META_DATA =
            "com.nxp.nfc.extensions";


    /**
     * Convenience NFCID2 list
     */
    final ArrayList<String> mNfcid2s;

    /**
     * All AID groups this service handles
     */
    final ArrayList<Nfcid2Group> mNfcid2Groups;

    /**
     * Mapping from category to static AID group
     */
    final HashMap<String, NQAidGroup> mStaticNQAidGroups;

    /**
     * Mapping from category to dynamic AID group
     */
    final HashMap<String, NQAidGroup> mDynamicNQAidGroups;

    final HashMap<String, Nfcid2Group> mNfcid2CategoryToGroup;

    /**
     * The Drawable of the service banner specified by the Application Dynamically.
     */
    public final Drawable mBanner;

    /**
     * This says whether the Application can modify the AIDs or not.
     */
    final boolean mModifiable;

    /**
     * This says whether the Service is enabled or disabled by the user
     * By default it is disabled.This is only applicable for OTHER category.
     * states are as follows
     * ENABLING(service creation)->ENABLED(Committed to Routing)->
     * DISABLING(user requested to disable)->DISABLED(Removed from Routing).
     * In ENABLED or DISABLING state, this service will be accounted for routing.
     */
    int mServiceState;

    /**
      * nxp se extension
      */
    final ESeInfo mSeExtension;
    final FelicaInfo mFelicaExtension;


    static ArrayList<AidGroup> nqAidGroups2AidGroups(ArrayList<NQAidGroup> nqAidGroup) {
        ArrayList<AidGroup> aidGroups = new ArrayList<AidGroup>();
        if(nqAidGroup != null) {
            for(NQAidGroup nqag : nqAidGroup) {
                AidGroup ag = nqag.createAidGroup();
                aidGroups.add(ag);
            }
        }
        return aidGroups;
    }

    /**
     * @hide
     */
  public NQApduServiceInfo(ResolveInfo info, boolean onHost, String description,
            ArrayList<NQAidGroup> staticNQAidGroups, ArrayList<NQAidGroup> dynamicNQAidGroups,
            boolean requiresUnlock, int bannerResource, int uid,
            String settingsActivityName, ESeInfo seExtension,
            ArrayList<Nfcid2Group> nfcid2Groups, Drawable banner,boolean modifiable) {
        super(info, onHost, description, nqAidGroups2AidGroups(staticNQAidGroups), nqAidGroups2AidGroups(dynamicNQAidGroups),
            requiresUnlock, bannerResource, uid, settingsActivityName);

        this.mStaticNQAidGroups = new HashMap<String, NQAidGroup>();
        this.mDynamicNQAidGroups = new HashMap<String, NQAidGroup>();
        if(staticNQAidGroups != null) {
            for (NQAidGroup nqAidGroup : staticNQAidGroups) {
                this.mStaticNQAidGroups.put(nqAidGroup.getCategory(), nqAidGroup);
            }
        }
        if(dynamicNQAidGroups != null) {
            for (NQAidGroup nqAidGroup : dynamicNQAidGroups) {
                this.mDynamicNQAidGroups.put(nqAidGroup.getCategory(), nqAidGroup);
            }
        }
        this.mBanner = banner;
        this.mModifiable = modifiable;
        this.mNfcid2Groups = new ArrayList<Nfcid2Group>();
        this.mNfcid2s = new ArrayList<String>();
        this.mNfcid2CategoryToGroup = new HashMap<String, Nfcid2Group>();
        this.mServiceState = NxpConstants.SERVICE_STATE_ENABLING;
        if(nfcid2Groups != null) {
            for (Nfcid2Group nfcid2Group : nfcid2Groups) {
                this.mNfcid2Groups.add(nfcid2Group);
                this.mNfcid2CategoryToGroup.put(nfcid2Group.category, nfcid2Group);
                this.mNfcid2s.addAll(nfcid2Group.nfcid2s);
            }
        }

        this.mSeExtension = seExtension;
        this.mFelicaExtension = null;
    }

    public NQApduServiceInfo(PackageManager pm, ResolveInfo info, boolean onHost)
            throws XmlPullParserException, IOException {
        super(pm, info, onHost);
        this.mBanner = null;
        this.mModifiable = false;
        this.mServiceState = NxpConstants.SERVICE_STATE_ENABLING;
        ServiceInfo si = info.serviceInfo;
        XmlResourceParser parser = null;
        XmlResourceParser extParser = null;
        try {
            if (onHost) {
                parser = si.loadXmlMetaData(pm, HostApduService.SERVICE_META_DATA);
                if (parser == null) {
                    throw new XmlPullParserException("No " + HostApduService.SERVICE_META_DATA +
                            " meta-data");
                }
            } else {
                parser = si.loadXmlMetaData(pm, OffHostApduService.SERVICE_META_DATA);
                if (parser == null) {
                    throw new XmlPullParserException("No " + OffHostApduService.SERVICE_META_DATA +
                            " meta-data");
                }

                /* load se extension xml */
                extParser = si.loadXmlMetaData(pm, NXP_NFC_EXT_META_DATA);
                if (extParser == null) {
                    Log.d(TAG,"No " + NXP_NFC_EXT_META_DATA +
                            " meta-data");
                }
            }

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
                eventType = parser.next();
            }

            String tagName = parser.getName();
            if (onHost && !"host-apdu-service".equals(tagName)) {
                throw new XmlPullParserException(
                        "Meta-data does not start with <host-apdu-service> tag");
            } else if (!onHost && !"offhost-apdu-service".equals(tagName)) {
                throw new XmlPullParserException(
                        "Meta-data does not start with <offhost-apdu-service> tag");
            }

            Resources res = pm.getResourcesForApplication(si.applicationInfo);
            AttributeSet attrs = Xml.asAttributeSet(parser);

            mStaticNQAidGroups = new HashMap<String, NQAidGroup>();
            mDynamicNQAidGroups = new HashMap<String, NQAidGroup>();

            for(Map.Entry<String,AidGroup> stringaidgroup : mStaticAidGroups.entrySet()) {
                String category = stringaidgroup.getKey();
                AidGroup aidg = stringaidgroup.getValue();
                mStaticNQAidGroups.put(category, new NQAidGroup(aidg));
            }

            for(Map.Entry<String,AidGroup> stringaidgroup : mDynamicAidGroups.entrySet()) {
                String category = stringaidgroup.getKey();
                AidGroup aidg = stringaidgroup.getValue();
                mDynamicNQAidGroups.put(category, new NQAidGroup(aidg));
            }

            mNfcid2Groups = new ArrayList<Nfcid2Group>();
            mNfcid2CategoryToGroup = new HashMap<String, Nfcid2Group>();
            mNfcid2s = new ArrayList<String>();

            final int depth = parser.getDepth();

            Nfcid2Group currentNfcid2Group = null;

            // Parsed values for the current AID group
            while (((eventType = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                    && eventType != XmlPullParser.END_DOCUMENT) {
                tagName = parser.getName();
                if (eventType == XmlPullParser.START_TAG && "nfcid2-group".equals(tagName) &&
                        currentNfcid2Group == null) {
                    final TypedArray groupAttrs = res.obtainAttributes(attrs,
                            com.android.internal.R.styleable.AidGroup);
                    // Get category of NFCID2 group
                    String groupDescription = groupAttrs.getString(
                            com.android.internal.R.styleable.AidGroup_description);
                    String groupCategory = groupAttrs.getString(
                            com.android.internal.R.styleable.AidGroup_category);
                    if (!CardEmulation.CATEGORY_PAYMENT.equals(groupCategory)) {
                        groupCategory = CardEmulation.CATEGORY_OTHER;
                    }
                    currentNfcid2Group = mNfcid2CategoryToGroup.get(groupCategory);
                    if (currentNfcid2Group != null) {
                        if (!CardEmulation.CATEGORY_OTHER.equals(groupCategory)) {
                            Log.e(TAG, "Not allowing multiple nfcid2-groups in the " +
                                    groupCategory + " category");
                            currentNfcid2Group = null;
                        }
                    } else {
                        currentNfcid2Group = new Nfcid2Group(groupCategory, groupDescription);
                    }
                    groupAttrs.recycle();
                } else if (eventType == XmlPullParser.END_TAG && "nfcid2-group".equals(tagName) &&
                        currentNfcid2Group != null) {
                    if (currentNfcid2Group.nfcid2s.size() > 0) {
                        if (!mNfcid2CategoryToGroup.containsKey(currentNfcid2Group.category)) {
                            mNfcid2Groups.add(currentNfcid2Group);
                            mNfcid2CategoryToGroup.put(currentNfcid2Group.category, currentNfcid2Group);
                        }
                    } else {
                        Log.e(TAG, "Not adding <nfcid2-group> with empty or invalid NFCID2s");
                    }
                    currentNfcid2Group = null;
                } else if (eventType == XmlPullParser.START_TAG && "nfcid2-filter".equals(tagName) &&
                        currentNfcid2Group != null) {
                    String nfcid2 = parser.getAttributeValue(null, "name").toUpperCase();
                    String syscode = parser.getAttributeValue(null, "syscode").toUpperCase();
                    String optparam = parser.getAttributeValue(null, "optparam").toUpperCase();
                    /* Only one NFCID2 is allowed per application now.*/
                    if (isValidNfcid2(nfcid2) && currentNfcid2Group.nfcid2s.size() == 0 ) {
                        currentNfcid2Group.nfcid2s.add(nfcid2);
                        currentNfcid2Group.syscode.add(syscode);
                        currentNfcid2Group.optparam.add(optparam);
                        mNfcid2s.add(nfcid2);
                    } else {
                        Log.e(TAG, "Ignoring invalid or duplicate aid: " + nfcid2);
                    }
                }
            }
        } catch (NameNotFoundException e) {
            throw new XmlPullParserException("Unable to create context for: " + si.packageName);
        } finally {
            if (parser != null) parser.close();
        }
        // Parsed values se name and power state
        if (extParser != null)
        {
            try{
                int eventType = extParser.getEventType();
                final int depth = extParser.getDepth();
                String seName = null;
                int powerState  = 0;
                String felicaId = null;
                String optParam = null;

                while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
                    eventType = extParser.next();
                }
                String tagName = extParser.getName();
                if (!"extensions".equals(tagName)) {
                    throw new XmlPullParserException(
                            "Meta-data does not start with <extensions> tag "+tagName);
                }
                while (((eventType = extParser.next()) != XmlPullParser.END_TAG || extParser.getDepth() > depth)
                        && eventType != XmlPullParser.END_DOCUMENT) {
                    tagName = extParser.getName();

                    if (eventType == XmlPullParser.START_TAG && "se-id".equals(tagName) ) {
                        // Get name of eSE
                        seName = extParser.getAttributeValue(null, "name");
                        if (seName == null  || (!seName.equalsIgnoreCase(SECURE_ELEMENT_ESE) && !seName.equalsIgnoreCase(SECURE_ELEMENT_UICC)
                            && !seName.equalsIgnoreCase(SECURE_ELEMENT_UICC2)) ) {
                            throw new XmlPullParserException("Unsupported se name: " + seName);
                        }
                    } else if (eventType == XmlPullParser.START_TAG && "se-power-state".equals(tagName) ) {
                        // Get power state
                        String powerName = extParser.getAttributeValue(null, "name");
                        boolean powerValue = (extParser.getAttributeValue(null, "value").equals("true")) ? true : false;
                        if (powerName.equalsIgnoreCase("SwitchOn") && powerValue) {
                            powerState |= POWER_STATE_SWITCH_ON;
                        }else if (powerName.equalsIgnoreCase("SwitchOff") && powerValue) {
                            powerState |= POWER_STATE_SWITCH_OFF;
                        }else if (powerName.equalsIgnoreCase("BatteryOff") && powerValue) {
                            powerState |= POWER_STATE_BATTERY_OFF;
                        }
                    } else if (eventType == XmlPullParser.START_TAG && "felica-id".equals(tagName) ) {
                        // Get the T3T_IDENTIFIER value
                        felicaId = extParser.getAttributeValue(null, "name");
                        if (felicaId == null  || felicaId.length() > 10 ) {
                            throw new XmlPullParserException("Unsupported felicaId: " + felicaId);
                        }
                        // Get the optional params value
                        optParam = extParser.getAttributeValue(null, "opt-params");
                        if ( optParam.length() > 8 ) {
                            throw new XmlPullParserException("Unsupported opt-params: " + optParam);
                        }
                    }
                }
                if(seName != null) {
                    mSeExtension = new ESeInfo(seName.equals(SECURE_ELEMENT_ESE)?SECURE_ELEMENT_ROUTE_ESE:(seName.equals(SECURE_ELEMENT_UICC)?SECURE_ELEMENT_ROUTE_UICC:SECURE_ELEMENT_ROUTE_UICC2),powerState);
                    Log.d(TAG, mSeExtension.toString());
                } else {
                    mSeExtension = new ESeInfo(-1, 0);
                    Log.d(TAG, mSeExtension.toString());
                }

                if(felicaId != null) {
                    mFelicaExtension = new FelicaInfo(felicaId, optParam);
                    Log.d(TAG, mFelicaExtension.toString());
                } else {
                    mFelicaExtension = new FelicaInfo(null, null);
                }
            } finally {
                extParser.close();
            }
        }else {
            if(!onHost) {
                Log.e(TAG, "SE extension not present, Setting default offhost seID");
                mSeExtension = new ESeInfo(SECURE_ELEMENT_ROUTE_UICC, 0);
            }
            else {
                mSeExtension = new ESeInfo(-1, 0);
            }
            mFelicaExtension = new FelicaInfo(null, null);
        }
    }

    public void writeToXml(XmlSerializer out) throws IOException {
        out.attribute(null, "description", mDescription);
        String modifiable = "";
        if(mModifiable) {
            modifiable = "true";
        } else {
            modifiable = "false";
        }
        out.attribute(null, "modifiable", modifiable);
        out.attribute(null, "uid", Integer.toString(mUid));
        out.attribute(null, "seId", Integer.toString(mSeExtension.seId));
        out.attribute(null, "bannerId", Integer.toString(mBannerResourceId));
        for (NQAidGroup group : mDynamicNQAidGroups.values()) {
            group.writeAsXml(out);
        }
    }

    public ComponentName getComponent() {
        return new ComponentName(mService.serviceInfo.packageName,
                mService.serviceInfo.name);
    }
    public ResolveInfo getResolveInfo() {
        return mService;
    }

    /**
     * This is a convenience function to create an ApduServiceInfo object of the current
     * NQApduServiceInfo.
     * It is required for legacy functions which expect an ApduServiceInfo on a Binder
     * interface.
     *
     * @return An ApduServiceInfo object which can be correctly serialized as parcel
     */
    public ApduServiceInfo createApduServiceInfo() {
        return new ApduServiceInfo(this.getResolveInfo(), this.isOnHost(), this.getDescription(),
            nqAidGroups2AidGroups(this.getStaticNQAidGroups()), nqAidGroups2AidGroups(this.getDynamicNQAidGroups()),
            this.requiresUnlock(), this.getBannerId(), this.getUid(),
            this.getSettingsActivityName());
    }

    /**
     * This api can be used to find the total aid size registered
     * by this service.
     * <p> This returns the size of only {@link #CardEmulation.CATEGORY_OTHER}.
     * <p> This includes both static and dynamic aid groups
     * @param category The category of the corresponding service.{@link #CardEmulation.CATEGORY_OTHER}.
     * @return The aid cache size for particular category.
     */
    public int getAidCacheSize(String category) {
        int aidSize = 0x00;
        if(!CardEmulation.CATEGORY_OTHER.equals(category) || !hasCategory(CardEmulation.CATEGORY_OTHER)) {
            return 0x00;
        }
        aidSize = getAidCacheSizeForCategory(CardEmulation.CATEGORY_OTHER);
        return aidSize;
    }

    public int getAidCacheSizeForCategory(String category) {
        ArrayList<NQAidGroup> nqAidGroups = new ArrayList<NQAidGroup>();
        List<String> aids;
        int aidCacheSize = 0x00;
        int aidLen = 0x00;
        nqAidGroups.addAll(getStaticNQAidGroups());
        nqAidGroups.addAll(getDynamicNQAidGroups());
        if(nqAidGroups == null || nqAidGroups.size() == 0x00) {
            return aidCacheSize;
        }
        for(NQAidGroup aidCache : nqAidGroups) {
            if(!aidCache.getCategory().equals(category)) {
                continue;
            }
            aids = aidCache.getAids();
            if (aids == null || aids.size() == 0) {
                continue;
            }
            for(String aid : aids) {
                aidLen = aid.length();
                if(aid.endsWith("*")) {
                    aidLen = aidLen - 1;
                }
                aidCacheSize += aidLen >> 1;
            }
        }
        return aidCacheSize;
    }
    /**
     * This api can be used to find the total aids count registered
     * by this service.
     * <p> This returns the size of only {@link #CardEmulation.CATEGORY_OTHER}.
     * <p> This includes both static and dynamic aid groups
     * @param category The category of the corresponding service.{@link #CardEmulation.CATEGORY_OTHER}.
     * @return The num of aids corresponding to particular cateogry
     */
    public int geTotalAidNum ( String category) {
        int aidTotalNum = 0x00;
        if(!CardEmulation.CATEGORY_OTHER.equals(category) || !hasCategory(CardEmulation.CATEGORY_OTHER)) {
            return 0x00;
        }
        aidTotalNum = getTotalAidNumCategory(CardEmulation.CATEGORY_OTHER);
        return aidTotalNum;
    }

    private int getTotalAidNumCategory( String category) {
        ArrayList<NQAidGroup> nqAidGroups = new ArrayList<NQAidGroup>();
        List<String> aids;
        int aidTotalNum = 0x00;
        nqAidGroups.addAll(getStaticNQAidGroups());
        nqAidGroups.addAll(getDynamicNQAidGroups());
        if(nqAidGroups == null || nqAidGroups.size() == 0x00) {
            return aidTotalNum;
        }
        for(NQAidGroup aidCache : nqAidGroups) {
            if(!aidCache.getCategory().equals(category)) {
                continue;
            }
            aids = aidCache.getAids();
            if (aids == null || aids.size() == 0) {
                continue;
            }
            for(String aid : aids) {
                if(aid != null && aid.length() > 0x00) { aidTotalNum++;}
            }
        }
        return aidTotalNum;
    }

    public ArrayList<String> getNfcid2s() {
        return mNfcid2s;
    }

    /**
     * Returns a consolidated list of AID groups
     * registered by this service. Note that if a service has both
     * a static (manifest-based) AID group for a category and a dynamic
     * AID group, only the dynamically registered AID group will be returned
     * for that category.
     * @return List of AIDs registered by the service
     */
    public ArrayList<NQAidGroup> getNQAidGroups() {
        final ArrayList<NQAidGroup> groups = new ArrayList<NQAidGroup>();
        for (Map.Entry<String, NQAidGroup> entry : mDynamicNQAidGroups.entrySet()) {
            groups.add(entry.getValue());
        }
        for (Map.Entry<String, NQAidGroup> entry : mStaticNQAidGroups.entrySet()) {
            if (!mDynamicNQAidGroups.containsKey(entry.getKey())) {
                // Consolidate AID groups - don't return static ones
                // if a dynamic group exists for the category.
                groups.add(entry.getValue());
            }
        }
        return groups;
    }

    /**@hide */
    public ArrayList<NQAidGroup> getStaticNQAidGroups() {
        final ArrayList<NQAidGroup> groups = new ArrayList<NQAidGroup>();

        for (Map.Entry<String, NQAidGroup> entry : mStaticNQAidGroups.entrySet()) {
                groups.add(entry.getValue());
        }
        return groups;
    }

    /**@hide */
    public ArrayList<NQAidGroup> getDynamicNQAidGroups() {
        final ArrayList<NQAidGroup> groups = new ArrayList<NQAidGroup>();
        for (Map.Entry<String, NQAidGroup> entry : mDynamicNQAidGroups.entrySet()) {
            groups.add(entry.getValue());
        }
        return groups;
    }

    public ArrayList<Nfcid2Group> getNfcid2Groups() {
        return mNfcid2Groups;
    }

    public ESeInfo getSEInfo() {
        return mSeExtension;
    }


    public boolean getModifiable() {
        return mModifiable;
    }

    public void setOrReplaceDynamicNQAidGroup(NQAidGroup nqAidGroup) {
        super.setOrReplaceDynamicAidGroup(nqAidGroup);
        mDynamicNQAidGroups.put(nqAidGroup.getCategory(), nqAidGroup);
    }

    public NQAidGroup getDynamicNQAidGroupForCategory(String category) {
        return mDynamicNQAidGroups.get(category);
    }

    public boolean removeDynamicNQAidGroupForCategory(String category) {
        super.removeDynamicAidGroupForCategory(category);
        return (mDynamicNQAidGroups.remove(category) != null);
    }

    public Drawable loadBanner(PackageManager pm) {
        Resources res;
        Drawable banner;
        try {
            res = pm.getResourcesForApplication(mService.serviceInfo.packageName);
            if(mBannerResourceId == -1) {
                 banner = mBanner;
            } else {
                banner = res.getDrawable(mBannerResourceId);
            }
            return banner;
        } catch (NotFoundException e) {
            Log.e(TAG, "Could not load banner.");
            return null;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Could not load banner.");
            return null;
        }
    }

    public int getBannerId() {
        return mBannerResourceId;
    }


    static boolean isValidNfcid2(String nfcid2) {
        if (nfcid2 == null)
            return false;

        int nfcid2Length = nfcid2.length();
        if (nfcid2Length == 0 || (nfcid2Length % 2) != 0) {
            Log.e(TAG, "AID " + nfcid2 + " is not correctly formatted.");
            return false;
        }
        // NFCID2 length must be 8 bytes, 16 hex chars
        if (nfcid2Length != 16) {
            Log.e(TAG, "NFCID2 " + nfcid2 + " is not 8 bytes.");
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("ApduService: ");
        out.append(getComponent());
        out.append(", description: " + mDescription);
        out.append(", Static AID Groups: ");
        for (NQAidGroup nqAidGroup : mStaticNQAidGroups.values()) {
            out.append(nqAidGroup.toString());
        }
        out.append(", Dynamic AID Groups: ");
        for (NQAidGroup nqAidGroup : mDynamicNQAidGroups.values()) {
            out.append(nqAidGroup.toString());
        }
        return out.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NQApduServiceInfo)) return false;
        NQApduServiceInfo thatService = (NQApduServiceInfo) o;

        return thatService.getComponent().equals(this.getComponent());
    }

    @Override
    public int hashCode() {
        return getComponent().hashCode();
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        mService.writeToParcel(dest, flags);
        dest.writeString(mDescription);
        dest.writeInt(mOnHost ? 1 : 0);
        dest.writeInt(mStaticNQAidGroups.size());
        if (mStaticNQAidGroups.size() > 0) {
            dest.writeTypedList(new ArrayList<NQAidGroup>(mStaticNQAidGroups.values()));
        }
        dest.writeInt(mDynamicNQAidGroups.size());
        if (mDynamicNQAidGroups.size() > 0) {
            dest.writeTypedList(new ArrayList<NQAidGroup>(mDynamicNQAidGroups.values()));
        }
        dest.writeInt(mRequiresDeviceUnlock ? 1 : 0);
        dest.writeInt(mBannerResourceId);
        dest.writeInt(mUid);
        dest.writeString(mSettingsActivityName);
        mSeExtension.writeToParcel(dest, flags);

        dest.writeInt(mNfcid2Groups.size());
        if (mNfcid2Groups.size() > 0) {
            dest.writeTypedList(mNfcid2Groups);
        }

        if(mBanner != null) {
            Bitmap bitmap = (Bitmap)((BitmapDrawable) mBanner).getBitmap();
            dest.writeParcelable(bitmap, flags);
        } else {
            dest.writeParcelable(null, flags);
        }
        dest.writeInt(mModifiable ? 1 : 0);
        dest.writeInt(mServiceState);
    };

    public static final Parcelable.Creator<NQApduServiceInfo> CREATOR =
            new Parcelable.Creator<NQApduServiceInfo>() {
        @Override
        public NQApduServiceInfo createFromParcel(Parcel source) {
            ResolveInfo info = ResolveInfo.CREATOR.createFromParcel(source);
            String description = source.readString();
            boolean onHost = source.readInt() != 0;
            ArrayList<NQAidGroup> staticNQAidGroups = new ArrayList<NQAidGroup>();
            int numStaticGroups = source.readInt();
            if (numStaticGroups > 0) {
                source.readTypedList(staticNQAidGroups, NQAidGroup.CREATOR);
            }
            ArrayList<NQAidGroup> dynamicNQAidGroups = new ArrayList<NQAidGroup>();
            int numDynamicGroups = source.readInt();
            if (numDynamicGroups > 0) {
                source.readTypedList(dynamicNQAidGroups, NQAidGroup.CREATOR);
            }
            boolean requiresUnlock = source.readInt() != 0;
            int bannerResource = source.readInt();
            int uid = source.readInt();
            String settingsActivityName = source.readString();
            ESeInfo seExtension = ESeInfo.CREATOR.createFromParcel(source);

            ArrayList<Nfcid2Group> nfcid2Groups = new ArrayList<Nfcid2Group>();
            int numGroups = source.readInt();
            if (numGroups > 0) {
                source.readTypedList(nfcid2Groups, Nfcid2Group.CREATOR);
            }
            Drawable drawable = null;
            if(getClass().getClassLoader() != null) {
                Bitmap bitmap = (Bitmap) source.readParcelable(getClass().getClassLoader());
                if(bitmap != null){
                    drawable = new BitmapDrawable(bitmap);
                    bannerResource = -1;
                }
            }
            boolean modifiable = source.readInt() != 0;
            NQApduServiceInfo service = new NQApduServiceInfo(info, onHost, description, staticNQAidGroups,
                    dynamicNQAidGroups, requiresUnlock, bannerResource, uid,
                    settingsActivityName, seExtension, nfcid2Groups, drawable,modifiable);
            service.setServiceState(CardEmulation.CATEGORY_OTHER, source.readInt());
            return service;
        }

        @Override
        public NQApduServiceInfo[] newArray(int size) {
            return new NQApduServiceInfo[size];
        }
    };

    public boolean isServiceEnabled(String category) {
        if(category != CardEmulation.CATEGORY_OTHER) {
            return true;
        }

        if((mServiceState ==  NxpConstants.SERVICE_STATE_ENABLED) || (mServiceState ==  NxpConstants.SERVICE_STATE_DISABLING)){
            return true;
        }else{ /*SERVICE_STATE_DISABLED or SERVICE_STATE_ENABLING*/
            return false;
        }
    }

    /**
     * This method is invoked before the service is commited to routing table.
     * mServiceState is previous state of the service, and,
     * user is now requesting to enable/disable (using flagEnable) this service
     * before committing all the services to routing table.
     * @param flagEnable To Enable/Disable the service.
     *        FALSE Disable service
     *        TRUE Enable service
     */
    public void enableService(String category ,boolean flagEnable) {
        if(category != CardEmulation.CATEGORY_OTHER) {
            return;
        }
        Log.d(TAG, "setServiceState:Description:" + mDescription + ":InternalState:" + mServiceState + ":flagEnable:"+ flagEnable);
        if(((mServiceState == NxpConstants.SERVICE_STATE_ENABLED) &&    (flagEnable == true )) ||
           ((mServiceState ==  NxpConstants.SERVICE_STATE_DISABLED) &&  (flagEnable == false)) ||
           ((mServiceState ==  NxpConstants.SERVICE_STATE_DISABLING) && (flagEnable == false)) ||
           ((mServiceState ==  NxpConstants.SERVICE_STATE_ENABLING) &&  (flagEnable == true ))){
            /*No change in state*/
            return;
        }
        else if((mServiceState ==  NxpConstants.SERVICE_STATE_ENABLED) && (flagEnable == false)){
            mServiceState =  NxpConstants.SERVICE_STATE_DISABLING;
        }
        else if((mServiceState ==  NxpConstants.SERVICE_STATE_DISABLED) && (flagEnable == true)){
            mServiceState =  NxpConstants.SERVICE_STATE_ENABLING;
        }
        else if((mServiceState ==  NxpConstants.SERVICE_STATE_DISABLING) && (flagEnable == true)){
            mServiceState =  NxpConstants.SERVICE_STATE_ENABLED;
        }
        else if((mServiceState ==  NxpConstants.SERVICE_STATE_ENABLING) && (flagEnable == false)){
            mServiceState =  NxpConstants.SERVICE_STATE_DISABLED;
        }
    }

    public int getServiceState(String category) {
        if(category != CardEmulation.CATEGORY_OTHER) {
            return NxpConstants.SERVICE_STATE_ENABLED;
        }

        return mServiceState;
    }

    public int setServiceState(String category ,int state) {
        if(category != CardEmulation.CATEGORY_OTHER) {
            return NxpConstants.SERVICE_STATE_ENABLED;
        }

        mServiceState = state;
        return mServiceState;
    }

    /**
     * Updates the state of the service based on the commit status
     * This method needs to be invoked after current service is pushed for the commit to routing table
     * @param commitStatus Result of the commit.
     *        FALSE if the commit failed. Reason for ex: there was an overflow of routing table
     *        TRUE if the commit was successful
     */
    public void updateServiceCommitStatus(String category ,boolean commitStatus) {
        if(category != CardEmulation.CATEGORY_OTHER) {
            return;
        }
        Log.d(TAG, "updateServiceCommitStatus:Description:" + mDescription + ":InternalState:" + mServiceState + ":commitStatus:"+ commitStatus);
        if(commitStatus){
            /*Commit was successful and all newly added services were registered,
             * disabled applications were removed/unregistered from routing entries*/
            if(mServiceState ==  NxpConstants.SERVICE_STATE_DISABLING){
                mServiceState =  NxpConstants.SERVICE_STATE_DISABLED;
            }
            else if(mServiceState == NxpConstants.SERVICE_STATE_ENABLING){
                mServiceState = NxpConstants.SERVICE_STATE_ENABLED;
            }

        }else{
            /*Commit failed and all newly added services were not registered successfully.
             * disabled applications were not successfully disabled*/
            if(mServiceState ==  NxpConstants.SERVICE_STATE_DISABLING){
                mServiceState =  NxpConstants.SERVICE_STATE_ENABLED;
            }
            else if(mServiceState ==  NxpConstants.SERVICE_STATE_ENABLING){
                mServiceState =  NxpConstants.SERVICE_STATE_DISABLED;
            }
        }
    }

    static String serviceStateToString(int state) {
        switch (state) {
            case NxpConstants.SERVICE_STATE_DISABLED:
                return "DISABLED";
            case NxpConstants.SERVICE_STATE_ENABLED:
                return "ENABLED";
            case NxpConstants.SERVICE_STATE_ENABLING:
                return "ENABLING";
            case NxpConstants.SERVICE_STATE_DISABLING:
                return "DISABLING";
            default:
                return "UNKNOWN";
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("    " + getComponent() +
                " (Description: " + getDescription() + ")");
        pw.println("    Static AID groups:");
        for (NQAidGroup group : mStaticNQAidGroups.values()) {
            pw.println("        Category: " + group.getCategory());
            for (String aid : group.getAids()) {
                pw.println("            AID: " + aid);
            }
        }
        pw.println("    Dynamic AID groups:");
        for (NQAidGroup group : mDynamicNQAidGroups.values()) {
            pw.println("        Category: " + group.getCategory());
            for (String aid : group.getAids()) {
                pw.println("            AID: " + aid);
            }
        }
        pw.println("    Settings Activity: " + mSettingsActivityName);
        pw.println("    Routing Destination: " + (mOnHost ? "host" : "secure element"));
        if (hasCategory(CardEmulation.CATEGORY_OTHER)) {
            pw.println("    Service State: " + serviceStateToString(mServiceState));
        }
    }

    public static class Nfcid2Group implements Parcelable {
        final ArrayList<String> nfcid2s;
        final String category;
        final String description;
        final ArrayList<String> syscode;
        final ArrayList<String> optparam;

        Nfcid2Group(ArrayList<String> nfcid2s, ArrayList<String> syscode, ArrayList<String> optparam, String category, String description) {
            this.nfcid2s = nfcid2s;
            this.category = category;
            this.description = description;
            this.syscode = syscode;
            this.optparam = optparam;
        }

        Nfcid2Group(String category, String description) {
            this.nfcid2s = new ArrayList<String>();
            this.syscode = new ArrayList<String>();
            this.optparam = new ArrayList<String>();
            this.category = category;
            this.description = description;
        }

        public String getCategory() {
            return category;
        }

        public ArrayList<String> getNfcid2s() {
            return nfcid2s;
        }

        public String getSyscodeForNfcid2(String nfcid2) {
            int idx = nfcid2s.indexOf(nfcid2);
            if(idx != -1)
                return syscode.get(idx);
            else
                return "";
        }

        public String getOptparamForNfcid2(String nfcid2) {
            int idx = nfcid2s.indexOf(nfcid2);
            if(idx != -1)
                return optparam.get(idx);
            else
                return "";
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder("Category: " + category +
                    ", description: " + description + ", AIDs:");
            for (String nfcid2 : nfcid2s) {
                out.append(nfcid2);
                out.append(", ");
            }
            return out.toString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(category);
            dest.writeString(description);

            dest.writeInt(syscode.size());
            if (syscode.size() > 0) {
                dest.writeStringList(syscode);
            }

            dest.writeInt(optparam.size());
            if (optparam.size() > 0) {
                dest.writeStringList(optparam);
            }

            dest.writeInt(nfcid2s.size());
            if (nfcid2s.size() > 0) {
                dest.writeStringList(nfcid2s);
            }
        }

        public static final Parcelable.Creator<NQApduServiceInfo.Nfcid2Group> CREATOR =
                new Parcelable.Creator<NQApduServiceInfo.Nfcid2Group>() {

            @Override
            public Nfcid2Group createFromParcel(Parcel source) {
                String category = source.readString();
                String description = source.readString();

                int syscodelistSize = source.readInt();
                ArrayList<String> syscodeList = new ArrayList<String>();
                if (syscodelistSize > 0) {
                    source.readStringList(syscodeList);
                }

                int optparamlistSize = source.readInt();
                ArrayList<String> optparamList = new ArrayList<String>();
                if (optparamlistSize > 0) {
                    source.readStringList(optparamList);
                }

                int nfcid2listSize = source.readInt();
                ArrayList<String> nfcid2List = new ArrayList<String>();
                if (nfcid2listSize > 0) {
                    source.readStringList(nfcid2List);
                }
                return new Nfcid2Group(nfcid2List, syscodeList, optparamList, category, description);
            }

            @Override
            public Nfcid2Group[] newArray(int size) {
                return new Nfcid2Group[size];
            }
        };
    }

    public static class ESeInfo implements Parcelable {
        final int seId;
        final int powerState;

        public ESeInfo(int seId, int powerState) {
            this.seId = seId;
            this.powerState = powerState;
        }

        public int getSeId() {
            return seId;
        }

        public int getPowerState() {
            return powerState;
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder("seId: " + seId +
                      ",Power state: [switchOn: " +
                      ((powerState & POWER_STATE_SWITCH_ON) !=0) +
                      ",switchOff: " + ((powerState & POWER_STATE_SWITCH_OFF) !=0) +
                      ",batteryOff: " + ((powerState & POWER_STATE_BATTERY_OFF) !=0) + "]");
            return out.toString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(seId);
            dest.writeInt(powerState);
        }

        public static final Parcelable.Creator<NQApduServiceInfo.ESeInfo> CREATOR =
                new Parcelable.Creator<NQApduServiceInfo.ESeInfo>() {

            @Override
            public ESeInfo createFromParcel(Parcel source) {
                int seId = source.readInt();
                int powerState = source.readInt();
                return new ESeInfo(seId, powerState);
            }

            @Override
            public ESeInfo[] newArray(int size) {
                return new ESeInfo[size];
            }
        };
    }

    public static class FelicaInfo implements Parcelable {
        final String felicaId;
        final String optParams;

        public FelicaInfo(String felica_id, String opt_params) {
            this.felicaId = felica_id;
            this.optParams = opt_params;
        }

        public String getFelicaId() {
            return felicaId;
        }

        public String getOptParams() {
            return optParams;
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder("felica id: " + felicaId +
                    ",optional params: " + optParams);
            return out.toString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(felicaId);
            dest.writeString(optParams);
        }

        public static final Parcelable.Creator<NQApduServiceInfo.FelicaInfo> CREATOR =
                new Parcelable.Creator<NQApduServiceInfo.FelicaInfo>() {

            @Override
            public FelicaInfo createFromParcel(Parcel source) {
                String felicaID = source.readString();
                String optParam = source.readString();
                return new FelicaInfo(felicaID, optParam);
            }

            @Override
            public FelicaInfo[] newArray(int size) {
                return new FelicaInfo[size];
            }
        };
    }
}
