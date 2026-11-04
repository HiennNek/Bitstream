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

JNIEXPORT jint JNICALL
Java_dev_hien_bitstream_core_playback_native_NativeAudio_nativePlayFile(
    JNIEnv*, jobject, jlong handle, jint fd, jlong startFrame, jint sampleRate,
    jint bitDepth, jint channels, jint subslotBytes) {
    return static_cast<jint>(bitstream_usb_play_file(
        static_cast<int64_t>(handle), fd, static_cast<int64_t>(startFrame), sampleRate,
        bitDepth, channels, subslotBytes));
}

JNIEXPORT jint JNICALL
Java_dev_hien_bitstream_core_playback_native_NativeAudio_nativeEnqueueNext(
    JNIEnv*, jobject, jlong handle, jint fd, jint sampleRate, jint bitDepth, jint channels,
    jint subslotBytes) {
    return static_cast<jint>(bitstream_usb_enqueue_next(
        static_cast<int64_t>(handle), fd, sampleRate, bitDepth, channels, subslotBytes));
}

JNIEXPORT jlongArray JNICALL
Java_dev_hien_bitstream_core_playback_native_NativeAudio_nativeStats(
    JNIEnv* env, jobject, jlong handle) {
    int64_t values[5] = {};
    bitstream_usb_stats(static_cast<int64_t>(handle), values);
    jlongArray result = env->NewLongArray(5);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, 5, values);
    }
    return result;
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
