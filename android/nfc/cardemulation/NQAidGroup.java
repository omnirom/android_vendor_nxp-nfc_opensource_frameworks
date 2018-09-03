/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2015 NXP Semiconductors
 * The original Work has been changed by NXP Semiconductors.
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
 * limitations under the License.
 */

package android.nfc.cardemulation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.lang.Class;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import java.lang.reflect.Field;

/**
 * The NQAidGroup class represents a group of Application Identifiers (AIDs).
 *
 * <p>The format of AIDs is defined in the ISO/IEC 7816-4 specification. This class
 * requires the AIDs to be input as a hexadecimal string, with an even amount of
 * hexadecimal characters, e.g. "F014811481".
 *
 * Note: While this class extends AidGroup, it has no access to protected fields/methods of the super class
 * as it is built in a different compile unit.
 *
 * @hide
 */
public final class NQAidGroup extends AidGroup implements Parcelable {
    /**
     * The maximum number of AIDs that can be present in any one group.
     */

    static final String TAG = "NQAidGroup";
    final String nqdescription;


    /**
     * Creates a new NQAidGroup object.
     *
     * @param aids The list of AIDs present in the group
     * @param category The category of this group, e.g. {@link CardEmulation#CATEGORY_PAYMENT}
     */
    protected ArrayList<ApduPatternGroup> mStaticNQApduPatternGroups;
    public NQAidGroup(List<String> aids, String category, String description) {
        super(aids,category);
        this.nqdescription = description;
        this.mStaticNQApduPatternGroups = new ArrayList<ApduPatternGroup>();
    }

    /**
     * Creates a new NQAidGroup object.
     *
     * @param aids The list of AIDs present in the group
     * @param category The category of this group, e.g. {@link CardEmulation#CATEGORY_PAYMENT}
     */
    public NQAidGroup(List<String> aids, String category) {
        super(aids, category);
        this.nqdescription = null;
    }

   public NQAidGroup(String category, String description) {
        super(category,description);
        this.nqdescription = null;
   }

    public NQAidGroup(AidGroup aid) {
        this(aid.getAids(), aid.getCategory(), getDescription(aid));
    }

    /**
     * @return the decription of this AID group
     */
    static String getDescription(AidGroup aid) {
        Field[] fs = aid.getClass().getDeclaredFields();
        for(Field f : fs) {
            f.setAccessible(true);
        }
        return aid.description;
    }

    /**
     * Creats an AidGroup object to be serialized with same AIDs
     * and same category.
     *
     * @return An AidGroup object to be serialized via parcel
     */
    public AidGroup createAidGroup() {
        if(this.getAids() == null || this.getAids().isEmpty()){
          Log.d(TAG, "Empty aid group creation");
          return new AidGroup(this.getCategory(), this.getDescription());
        }
        Log.d(TAG, "Non Empty aid group creation");
        return new AidGroup(this.getAids(), this.getCategory());
    }

    public void addApduGroup(ApduPatternGroup apdu) {
        mStaticNQApduPatternGroups.add(apdu);
    }

    /**
     * Returns a consolidated list of APDU from the APDU groups
     * registered by this service.
     * @return List of APDU pattern registered by the service
     */
    public ArrayList<ApduPattern> getApduPatternList() {
        final ArrayList<ApduPattern> apdulist = new ArrayList<ApduPattern>();
        if(apdulist == null || mStaticNQApduPatternGroups == null){
          return null;
        }
        try
        {
          for (ApduPatternGroup group : mStaticNQApduPatternGroups) {
            if(group == null){
              continue;
            }
            for(ApduPattern apduPattern : group.getApduPattern()) {
              if(apduPattern == null){
                continue;
            }
              apdulist.add(apduPattern);
        }
    }
        }catch(NullPointerException e){
          e.printStackTrace();
        }
        return apdulist;
    }

