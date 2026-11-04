#include <jni.h>

#include "usb_audio.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_hien_bitstream_core_playback_native_NativeAudio_nativeOpen(
    JNIEnv*, jobject, jint fd, jint endpoint, jint interfaceNumber, jint altSetting,
    jint subslotBytes, jint channels, jint sampleRate, jint syncType,
    jint feedbackEndpoint, jint clockSourceId) {
    return static_cast<jlong>(bitstream_usb_open(
        fd, endpoint, interfaceNumber, altSetting, subslotBytes, channels, sampleRate,
        syncType, feedbackEndpoint, clockSourceId));
}

JNIEXPORT jint JNICALL
Java_dev_hien_bitstream_core_playback_native_NativeAudio_nativeStartSilence(
    JNIEnv*, jobject, jlong handle, jlong frames) {
    return static_cast<jint>(bitstream_usb_start_silence(
        static_cast<int64_t>(handle), static_cast<int64_t>(frames)));
}

JNIEXPORT void JNICALL
Java_dev_hien_bitstream_core_playback_native_NativeAudio_nativeStop(
    JNIEnv*, jobject, jlong handle) {
    bitstream_usb_stop(static_cast<int64_t>(handle));
}

JNIEXPORT void JNICALL
Java_dev_hien_bitstream_core_playback_native_NativeAudio_nativeClose(
    JNIEnv*, jobject, jlong handle) {
    bitstream_usb_close(static_cast<int64_t>(handle));
}

}
