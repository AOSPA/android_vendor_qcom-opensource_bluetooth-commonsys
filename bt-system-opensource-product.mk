#BT
ifeq ($(BOARD_HAVE_BLUETOOTH_QCOM),true)
PRODUCT_PACKAGES += Bluetooth

ifneq ($(TARGET_BOARD_TYPE),auto)
# Set supported Bluetooth profiles to enabled
PRODUCT_PRODUCT_PROPERTIES += \
    bluetooth.profile.a2dp.source.enabled=true \
    bluetooth.profile.avrcp.target.enabled=true \
    bluetooth.profile.avrcp.controller.enabled=true \
    bluetooth.profile.hfp.ag.enabled=true \
    bluetooth.profile.gatt.enabled=true \
    bluetooth.profile.hid.host.enabled=true \
    bluetooth.profile.hid.device.enabled=true \
    bluetooth.profile.map.server.enabled=true \
    bluetooth.profile.opp.enabled=true \
    bluetooth.profile.pan.nap.enabled=true \
    bluetooth.profile.pan.panu.enabled=true \
    bluetooth.profile.pbap.server.enabled=true \
    bluetooth.profile.bas.client.enabled=true \
    bluetooth.device_id.vendor_id=0x001D

PRODUCT_SYSTEM_EXT_PROPERTIES += \
    bluetooth.profile.sap.server.enabled=true \
    bluetooth.profile.pbap.sim.enabled=true \
    bluetooth.hfp.codec_aptx_voice.enabled=true \
    bluetooth.hfp.swb.aptx.power_management.enabled=true \
    ro.bluetooth.leaudio_offload.supported=true \
    bluetooth.device.class_of_device=90,2,12 \
    persist.bluetooth.leaudio_offload.disabled=false \
    persist.bluetooth.leaudio.allow.multiple.context=false \
    persist.bluetooth.leaudio.bypass_allow_list=true \
    bluetooth.leaudio.dual_bidirection_swb.supported=true \
    persist.bluetooth.leaudio.notify.idle.during.call=true \
    persist.vendor.bluetooth.haltest=true

ifneq ($(TARGET_HAS_LOW_RAM), true)
PRODUCT_PACKAGES += Xpan
PRODUCT_PACKAGES += privapp-permission-xpan.xml
endif #TARGET_HAS_LOW_RAM

endif #TARGET_BOARD_TYPE
endif #BOARD_HAVE_BLUETOOTH_QCOM

#FM
ifeq ($(BOARD_HAVE_QCOM_FM), true)
ifeq ($(TARGET_FWK_SUPPORTS_FULL_VALUEADDS), true)
PRODUCT_PACKAGES += libqcomfm_jni
PRODUCT_PACKAGES += libfmjni
PRODUCT_PACKAGES += fm_helium
PRODUCT_PACKAGES += libfm-hci
PRODUCT_PACKAGES += FM2
PRODUCT_PACKAGES += qcom.fmradio
PRODUCT_BOOT_JARS += qcom.fmradio
PRODUCT_PACKAGES += vendor.qti.hardware.fm@1.0
# system prop for fm
PRODUCT_PROPERTY_OVERRIDES += vendor.hw.fm.init=0
endif #TARGET_FWK_SUPPORTS_FULL_VALUEADDS
endif #BOARD_HAVE_QCOM_FM