    /**
      * @return the decription of this AID group.
      */
    public String getDescription() {
        return nqdescription;
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest,flags);
        if(nqdescription != null) {
            dest.writeString(nqdescription);
        } else {
            dest.writeString(null);
        }
    }

    public static final Parcelable.Creator<NQAidGroup> CREATOR =
            new Parcelable.Creator<NQAidGroup>() {

        @Override
        public NQAidGroup createFromParcel(Parcel source) {
            String category = source.readString();
            int listSize = source.readInt();
            ArrayList<String> aidList = new ArrayList<String>();
            if (listSize > 0) {
                source.readStringList(aidList);
            }
            String nqdescription = source.readString();
            if(aidList == null || aidList.size() == 0)
                return new NQAidGroup(category, nqdescription);
            else
                return new NQAidGroup(aidList, category, nqdescription);
        }

        @Override
        public NQAidGroup[] newArray(int size) {
            return new NQAidGroup[size];
        }
    };

    static public NQAidGroup createFromXml(XmlPullParser parser) throws XmlPullParserException, IOException {
        String category = null;
        String nqdescription = null;
        ArrayList<String> aids = new ArrayList<String>();
        NQAidGroup group = null;
        boolean inGroup = false;

        int eventType = parser.getEventType();
        int minDepth = parser.getDepth();
        while (eventType != XmlPullParser.END_DOCUMENT && parser.getDepth() >= minDepth) {
            String tagName = parser.getName();
            if (eventType == XmlPullParser.START_TAG) {
                if (tagName.equals("aid")) {
                    if (inGroup) {
                        String aid = parser.getAttributeValue(null, "value");
                        if (aid != null) {
                            aids.add(aid.toUpperCase());
                        }
                    } else {
                        Log.d(TAG, "Ignoring <aid> tag while not in group");
                    }
                } else if (tagName.equals("aid-group")) {
                    category = parser.getAttributeValue(null, "category");
                    nqdescription = parser.getAttributeValue(null, "description");
                    if (category == null) {
                        Log.e(TAG, "<aid-group> tag without valid category");
                        return null;
                    }
                    inGroup = true;
                } else {
                    Log.d(TAG, "Ignoring unexpected tag: " + tagName);
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (tagName.equals("aid-group") && inGroup) {
                    if (aids.size() > 0) {
                        group = new NQAidGroup(aids, category, nqdescription);
                    }
                    else {
                        group = new NQAidGroup(category, nqdescription);
                    }
                    break;
                }
            }
            eventType = parser.next();
        }
        return group;
    }

    public void writeAsXml(XmlSerializer out) throws IOException {
        out.startTag(null, "aid-group");
        out.attribute(null, "category", category);
        if(nqdescription != null)
            out.attribute(null, "description", nqdescription);
        for (String aid : aids) {
            out.startTag(null, "aid");
            out.attribute(null, "value", aid);
            out.endTag(null, "aid");
        }
        out.endTag(null, "aid-group");
    }

    public static class ApduPatternGroup implements Parcelable {
        public static final int MAX_NUM_APDU = 5;
        public static final String TAG = "ApduPatternGroup";

        protected String description;
        protected List<ApduPattern> apduList;

        public ApduPatternGroup(String description)
        {
            this.description = description;
            apduList = new ArrayList<ApduPattern>(MAX_NUM_APDU);
        }

        public void addApduPattern(ApduPattern apduPattern)
        {
            if(!containsApduPattern(apduPattern))
            {
                apduList.add(apduPattern);
            }
        }

        private boolean containsApduPattern(ApduPattern apduPattern)
        {
            boolean status = false;
            for(ApduPattern apdu : apduList)
            {
                if(apdu.getreferenceData().equalsIgnoreCase(apduPattern.getreferenceData()))
                {
                    status = true;
                    break;
                }
            }
            return status;
        }

        public List<ApduPattern> getApduPattern()
        {
            return apduList;
        }

        public String getDescription()
        {
            return description;
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder("APDU Pattern List");
            for (ApduPattern apdu : apduList) {
                out.append("apdu_data"+apdu.getreferenceData());
                out.append("apdu mask"+apdu.getMask());
            }
            return out.toString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(description);
            dest.writeInt(apduList.size());
            if (apduList.size() > 0) {
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<ApduPatternGroup> CREATOR =
                new Parcelable.Creator<ApduPatternGroup>() {

            @Override
            public ApduPatternGroup createFromParcel(Parcel source) {
                String description = source.readString();
                int listSize = source.readInt();
                ArrayList<ApduPattern> apduList = new ArrayList<ApduPattern>();
                ApduPatternGroup apduGroup = new ApduPatternGroup(description);
                if (listSize > 0) {
                }
                return apduGroup;
            }

            @Override
            public ApduPatternGroup[] newArray(int size) {
                return new ApduPatternGroup[size];
            }
        };
    }

    public class ApduPattern {
        private String reference_data;
        private String mask;
        private String description;
        public ApduPattern(String reference_data, String mask, String description)
        {
            this.reference_data = reference_data;
            this.mask = mask;
            this.description = description;
        }
        public String getreferenceData()
        {
            return reference_data;
        }
        public String getMask()
        {
           return mask;
        }
    }
}
