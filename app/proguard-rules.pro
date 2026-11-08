# JNI uses static registration: C++ looks up Java_dev_hien_... by name
# (core/playback/src/main/cpp/jni_bridge.cpp), so names must survive R8.
-keepclasseswithmembernames class dev.hien.bitstream.core.playback.native.** {
    native <methods>;
}
