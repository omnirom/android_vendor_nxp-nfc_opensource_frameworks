/*
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2015 NXP Semiconductors
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
package com.nxp.nfc.gsma.internal;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.Random;
import android.nfc.NfcAdapter;
import com.nxp.nfc.NxpNfcAdapter;
import android.annotation.SystemApi;
import android.util.Log;
import android.nfc.cardemulation.NQAidGroup;
import android.nfc.cardemulation.AidGroup;
import android.nfc.cardemulation.NQApduServiceInfo;
import android.nfc.cardemulation.NQApduServiceInfo.ESeInfo;
import android.os.UserHandle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import com.nxp.nfc.gsma.internal.INxpNfcController;
import com.nxp.nfc.NxpConstants;

public class NxpNfcController {

    public static final int TECHNOLOGY_NFC_A=0x01;
    public static final int TECHNOLOGY_NFC_B=0x02;
    public static final int TECHNOLOGY_NFC_F=0x04;
    public static final int PROTOCOL_ISO_DEP=0x10;
    private static final int MW_PROTOCOL_MASK_ISO_DEP = 0x08;

    static final String TAG = "NxpNfcControllerFramework";
    static final Boolean DBG = true;

    /** Battery of the handset is in "Operational" mode*/
    public static final int BATTERY_OPERATIONAL_STATE=0x01;
    /** Any battery power levels*/
    public static final int BATTERY_ALL_STATES=0x02;

    /** Screen is "ON" (not in "Screen Off" mode) and locked*/
    public static final int SCREEN_ON_AND_LOCKED_MODE=0x01;
    /** Any screen mode*/
    public static final int SCREEN_ALL_MODES=0x02;

    private Context mContext;
    private NfcAdapter mNfcAdapter = null;
    private NxpNfcAdapter mNxpNfcAdapter = null;
    private INxpNfcController mNfcControllerService = null;
    private ESeInfo seExtension;
    private boolean mEnable = false;
    private boolean mState = false;
    private boolean mDialogBoxFlag = false;
    private NxpNfcController.NxpCallbacks mCallBack = null;

    // Map between SE name and NQApduServiceInfo
    private final HashMap<String, NQApduServiceInfo> mSeNameApduService =  new HashMap<String, NQApduServiceInfo>();//Maps.newHashMap();

    public static interface NxpCallbacks {
        /**
         * Called when process for enabling the NFC Controller is finished.
         */
        public abstract void onNxpEnableNfcController(boolean success);

    }

    // For QC
    public static interface Callbacks {
        /**
         * Called when process for enabling the NFC Controller is finished.
         * @hide
         */
        public void onGetOffHostService(boolean isLast, boolean isDefault,
                String description, String seName,int bannerResId,
                List<String> dynamicAidGroupDescriptions,
                List<android.nfc.cardemulation.AidGroup> dynamicAidGroups);

    }

    private final BroadcastReceiver mOwnerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            int state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE,0);
            Log.d(TAG,"onReceive: action: " + action + "mState: "+ mState);
            if((state == NfcAdapter.STATE_ON)  && (mState == true) && (mDialogBoxFlag == true)) {
                mEnable = true;
                mCallBack.onNxpEnableNfcController(true);
                mDialogBoxFlag = false;
                mState = false;
                mContext.unregisterReceiver(mOwnerReceiver);
                mContext.unregisterReceiver(mReceiver);
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(NxpConstants.ACTION_GSMA_ENABLE_SET_FLAG)) {
                mState = intent.getExtras().getBoolean("ENABLE_STATE");
            }
            if(mState == false) {
                mCallBack.onNxpEnableNfcController(false);
                mContext.unregisterReceiver(mOwnerReceiver);
                mContext.unregisterReceiver(mReceiver);
            } else {
                mDialogBoxFlag = true;
            }
        }
    };

    public NxpNfcController() {}

    public NxpNfcController(Context context) {
        mContext = context;
        if(mNfcAdapter == null)
            mNfcAdapter = NfcAdapter.getNfcAdapter(mContext);
        if((mNxpNfcAdapter == null) && (mNfcAdapter != null))
            mNxpNfcAdapter = NxpNfcAdapter.getNxpNfcAdapter(mNfcAdapter);

        if(mNfcControllerService == null) {
            mNfcControllerService = mNxpNfcAdapter.getNxpNfcControllerInterface();
        }
    }

    /**
     * Check if the NFC Controller is enabled or disabled.
     * return true,if the NFC adapter is enabled and false otherwise
     */
    public boolean isNxpNfcEnabled() {
        return mNfcAdapter.isEnabled ();
    }

    /**
     * Asks the system to enable the NFC Controller.
     */
    public void enableNxpNfcController(NxpNfcController.NxpCallbacks cb) {

        mCallBack = cb;
        IntentFilter ownerFilter = new IntentFilter();
        ownerFilter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        mContext.registerReceiver(mOwnerReceiver, ownerFilter);

        IntentFilter filter = new IntentFilter();
        filter.addAction(NxpConstants.ACTION_GSMA_ENABLE_SET_FLAG);
        mContext.registerReceiver(mReceiver, filter);

        // To Enable NfC
        Intent enableNfc = new Intent();
        enableNfc.setAction(NxpConstants.ACTION_GSMA_ENABLE_NFC);
        mContext.sendBroadcast(enableNfc);
    }

    /**
     * Converting from apdu service to off host service.
     * return Off-Host service object
     */
    private NxpOffHostService ConvertApduServiceToOffHostService(PackageManager pm, NQApduServiceInfo apduService) {
        NxpOffHostService mService;
        int seId=0;
        String sEname =null;
        ResolveInfo resolveInfo = apduService.getResolveInfo();
        String description = apduService.getDescription();
        seId = apduService.getSEInfo().getSeId();
        if (NxpConstants.UICC_ID_TYPE == seId) {
            sEname = "SIM1";
        } else if (NxpConstants.UICC2_ID_TYPE == seId) {
            sEname = "SIM2";
        } else if (NxpConstants.SMART_MX_ID_TYPE == seId) {
            sEname = "eSE";
        } else {
            Log.e(TAG,"Wrong SE ID");
        }
        Drawable banner = null; //apduService.loadBanner(pm);
        boolean modifiable = apduService.getModifiable();
        int bannerId = apduService.getBannerId();
        banner = apduService.loadBanner(pm);
        int userId = apduService.getUid();
        List<String> ApduAids = apduService.getAids();
        mService =  new NxpOffHostService(userId,description, sEname, resolveInfo.serviceInfo.packageName,
                resolveInfo.serviceInfo.name, modifiable);
        if(modifiable) {
            for(android.nfc.cardemulation.NQAidGroup group : apduService.getDynamicNQAidGroups()) {
                mService.mNQAidGroupList.add(group);
            }
        } else {
            for(android.nfc.cardemulation.NQAidGroup group : apduService.getStaticNQAidGroups()) {
                mService.mNQAidGroupList.add(group);
            }
        }
        //mService.setBanner(banner);
        mService.setContext(mContext);
        mService.setBannerId(bannerId);
        mService.setBanner(banner);
        mService.setNxpNfcController(this);
        return mService;
    }

    /**
     * Converting from Off_Host service object to Apdu Service object
     * return APDU service Object
     */
    private NQApduServiceInfo ConvertOffhostServiceToApduService(NxpOffHostService mService, int userId, String pkg) {
        boolean onHost = false;
        String description = mService.getDescription();
        boolean modifiable = mService.getModifiable();
        ArrayList<android.nfc.cardemulation.NQAidGroup> staticNQAidGroups  = new ArrayList<NQAidGroup>();
        ArrayList<android.nfc.cardemulation.NQAidGroup> dynamicNQAidGroups = new ArrayList<NQAidGroup>();
        dynamicNQAidGroups.addAll(mService.mNQAidGroupList);
        boolean requiresUnlock = false;
        //Drawable DrawableResource = null;
        //mService.getBanner();
        Drawable DrawableResource = mService.getBanner();
        int seId = 0;
        String seName = mService.getLocation();
        int powerstate = -1;
        int bannerId = mService.mBannerId;
        /* creating Resolveinfo object */
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.applicationInfo = new ApplicationInfo();
        resolveInfo.serviceInfo.packageName = pkg;
        resolveInfo.serviceInfo.name = mService.getServiceName();
        if(seName != null) {
            if(seName.equals("SIM") || seName.equals("SIM1")) {
                seId = NxpConstants.UICC_ID_TYPE;
            } else if (seName.equals("SIM2")) {
                seId = NxpConstants.UICC2_ID_TYPE;
            } else if (seName.equals("eSE")) {
                seId = NxpConstants.SMART_MX_ID_TYPE;
            } else {
                Log.e(TAG,"wrong Se name");
            }
        }
        NQApduServiceInfo.ESeInfo mEseInfo = new NQApduServiceInfo.ESeInfo(seId,powerstate);
        return new NQApduServiceInfo(resolveInfo,onHost,description,staticNQAidGroups, dynamicNQAidGroups,
                requiresUnlock,bannerId,userId, "Fixme: NXP:<Activity Name>", mEseInfo, null, DrawableResource, modifiable);
    }

    /**
     * Delete Off-Host service from routing table
     * return true or false
     */
    public boolean deleteOffHostService(int userId, String packageName, NxpOffHostService service) {
        boolean result = false;
        NQApduServiceInfo apduService;
        apduService = ConvertOffhostServiceToApduService(service, userId, packageName);
        try {
            result = mNfcControllerService.deleteOffHostService(userId, packageName, apduService);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception:deleteOffHostService failed", e);
            result = false;
        }
        if(result != true) {
            Log.e(TAG, "GSMA: deleteOffHostService failed");
        }
        return result;
    }

    /**
     * Get the list Off-Host services
     * return off-Host service List
     */
    public ArrayList<NxpOffHostService> getOffHostServices(int userId, String packageName) {
        List<NQApduServiceInfo> apduServices = new ArrayList<NQApduServiceInfo>();
        ArrayList<NxpOffHostService> mService = new ArrayList<NxpOffHostService>();
        PackageManager pm = mContext.getPackageManager();
        try {
            apduServices = mNfcControllerService.getOffHostServices(userId, packageName);
        } catch (RemoteException e) {
            Log.e(TAG, "getOffHostServices failed", e);
            return null;
        }
        for(NQApduServiceInfo service: apduServices) {
            mService.add(ConvertApduServiceToOffHostService(pm, service));
        }
        return mService;
    }

    /**
     * Get the Default Off-Host services
     * return default off-Host service
     */
    public NxpOffHostService getDefaultOffHostService(int userId, String packageName) {
        NQApduServiceInfo apduService;
        NxpOffHostService mService;
        PackageManager pm = mContext.getPackageManager();
        try {
            apduService = mNfcControllerService.getDefaultOffHostService(userId, packageName);
        } catch (RemoteException e) {
            Log.e(TAG, "getDefaultOffHostService failed", e);
            return null;
        }
        if(apduService != null) {
            mService = ConvertApduServiceToOffHostService(pm, apduService);
            return mService;
        }
        Log.e(TAG, "getDefaultOffHostService: Service is NULL");
        return null;
    }

    /**
     * add the Off-Host service to routing tableh
     * return true
     */
    public boolean commitOffHostService(int userId, String packageName, NxpOffHostService service) {
        boolean result = false;
        NQApduServiceInfo newService;
        String serviceName = service.getServiceName();
        newService = ConvertOffhostServiceToApduService(service, userId, packageName);
        try {
            if(mNfcControllerService != null) {
                result = mNfcControllerService.commitOffHostService(userId, packageName, serviceName, newService);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Exception:commitOffHostService failed", e);
            result = false;
        }
        if(result != true) {
            Log.e(TAG, "GSMA: commitOffHostService Failed");
        }
        return result;
    }


    /**
     * add the Off-Host service to routing tableh
     * return true
     * @hide
     */
    public boolean commitOffHostService(String packageName, String seName, String description,
            int bannerResId, int uid, List<String> aidGroupDescriptions,
            List<android.nfc.cardemulation.AidGroup> aidGroups) {

        boolean result = false;
        int userId = UserHandle.myUserId();
        NQApduServiceInfo service = null;
        boolean onHost = false;
        ArrayList<android.nfc.cardemulation.NQAidGroup> staticNQAidGroups  = new ArrayList<NQAidGroup>();
        ArrayList<android.nfc.cardemulation.NQAidGroup> dynamicNQAidGroups = new ArrayList<NQAidGroup>();
        int i = 0;
        for(android.nfc.cardemulation.AidGroup aidg : aidGroups) {
            android.nfc.cardemulation.NQAidGroup nqAidg = new NQAidGroup(aidg.getAids(), aidg.getCategory(), aidGroupDescriptions.get(i++));
            dynamicNQAidGroups.add(nqAidg);
        }
        if(DBG) Log.d(TAG, "aidGroups.size() " + aidGroups.size());
        boolean requiresUnlock = false;
        Drawable DrawableResource = null;
        int seId = 0;
        int powerstate = -1;
        boolean modifiable = true;

        /* creating Resolveinfo object */
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.applicationInfo = new ApplicationInfo();
        resolveInfo.serviceInfo.packageName = packageName;
        resolveInfo.serviceInfo.name = seName;

        //Temp for SE conversion
        String secureElement = null;
        if((seName.equals("SIM")) || (seName.equals("SIM1"))) {
            secureElement = NxpConstants.UICC_ID;
        } else if (seName.equals("SIM2")){
            secureElement = NxpConstants.UICC2_ID;
        } else if ((seName.equals("eSE1")) || (seName.equals("eSE"))){
            secureElement = NxpConstants.SMART_MX_ID;
        } else {
            Log.e(TAG,"wrong Se name");
        }

        if(secureElement.equals(NxpConstants.UICC_ID)) {
            seId = NxpConstants.UICC_ID_TYPE;
        } else if (secureElement.equals(NxpConstants.UICC2_ID)) {
            seId = NxpConstants.UICC2_ID_TYPE;
        } else if (secureElement.equals(NxpConstants.SMART_MX_ID)) {
            seId = NxpConstants.SMART_MX_ID_TYPE;
        } else if (secureElement.equals(NxpConstants.HOST_ID)) {
            seId = NxpConstants.HOST_ID_TYPE;
        } else {
            Log.e(TAG,"wrong Se name");
        }
        NQApduServiceInfo.ESeInfo mEseInfo = new NQApduServiceInfo.ESeInfo(seId,powerstate);
        NQApduServiceInfo newService = new NQApduServiceInfo(resolveInfo, onHost, description,
                staticNQAidGroups, dynamicNQAidGroups, requiresUnlock, bannerResId, userId,
                "Fixme: NXP:<Activity Name>", mEseInfo, null, DrawableResource, modifiable);

        mSeNameApduService.put(seName, newService);

        try {
            if(mNfcControllerService != null) {
                result = mNfcControllerService.commitOffHostService(userId, packageName, seName, newService);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Exception:commitOffHostService failed " + e.getMessage());
            result = false;
        }
        if(result != true) {
            Log.w(TAG, "GSMA: commitOffHostService Failed");
        }
        return result;
    }

    /**
     * Delete Off-Host service from routing table
     * return true or false
     */
    public boolean deleteOffHostService(String packageName, String seName) {
        Log.d(TAG, "deleteOffHostService: " + packageName + " " + seName);
        boolean result = false;
        int userId = UserHandle.myUserId();

        NQApduServiceInfo nqapdu = mSeNameApduService.get(seName);
        if(nqapdu == null) {
            Log.d(TAG, "delteOffHostService() not found");
            return result;
        } else {
            Log.d(TAG, "deleteOffHostService() found " + nqapdu.toString());
        }

        try {
            result = mNfcControllerService.deleteOffHostService(userId, packageName, nqapdu);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception:deleteOffHostService failed", e);
        }
        if(result != true) {
            Log.w(TAG, "GSMA: deleteOffHostService failed");
            return result;
        }
        return result;
    }

    /**
     * Get the list Off-Host services
     * return off-Host service List
     */
    public boolean getOffHostServices(String packageName, Callbacks callbacks) {

        int userId = UserHandle.myUserId();
        boolean isLast = false;
        boolean isDefault = false;
        String seName = null;
        int seId=0;

        List<NQApduServiceInfo> apduServices = new ArrayList<NQApduServiceInfo>();
        try {
            apduServices = mNfcControllerService.getOffHostServices(userId, packageName);
            NQApduServiceInfo defaultApduService = mNfcControllerService.getDefaultOffHostService(userId, packageName);

            for(int i =0; i< apduServices.size(); i++) {

                if( i == apduServices.size() - 1 ) {
                    isLast = true;
                }
                if( defaultApduService != null && apduServices.get(i).equals(defaultApduService)) {
                    isDefault = true;
                }

                seId = apduServices.get(i).getSEInfo().getSeId();
                if( seId == -1) {
                    Log.e(TAG,"Wrong SE ID (-1)");
                    continue;
                }
                Log.e(TAG, " SE ID: " + seId);
                if (NxpConstants.UICC_ID_TYPE == seId) {
                    seName = "SIM";
                } else if (NxpConstants.UICC2_ID_TYPE == seId) {
                    seName = "SIM";
                } else {
                    Log.e(TAG,"Wrong SE ID");
                }

                if(DBG) Log.d(TAG, "getOffHostServices() : seName: " +seName + " apduServices.get(" + i + ").toString(): "
                        + apduServices.get(i).toString());

                ArrayList<String> groupDescription = new ArrayList<String>();
                for (NQAidGroup nqaidGroup : apduServices.get(i).getNQAidGroups()) {
                    groupDescription.add(nqaidGroup.getDescription());
                }

                callbacks.onGetOffHostService(isLast, isDefault, apduServices.get(i).getDescription(), seName, apduServices.get(i).getBannerId(),
                        groupDescription,
                        apduServices.get(i).getAidGroups());

                mSeNameApduService.put(seName, apduServices.get(i));

            }
        } catch (RemoteException e) {
            Log.e(TAG, "getOffHostServices failed", e);
            return false;
        }

        return true;
    }

    /**
     * Get the Default Off-Host services
     * return default off-Host service
     */
    public boolean getDefaultOffHostService(String packageName, Callbacks callbacks) {

        Log.d(TAG, "getDefaultOffHostService: Enter");

        NQApduServiceInfo apduService;
        boolean isLast = true;
        boolean isDefault = true;
        int userId = UserHandle.myUserId();
        String seName = null;
        int seId=0;
        try {
            apduService = mNfcControllerService.getDefaultOffHostService(userId, packageName);
            seId = apduService.getSEInfo().getSeId();
	    Log.e(TAG," SE ID: " + seId);
            if (NxpConstants.UICC_ID_TYPE == seId) {
                seName = "SIM";
            } else if (NxpConstants.UICC2_ID_TYPE == seId) {
                seName = "SIM";
            } else {
                Log.e(TAG,"Wrong SE ID");
            }

            if(DBG) Log.d(TAG, "getDefaultOffHostService: seName: " + seName + " apduService.toString():" + apduService.toString());

            ArrayList<String> groupDescription = new ArrayList<String>();
            for (NQAidGroup nqaidGroup : apduService.getNQAidGroups()) {
                groupDescription.add(nqaidGroup.getDescription());
            }

            callbacks.onGetOffHostService(isLast, isDefault, apduService.getDescription(), seName, apduService.getBannerId(),
                    groupDescription,
                    apduService.getAidGroups());
        } catch (RemoteException e) {
            Log.e(TAG, "getDefaultOffHostService failed", e);
            return false;
        }

        Log.d(TAG, "getDefaultOffHostService: End");
        return true;
    }

    /**
     * To enable the the system to inform "transaction events" to any authorized/registered components
     * via BroadcastReceiver
     *
     */
    public void enableMultiReception(String seName, String packageName) {
        try {
            mNfcControllerService.enableMultiReception(packageName, seName);
        } catch (RemoteException e) {
            Log.e(TAG, "enableMultiReception failed", e);
            return;
        }
    }

    public boolean isStaticOffhostService(int userId, String packageName, NxpOffHostService service) {
        boolean isStatic = false;
        List<NQApduServiceInfo> nqapduServices = new ArrayList<NQApduServiceInfo>();

        try {
            nqapduServices = mNfcControllerService.getOffHostServices(userId, packageName);

            for(int i=0; i< nqapduServices.size(); i++) {
                NQApduServiceInfo sService = nqapduServices.get(i);
                if(sService.getModifiable() == false && service.getServiceName().compareTo((sService.getResolveInfo()).serviceInfo.name)==0){
                    isStatic = true;
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "getOffHostServices failed", e);
            isStatic = true;
        }
        return isStatic;
    }
}
