LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_SRC_FILES := $(call all-java-files-under, java)
LOCAL_JAVA_LIBRARIES := com.nxp.nfc.nq
LOCAL_MODULE:= com.gsma.services.nfc
LOCAL_MODULE_TAGS := optional
LOCAL_REQUIRED_MODULES:= com.gsma.services.nfc.xml com.nxp.nfc.nq.xml
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_JAVA_LIBRARY)
# ====  permissions ========================
include $(CLEAR_VARS)

LOCAL_MODULE := com.gsma.services.nfc.xml
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
# Install to /system/etc/permissions
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)
