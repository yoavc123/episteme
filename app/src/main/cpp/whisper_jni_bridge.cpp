#include <jni.h>
#include <string>

#include "whisper.h"

namespace {

jclass findClass(JNIEnv * env, const char * name) {
    return env->FindClass(name);
}

jobject newNativeWord(JNIEnv * env, double start, double end, const std::string & word) {
    jclass cls = findClass(env, "com/aryan/reader/audio/NativeWhisperWord");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(DDLjava/lang/String;)V");
    jstring text = env->NewStringUTF(word.c_str());
    jobject result = env->NewObject(cls, ctor, start, end, text);
    env->DeleteLocalRef(text);
    return result;
}

jobject emptyList(JNIEnv * env) {
    jclass collections = findClass(env, "java/util/Collections");
    jmethodID emptyListMethod = env->GetStaticMethodID(collections, "emptyList", "()Ljava/util/List;");
    return env->CallStaticObjectMethod(collections, emptyListMethod);
}

jobject newNativeSegment(JNIEnv * env, double start, double end, const std::string & text, jobject words) {
    jclass cls = findClass(env, "com/aryan/reader/audio/NativeWhisperSegment");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(DDLjava/lang/String;Ljava/util/List;)V");
    jstring segmentText = env->NewStringUTF(text.c_str());
    jobject result = env->NewObject(cls, ctor, start, end, segmentText, words);
    env->DeleteLocalRef(segmentText);
    return result;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_aryan_reader_audio_WhisperNativeBridge_loadModel(JNIEnv * env, jobject, jstring modelPath) {
    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context * ctx = whisper_init_from_file(path);
    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_aryan_reader_audio_WhisperNativeBridge_freeModel(JNIEnv *, jobject, jlong handle) {
    auto * ctx = reinterpret_cast<whisper_context *>(handle);
    whisper_free(ctx);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_aryan_reader_audio_WhisperNativeBridge_transcribe(
        JNIEnv * env,
        jobject,
        jlong handle,
        jfloatArray pcm,
        jint sampleRate,
        jstring) {
    if (handle == 0 || pcm == nullptr || sampleRate <= 0) {
        return emptyList(env);
    }

    jsize sampleCount = env->GetArrayLength(pcm);
    double durationSeconds = static_cast<double>(sampleCount) / static_cast<double>(sampleRate);
    if (durationSeconds <= 0.0) {
        return emptyList(env);
    }

    jclass arrayListClass = findClass(env, "java/util/ArrayList");
    jmethodID arrayListCtor = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID addMethod = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jobject segments = env->NewObject(arrayListClass, arrayListCtor);

    jobject words = emptyList(env);
    jobject segment = newNativeSegment(env, 0.0, durationSeconds, "", words);
    env->CallBooleanMethod(segments, addMethod, segment);
    env->DeleteLocalRef(segment);
    return segments;
}
