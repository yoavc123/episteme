#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <string>
#include <thread>
#include <vector>

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

jobject newArrayList(JNIEnv * env) {
    jclass arrayListClass = findClass(env, "java/util/ArrayList");
    jmethodID arrayListCtor = env->GetMethodID(arrayListClass, "<init>", "()V");
    return env->NewObject(arrayListClass, arrayListCtor);
}

void addToList(JNIEnv * env, jobject list, jobject item) {
    jclass listClass = findClass(env, "java/util/List");
    jmethodID addMethod = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
    env->CallBooleanMethod(list, addMethod, item);
}

jobject newNativeSegment(JNIEnv * env, double start, double end, const std::string & text, jobject words) {
    jclass cls = findClass(env, "com/aryan/reader/audio/NativeWhisperSegment");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(DDLjava/lang/String;Ljava/util/List;)V");
    jstring segmentText = env->NewStringUTF(text.c_str());
    jobject result = env->NewObject(cls, ctor, start, end, segmentText, words);
    env->DeleteLocalRef(segmentText);
    return result;
}

void throwJavaException(JNIEnv * env, const char * className, const char * message) {
    jclass cls = findClass(env, className);
    env->ThrowNew(cls, message);
}

double whisperTimeToSeconds(int64_t time) {
    return static_cast<double>(time) / 100.0;
}

std::string jstringToString(JNIEnv * env, jstring value) {
    if (value == nullptr) return "";
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return "";
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

int nativeThreadCount() {
    const unsigned int detected = std::thread::hardware_concurrency();
    return static_cast<int>(std::max(1u, std::min(4u, detected == 0 ? 2u : detected)));
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_aryan_reader_audio_WhisperNativeBridge_loadModel(JNIEnv * env, jobject, jstring modelPath) {
    if (modelPath == nullptr) return 0;
    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) return 0;
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context * ctx = whisper_init_from_file_with_params(path, params);
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
        jstring language) {
    if (handle == 0 || pcm == nullptr || sampleRate <= 0) {
        return emptyList(env);
    }

    if (sampleRate != 16000) {
        throwJavaException(env, "java/lang/IllegalArgumentException", "Whisper native bridge requires 16 kHz mono PCM.");
        return nullptr;
    }

    jsize sampleCount = env->GetArrayLength(pcm);
    if (sampleCount <= 0) {
        return emptyList(env);
    }

    auto * ctx = reinterpret_cast<whisper_context *>(handle);
    std::vector<float> samples(static_cast<size_t>(sampleCount));
    env->GetFloatArrayRegion(pcm, 0, sampleCount, samples.data());
    if (env->ExceptionCheck()) return nullptr;

    const std::string requestedLanguage = jstringToString(env, language);
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = nativeThreadCount();
    params.no_context = true;
    params.no_timestamps = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.token_timestamps = true;
    params.split_on_word = true;
    params.language = requestedLanguage.empty() ? "auto" : requestedLanguage.c_str();
    params.detect_language = requestedLanguage.empty() || requestedLanguage == "auto";

    const int result = whisper_full(ctx, params, samples.data(), sampleCount);
    if (result != 0) {
        throwJavaException(env, "java/lang/IllegalStateException", "Whisper native transcription failed.");
        return nullptr;
    }

    jobject segments = newArrayList(env);
    const int segmentCount = whisper_full_n_segments(ctx);
    for (int segmentIndex = 0; segmentIndex < segmentCount; ++segmentIndex) {
        const double segmentStart = whisperTimeToSeconds(whisper_full_get_segment_t0(ctx, segmentIndex));
        const double segmentEnd = whisperTimeToSeconds(whisper_full_get_segment_t1(ctx, segmentIndex));
        const char * segmentText = whisper_full_get_segment_text(ctx, segmentIndex);
        jobject words = newArrayList(env);

        const int tokenCount = whisper_full_n_tokens(ctx, segmentIndex);
        for (int tokenIndex = 0; tokenIndex < tokenCount; ++tokenIndex) {
            const whisper_token_data token = whisper_full_get_token_data(ctx, segmentIndex, tokenIndex);
            const char * tokenText = whisper_full_get_token_text(ctx, segmentIndex, tokenIndex);
            if (tokenText == nullptr || tokenText[0] == '\0' || token.t1 <= token.t0) continue;

            jobject word = newNativeWord(
                    env,
                    whisperTimeToSeconds(token.t0),
                    whisperTimeToSeconds(token.t1),
                    tokenText);
            addToList(env, words, word);
            env->DeleteLocalRef(word);
        }

        jobject segment = newNativeSegment(
                env,
                segmentStart,
                segmentEnd,
                segmentText == nullptr ? "" : segmentText,
                words);
        addToList(env, segments, segment);
        env->DeleteLocalRef(words);
        env->DeleteLocalRef(segment);
    }
    return segments;
}
