#include "spotifyengine.h"

#include <android/log.h>

#include <cstdlib>
#include <cstring>
#include <limits>
#include <vector>

#include "node.h"

static constexpr const char* TAG = "SpotifyPlusEngine";

static std::string JStringToStdString(JNIEnv* env, jstring value)
{
    if (!env || !value) return "";

    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return "";

    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

static jstring StdStringToJString(JNIEnv* env, const std::string& value)
{
    if (!env) return nullptr;
    return env->NewStringUTF(value.c_str());
}

static std::vector<std::string> JStringArrayToVector(JNIEnv* env, jobjectArray array)
{
    std::vector<std::string> result;
    if (!env || !array) return result;

    jsize length = env->GetArrayLength(array);
    result.reserve(length);

    for (jsize i = 0; i < length; i++)
    {
        jstring item = (jstring)env->GetObjectArrayElement(array, i);
        result.push_back(JStringToStdString(env, item));
        if (item) env->DeleteLocalRef(item);
    }

    return result;
}

SpotifyPlusEngine& SpotifyPlusEngine::Get()
{
    static SpotifyPlusEngine instance;
    return instance;
}

SpotifyPlusEngine::~SpotifyPlusEngine()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    if (!m_vm) return;

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env) return;

    ReleaseEventHandlerLocked();
    ClearBridgeLocked(env);
    DetachIfNeeded(didAttach);
}

bool SpotifyPlusEngine::Initialize(JavaVM* vm)
{
    if (!vm)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Initialize failed: vm is null");
        return false;
    }

    std::lock_guard<std::mutex> lock(m_mutex);
    m_vm = vm;
    __android_log_print(ANDROID_LOG_INFO, TAG, "Initialized with JavaVM=%p", vm);
    return true;
}

int pipe_stdout[2];
int pipe_stderr[2];
pthread_t thread_stdout;
pthread_t thread_stderr;

static bool should_skip_log(const char* msg)
{
    if (msg == nullptr)
        return true;

    return strstr(msg, "s_glBindAttribLocation:") != nullptr;
}

void* thread_stderr_func(void*)
{
    ssize_t redirect_size;
    char buf[2048];
    while ((redirect_size = read(pipe_stderr[0], buf, sizeof buf - 1)) > 0)
    {
        if (buf[redirect_size - 1] == '\n')
            --redirect_size;
        buf[redirect_size] = 0;

        if (should_skip_log(buf))
            continue;

        __android_log_write(ANDROID_LOG_ERROR, TAG, buf);
    }
    return 0;
}

void* thread_stdout_func(void*)
{
    ssize_t redirect_size;
    char buf[2048];
    while ((redirect_size = read(pipe_stdout[0], buf, sizeof buf - 1)) > 0)
    {
        if (buf[redirect_size - 1] == '\n')
            --redirect_size;
        buf[redirect_size] = 0;

        if (should_skip_log(buf))
            continue;

        __android_log_write(ANDROID_LOG_INFO, TAG, buf);
    }
    return 0;
}

int start_redirecting_stdout_stderr()
{
    // set stdout as unbuffered.
    setvbuf(stdout, 0, _IONBF, 0);
    pipe(pipe_stdout);
    dup2(pipe_stdout[1], STDOUT_FILENO);

    // set stderr as unbuffered.
    setvbuf(stderr, 0, _IONBF, 0);
    pipe(pipe_stderr);
    dup2(pipe_stderr[1], STDERR_FILENO);

    if (pthread_create(&thread_stdout, 0, thread_stdout_func, 0) == -1)
        return -1;
    pthread_detach(thread_stdout);

    if (pthread_create(&thread_stderr, 0, thread_stderr_func, 0) == -1)
        return -1;
    pthread_detach(thread_stderr);

    return 0;
}

void SpotifyPlusEngine::LoadNodeRuntime(JNIEnv* env, jobjectArray args)
{
    if (!env || !args)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "LoadNodeRuntime failed: env or args is null");
        return;
    }

    jsize argCount = env->GetArrayLength(args);
    if (argCount <= 0)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "LoadNodeRuntime failed: args was empty");
        return;
    }

    std::vector<std::string> convertedArgs;
    convertedArgs.reserve(argCount);

    size_t totalSize = 0;
    for (jsize i = 0; i < argCount; i++)
    {
        jstring arg = (jstring)env->GetObjectArrayElement(args, i);
        std::string value = JStringToStdString(env, arg);
        convertedArgs.push_back(value);
        totalSize += value.size() + 1;
        if (arg) env->DeleteLocalRef(arg);
    }

    char* argsBuffer = (char*)calloc(totalSize, sizeof(char));
    if (!argsBuffer)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "LoadNodeRuntime failed: calloc returned null");
        return;
    }

    std::vector<char*> argv;
    argv.reserve(convertedArgs.size());

    char* current = argsBuffer;
    for (const std::string& value : convertedArgs)
    {
        argv.push_back(current);
        memcpy(current, value.c_str(), value.size());
        current[value.size()] = '\0';
        current += value.size() + 1;
    }

    bool enable = true;
    if (enable && start_redirecting_stdout_stderr() == -1)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Couldn't start redirecting stdout and stderr to logcat.");
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "Calling node::Start with %d args", argCount);
    int result = node::Start(argCount, argv.data());
    __android_log_print(ANDROID_LOG_INFO, TAG, "node::Start returned %d", result);

    free(argsBuffer);
}

bool SpotifyPlusEngine::SetBridge(JNIEnv* env, jobject bridge, jobjectArray args)
{
    if (!env || !bridge)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "SetBridge failed: env or bridge is null");
        return false;
    }

    std::lock_guard<std::mutex> lock(m_mutex);

    if (!m_vm)
    {
        JavaVM* vm = nullptr;
        if (env->GetJavaVM(&vm) != JNI_OK || !vm)
        {
            __android_log_write(ANDROID_LOG_ERROR, TAG, "SetBridge failed: unable to get JavaVM from env");
            return false;
        }

        m_vm = vm;
    }

    ClearBridgeLocked(env);

    m_bridge = env->NewGlobalRef(bridge);
    if (!m_bridge)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "SetBridge failed: NewGlobalRef(bridge) returned null");
        return false;
    }

    jclass localClass = env->GetObjectClass(bridge);
    if (!localClass)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "SetBridge failed: GetObjectClass returned null");
        env->DeleteGlobalRef(m_bridge);
        m_bridge = nullptr;
        return false;
    }

    m_bridgeClass = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
    env->DeleteLocalRef(localClass);

    if (!m_bridgeClass)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "SetBridge failed: NewGlobalRef(class) returned null");
        env->DeleteGlobalRef(m_bridge);
        m_bridge = nullptr;
        return false;
    }

    if (!CacheMethodsLocked(env))
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "SetBridge failed: could not cache Java methods");
        ClearBridgeLocked(env);
        return false;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "Bridge registered successfully: bridge=%p class=%p", m_bridge, m_bridgeClass);

    StartNodeRuntimeAsync(env, args);
    return true;
}

void SpotifyPlusEngine::StartNodeRuntimeAsync(JNIEnv* env, jobjectArray args)
{
    bool expected = false;
    if (!m_nodeStarted.compare_exchange_strong(expected, true))
    {
        __android_log_write(ANDROID_LOG_INFO, TAG, "Node runtime already started");
        return;
    }

    JavaVM* vm = nullptr;
    if (env->GetJavaVM(&vm) != JNI_OK || !vm)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "StartNodeRuntimeAsync failed: could not get Java VM");
        m_nodeStarted = false;
        return;
    }

    jobjectArray globalArgs = args ? reinterpret_cast<jobjectArray>(env->NewGlobalRef(args)) : nullptr;
    if (args && !globalArgs)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "StartNodeRuntimeAsync failed: could not global ref args");
        m_nodeStarted = false;
        return;
    }

    std::thread([this, vm, globalArgs]()
                {
                    JNIEnv* threadEnv = nullptr;
                    bool attached = false;

                    if (vm->GetEnv(reinterpret_cast<void**>(&threadEnv), JNI_VERSION_1_6) != JNI_OK)
                    {
                        if (vm->AttachCurrentThread(&threadEnv, nullptr) != JNI_OK)
                        {
                            __android_log_write(ANDROID_LOG_ERROR, TAG, "Node thread failed to attach JVM");
                            m_nodeStarted = false;
                            return;
                        }

                        attached = true;
                    }

                    __android_log_write(ANDROID_LOG_INFO, TAG, "Starting node runtime on background thread");
                    LoadNodeRuntime(threadEnv, globalArgs);

                    if (globalArgs) threadEnv->DeleteGlobalRef(globalArgs);

                    if (attached) vm->DetachCurrentThread(); })
        .detach();
}

bool SpotifyPlusEngine::CacheMethodsLocked(JNIEnv* env)
{
    if (!env || !m_bridgeClass) return false;

    auto getField = [&](jclass clazz, const char* name, const char* sig) -> jfieldID
    {
        jfieldID field = env->GetFieldID(clazz, name, sig);
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            return nullptr;
        }

        return field;
    };

    auto getFieldEither = [&](jclass clazz, const char* first, const char* second, const char* sig) -> jfieldID
    {
        jfieldID field = getField(clazz, first, sig);
        if (field) return field;
        if (!second) return nullptr;
        return getField(clazz, second, sig);
    };

    jclass platformDataClass = env->FindClass("com/lenerd/spotifyplus/module/scripting/entities/PlatformData");
    if (!platformDataClass)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to find PlatformData class");
        if (env->ExceptionCheck())
        {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return false;
    }

    m_platformDataClass = reinterpret_cast<jclass>(env->NewGlobalRef(platformDataClass));
    env->DeleteLocalRef(platformDataClass);

    if (!m_platformDataClass)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to create global ref for PlatformData class");
        return false;
    }

    m_platformDataClientVersionField = env->GetFieldID(m_platformDataClass, "clientVersion", "Ljava/lang/String;");
    m_platformDataOsNameField = env->GetFieldID(m_platformDataClass, "osName", "Ljava/lang/String;");
    m_platformDataOsVersionField = env->GetFieldID(m_platformDataClass, "osVersion", "Ljava/lang/String;");
    m_platformDataSdkVersionField = env->GetFieldID(m_platformDataClass, "sdkVersion", "I");

    m_getPlatformDataMethod = env->GetMethodID(m_bridgeClass, "getPlatformData", "()Lcom/lenerd/spotifyplus/module/scripting/entities/PlatformData;");
    m_getSessionMethod = env->GetMethodID(m_bridgeClass, "getAccessToken", "()Ljava/lang/String;");

    if (!m_getPlatformDataMethod || !m_getSessionMethod || !m_platformDataClientVersionField || !m_platformDataOsNameField || !m_platformDataOsVersionField || !m_platformDataSdkVersionField)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to cache PlatformData/session method/fields");
        if (env->ExceptionCheck())
        {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return false;
    }

    jclass spotifyTrackClass = env->FindClass("com/lenerd/spotifyplus/sdk/spotify/entities/SpotifyTrack");
    if (!spotifyTrackClass)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to find SpotifyTrack class");
        if (env->ExceptionCheck())
        {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return false;
    }

    m_spotifyTrackClass = reinterpret_cast<jclass>(env->NewGlobalRef(spotifyTrackClass));
    env->DeleteLocalRef(spotifyTrackClass);

    if (!m_spotifyTrackClass)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to create global ref for SpotifyTrack class");
        return false;
    }

    jclass spotifyAlbumClass = env->FindClass("com/lenerd/spotifyplus/sdk/spotify/entities/SpotifyAlbum");
    if (!spotifyAlbumClass)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to find SpotifyAlbum class");
        if (env->ExceptionCheck())
        {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return false;
    }

    m_spotifyAlbumClass = reinterpret_cast<jclass>(env->NewGlobalRef(spotifyAlbumClass));
    env->DeleteLocalRef(spotifyAlbumClass);

    if (!m_spotifyAlbumClass)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to create global ref for SpotifyAlbum class");
        return false;
    }

    m_loadApkMethod = env->GetMethodID(m_bridgeClass, "loadApk", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    m_getCurrentTrackMethod = env->GetMethodID(m_bridgeClass, "getCurrentTrack", "()Lcom/lenerd/spotifyplus/sdk/spotify/entities/SpotifyTrack;");
    m_getTrackMethod = env->GetMethodID(m_bridgeClass, "getTrack", "(Ljava/lang/String;)Lcom/lenerd/spotifyplus/sdk/spotify/entities/SpotifyTrack;");

    m_spotifyAlbumTitleField = getField(m_spotifyAlbumClass, "title", "Ljava/lang/String;");
    m_spotifyAlbumArtistField = getField(m_spotifyAlbumClass, "artist", "Ljava/lang/String;");
    m_spotifyAlbumReleaseField = getFieldEither(m_spotifyAlbumClass, "release", "date", "Ljava/lang/String;");
    m_spotifyAlbumImageField = getField(m_spotifyAlbumClass, "image", "Ljava/lang/String;");

    m_spotifyTrackTitleField = getField(m_spotifyTrackClass, "title", "Ljava/lang/String;");
    m_spotifyTrackTrackNumberField = getField(m_spotifyTrackClass, "trackNumber", "I");
    m_spotifyTrackDurationField = getFieldEither(m_spotifyTrackClass, "durationMs", "duration", "J");
    m_spotifyTrackExplicitField = getFieldEither(m_spotifyTrackClass, "explicit", "explicitTrack", "Z");
    m_spotifyTrackUriField = getField(m_spotifyTrackClass, "uri", "Ljava/lang/String;");
    m_spotifyTrackIdField = getField(m_spotifyTrackClass, "id", "Ljava/lang/String;");
    m_spotifyTrackArtistField = getField(m_spotifyTrackClass, "artist", "Ljava/lang/String;");
    m_spotifyTrackArtistsField = getField(m_spotifyTrackClass, "artists", "[Ljava/lang/String;");
    m_spotifyTrackAlbumField = getField(m_spotifyTrackClass, "album", "Lcom/lenerd/spotifyplus/sdk/spotify/entities/SpotifyAlbum;");

    if (!m_getCurrentTrackMethod || !m_getTrackMethod || !m_spotifyAlbumTitleField || !m_spotifyAlbumArtistField || !m_spotifyAlbumReleaseField || !m_spotifyAlbumImageField || !m_spotifyTrackTitleField || !m_spotifyTrackTrackNumberField || !m_spotifyTrackDurationField || !m_spotifyTrackExplicitField || !m_spotifyTrackUriField || !m_spotifyTrackArtistField || !m_spotifyTrackArtistsField || !m_spotifyTrackAlbumField)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to cache SpotifyTrack method/fields");
        if (env->ExceptionCheck())
        {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return false;
    }

    m_unregisterScriptMethod = env->GetMethodID(m_bridgeClass, "unregisterScript", "(Ljava/lang/String;)V");
    m_getPlaybackPositionMethod = env->GetMethodID(m_bridgeClass, "getPlaybackPosition", "()D");
    m_seekMethod = env->GetMethodID(m_bridgeClass, "seek", "(J)V");
    m_playMethod = env->GetMethodID(m_bridgeClass, "play", "()V");
    m_pauseMethod = env->GetMethodID(m_bridgeClass, "pause", "()V");
    m_togglePlayMethod = env->GetMethodID(m_bridgeClass, "togglePlay", "()V");
    m_skipNextMethod = env->GetMethodID(m_bridgeClass, "skipNext", "()V");
    m_skipPreviousMethod = env->GetMethodID(m_bridgeClass, "skipPrevious", "()V");

    m_toastMethod = env->GetMethodID(m_bridgeClass, "toast", "(Ljava/lang/String;Z)V");
    m_navigateMethod = env->GetMethodID(m_bridgeClass, "navigate", "(Ljava/lang/String;Ljava/lang/String;)Z");
    m_navigateBackMethod = env->GetMethodID(m_bridgeClass, "navigateBack", "()Z");

    m_registerContextMenuMethod = env->GetMethodID(m_bridgeClass, "registerContextMenu", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    m_registerSideDrawerMethod = env->GetMethodID(m_bridgeClass, "registerSideDrawer", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    m_registerSideDrawerWithIconMethod = env->GetMethodID(m_bridgeClass, "registerSideDrawerWithIcon", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");

    m_storageSetMethod = env->GetMethodID(m_bridgeClass, "storageSet", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    m_storageGetMethod = env->GetMethodID(m_bridgeClass, "storageGet", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    m_storageRemoveMethod = env->GetMethodID(m_bridgeClass, "storageRemove", "(Ljava/lang/String;Ljava/lang/String;)V");
    m_storageWriteTextMethod = env->GetMethodID(m_bridgeClass, "storageWriteText", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    m_storageWriteJsonMethod = env->GetMethodID(m_bridgeClass, "storageWriteJson", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    m_storageWriteBinaryMethod = env->GetMethodID(m_bridgeClass, "storageWriteBinary", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    m_storageReadMethod = env->GetMethodID(m_bridgeClass, "storageRead", "(Ljava/lang/String;Ljava/lang/String;)Lcom/lenerd/spotifyplus/module/scripting/SpotifyNativeBridge$StorageReadResult;");
    m_getDeveloperModeMethod = env->GetMethodID(m_bridgeClass, "getDeveloperMode", "()Z");
    m_setDeveloperModeMethod = env->GetMethodID(m_bridgeClass, "setDeveloperMode", "(Z)V");
    m_pickLocalExtensionsFolderMethod = env->GetMethodID(m_bridgeClass, "pickLocalExtensionsFolder", "()Z");
    m_getLocalExtensionsFolderDisplayNameMethod = env->GetMethodID(m_bridgeClass, "getLocalExtensionsFolderDisplayName", "()Ljava/lang/String;");
    m_listLocalExtensionsMethod = env->GetMethodID(m_bridgeClass, "listLocalExtensions", "()Ljava/lang/String;");
    m_refreshLocalExtensionsMethod = env->GetMethodID(m_bridgeClass, "refreshLocalExtensions", "()Ljava/lang/String;");

    if (!m_getPlaybackPositionMethod || !m_seekMethod || !m_playMethod || !m_pauseMethod || !m_togglePlayMethod || !m_skipNextMethod || !m_skipPreviousMethod || !m_toastMethod || !m_navigateMethod || !m_navigateBackMethod || !m_registerContextMenuMethod || !m_registerSideDrawerMethod || !m_registerSideDrawerWithIconMethod || !m_storageSetMethod || !m_storageGetMethod || !m_storageRemoveMethod || !m_storageWriteTextMethod || !m_storageWriteJsonMethod || !m_storageWriteBinaryMethod || !m_storageReadMethod || !m_getDeveloperModeMethod || !m_setDeveloperModeMethod || !m_pickLocalExtensionsFolderMethod || !m_getLocalExtensionsFolderDisplayNameMethod || !m_listLocalExtensionsMethod || !m_refreshLocalExtensionsMethod || !m_loadApkMethod || !m_unregisterScriptMethod)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "One or more required bridge methods were not found");
        if (env->ExceptionCheck())
        {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return false;
    }

    jclass storageReadResultClass = env->FindClass("com/lenerd/spotifyplus/module/scripting/SpotifyNativeBridge$StorageReadResult");
    if (!storageReadResultClass)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to find StorageReadResult class");
        if (env->ExceptionCheck())
        {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        return false;
    }

    m_storageReadResultClass = reinterpret_cast<jclass>(env->NewGlobalRef(storageReadResultClass));
    env->DeleteLocalRef(storageReadResultClass);

    if (!m_storageReadResultClass)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to create global ref for StorageReadResult class");
        return false;
    }

    m_storageReadFoundField = getField(m_storageReadResultClass, "found", "Z");
    m_storageReadTypeField = getField(m_storageReadResultClass, "type", "Ljava/lang/String;");
    m_storageReadValueField = getField(m_storageReadResultClass, "value", "Ljava/lang/String;");
    m_storageReadDataField = getField(m_storageReadResultClass, "data", "Ljava/lang/String;");

    if (!m_storageReadFoundField || !m_storageReadTypeField || !m_storageReadValueField || !m_storageReadDataField)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to cache StorageReadResult fields");
        return false;
    }

    m_registerSurfaceMethod = env->GetMethodID(m_bridgeClass, "registerSurface", "(Ljava/lang/String;)V");
    m_unregisterSurfaceMethod = env->GetMethodID(m_bridgeClass, "unregisterSurface", "(Ljava/lang/String;)V");
    m_commitSurfaceMethod = env->GetMethodID(m_bridgeClass, "commitSurface", "(Ljava/lang/String;Ljava/lang/String;)V");
    m_requestWorkletFrameMethod = env->GetMethodID(m_bridgeClass, "requestWorkletFrame", "()V");
    m_dispatchWorkletUpdatesMethod = env->GetMethodID(
        m_bridgeClass,
        "dispatchWorkletUpdates",
        "(J[Ljava/lang/String;[I[Ljava/lang/String;[I[D[Ljava/lang/String;)V");
    m_measureWorkletViewMethod = env->GetMethodID(m_bridgeClass, "measureWorkletView", "(Ljava/lang/String;I)Ljava/lang/String;");
    m_getWorkletRelativeCoordsMethod = env->GetMethodID(m_bridgeClass, "getWorkletRelativeCoords", "(Ljava/lang/String;IDD)Ljava/lang/String;");
    m_scrollWorkletViewMethod = env->GetMethodID(m_bridgeClass, "scrollWorkletView", "(Ljava/lang/String;IDDZ)Z");
    m_dispatchWorkletCommandMethod = env->GetMethodID(m_bridgeClass, "dispatchWorkletCommand", "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;)Z");
    m_activateWorkletSourceMethod = env->GetMethodID(m_bridgeClass, "activateWorkletSource", "(Ljava/lang/String;Ljava/lang/String;)V");
    m_deactivateWorkletSourceMethod = env->GetMethodID(m_bridgeClass, "deactivateWorkletSource", "(Ljava/lang/String;Ljava/lang/String;)V");

    if (!m_registerSurfaceMethod || !m_unregisterSurfaceMethod || !m_commitSurfaceMethod || !m_requestWorkletFrameMethod || !m_dispatchWorkletUpdatesMethod || !m_measureWorkletViewMethod || !m_getWorkletRelativeCoordsMethod || !m_scrollWorkletViewMethod || !m_dispatchWorkletCommandMethod || !m_activateWorkletSourceMethod || !m_deactivateWorkletSourceMethod)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to cache surface or worklet methods");
        return false;
    }

    return true;
}

JNIEnv* SpotifyPlusEngine::GetEnv(bool* didAttach)
{
    if (didAttach) *didAttach = false;
    if (!m_vm) return nullptr;

    JNIEnv* env = nullptr;
    jint result = m_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (result == JNI_OK) return env;

    if (result == JNI_EDETACHED)
    {
        if (m_vm->AttachCurrentThread(&env, nullptr) != JNI_OK)
        {
            __android_log_write(ANDROID_LOG_ERROR, TAG, "AttachCurrentThread failed");
            return nullptr;
        }

        if (didAttach) *didAttach = true;
        return env;
    }

    __android_log_print(ANDROID_LOG_ERROR, TAG, "GetEnv failed with result=%d", result);
    return nullptr;
}

void SpotifyPlusEngine::DetachIfNeeded(bool didAttach)
{
    if (didAttach && m_vm) m_vm->DetachCurrentThread();
}

bool SpotifyPlusEngine::EnsureBridgeLocked(JNIEnv* env)
{
    return env && m_bridge && m_bridgeClass;
}

void SpotifyPlusEngine::ClearBridgeLocked(JNIEnv* env)
{
    m_getPlatformDataMethod = nullptr;
    m_getSessionMethod = nullptr;
    m_getCurrentTrackMethod = nullptr;
    m_getTrackMethod = nullptr;
    m_getPlaybackPositionMethod = nullptr;
    m_seekMethod = nullptr;
    m_playMethod = nullptr;
    m_pauseMethod = nullptr;
    m_togglePlayMethod = nullptr;
    m_skipNextMethod = nullptr;
    m_skipPreviousMethod = nullptr;
    m_toastMethod = nullptr;
    m_navigateMethod = nullptr;
    m_navigateBackMethod = nullptr;
    m_storageSetMethod = nullptr;
    m_storageGetMethod = nullptr;
    m_storageRemoveMethod = nullptr;
    m_storageWriteTextMethod = nullptr;
    m_storageWriteJsonMethod = nullptr;
    m_storageWriteBinaryMethod = nullptr;
    m_storageReadMethod = nullptr;
    m_getDeveloperModeMethod = nullptr;
    m_setDeveloperModeMethod = nullptr;
    m_pickLocalExtensionsFolderMethod = nullptr;
    m_getLocalExtensionsFolderDisplayNameMethod = nullptr;
    m_listLocalExtensionsMethod = nullptr;
    m_refreshLocalExtensionsMethod = nullptr;
    m_registerSurfaceMethod = nullptr;
    m_unregisterSurfaceMethod = nullptr;
    m_commitSurfaceMethod = nullptr;
    m_requestWorkletFrameMethod = nullptr;
    m_dispatchWorkletUpdatesMethod = nullptr;
    m_measureWorkletViewMethod = nullptr;
    m_getWorkletRelativeCoordsMethod = nullptr;
    m_scrollWorkletViewMethod = nullptr;
    m_dispatchWorkletCommandMethod = nullptr;
    m_activateWorkletSourceMethod = nullptr;
    m_deactivateWorkletSourceMethod = nullptr;

    m_platformDataClientVersionField = nullptr;
    m_platformDataOsNameField = nullptr;
    m_platformDataOsVersionField = nullptr;
    m_platformDataSdkVersionField = nullptr;

    m_spotifyAlbumTitleField = nullptr;
    m_spotifyAlbumArtistField = nullptr;
    m_spotifyAlbumReleaseField = nullptr;
    m_spotifyAlbumImageField = nullptr;

    m_spotifyTrackTitleField = nullptr;
    m_spotifyTrackTrackNumberField = nullptr;
    m_spotifyTrackDurationField = nullptr;
    m_spotifyTrackExplicitField = nullptr;
    m_spotifyTrackUriField = nullptr;
    m_spotifyTrackIdField = nullptr;
    m_spotifyTrackArtistField = nullptr;
    m_spotifyTrackArtistsField = nullptr;
    m_spotifyTrackAlbumField = nullptr;

    m_storageReadFoundField = nullptr;
    m_storageReadTypeField = nullptr;
    m_storageReadValueField = nullptr;
    m_storageReadDataField = nullptr;

    if (m_storageReadResultClass)
    {
        env->DeleteGlobalRef(m_storageReadResultClass);
        m_storageReadResultClass = nullptr;
    }

    if (m_spotifyAlbumClass)
    {
        env->DeleteGlobalRef(m_spotifyAlbumClass);
        m_spotifyAlbumClass = nullptr;
    }

    if (m_spotifyTrackClass)
    {
        env->DeleteGlobalRef(m_spotifyTrackClass);
        m_spotifyTrackClass = nullptr;
    }

    if (m_platformDataClass)
    {
        env->DeleteGlobalRef(m_platformDataClass);
        m_platformDataClass = nullptr;
    }

    if (m_bridgeClass)
    {
        env->DeleteGlobalRef(m_bridgeClass);
        m_bridgeClass = nullptr;
    }

    if (m_bridge)
    {
        env->DeleteGlobalRef(m_bridge);
        m_bridge = nullptr;
    }
}

bool SpotifyPlusEngine::IsReady() const
{
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_vm && m_bridge && m_bridgeClass;
}

bool SpotifyPlusEngine::SetEventHandler(napi_env env, napi_value callback)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    ReleaseEventHandlerLocked();

    napi_value resourceName;
    napi_create_string_utf8(env, "SpotifyPlusNativeEvents", NAPI_AUTO_LENGTH, &resourceName);

    napi_status status = napi_create_threadsafe_function(env, callback, nullptr, resourceName, 0, 1, nullptr, nullptr, nullptr, CallJs, &m_eventTSfn);
    if (status != napi_ok)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to create ThreadSafeFunction");
        m_eventTSfn = nullptr;
        return false;
    }

    return true;
}

void SpotifyPlusEngine::ReleaseEventHandlerLocked()
{
    if (!m_eventTSfn) return;
    napi_release_threadsafe_function(m_eventTSfn, napi_tsfn_abort);
    m_eventTSfn = nullptr;
}

void SpotifyPlusEngine::EmitEvent(const std::string& type, const std::string& payload)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    if (!m_eventTSfn) return;

    NativeEvent* event = new NativeEvent{type, payload};
    napi_status status = napi_call_threadsafe_function(m_eventTSfn, event, napi_tsfn_nonblocking);
    if (status != napi_ok)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to queue native event: %d", status);
        delete event;
    }
}

void SpotifyPlusEngine::CallJs(napi_env env, napi_value jsCallback, void*, void* data)
{
    NativeEvent* event = static_cast<NativeEvent*>(data);
    if (!env || !jsCallback || !event)
    {
        delete event;
        return;
    }

    napi_value global;
    napi_get_global(env, &global);

    napi_value argv[2];
    napi_create_string_utf8(env, event->type.c_str(), NAPI_AUTO_LENGTH, &argv[0]);
    napi_create_string_utf8(env, event->payload.c_str(), NAPI_AUTO_LENGTH, &argv[1]);

    napi_value result;
    napi_status status = napi_call_function(env, global, jsCallback, 2, argv, &result);
    if (status != napi_ok)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "CallJs failed: napi_call_function status=%d", status);

        bool isExceptionPending = false;
        napi_is_exception_pending(env, &isExceptionPending);
        if (isExceptionPending)
        {
            napi_value error;
            napi_get_and_clear_last_exception(env, &error);

            napi_value errorString;
            napi_coerce_to_string(env, error, &errorString);

            size_t length = 0;
            napi_get_value_string_utf8(env, errorString, nullptr, 0, &length);

            std::string message;
            message.resize(length + 1);
            napi_get_value_string_utf8(env, errorString, message.data(), length + 1, &length);
            message.resize(length);

            __android_log_print(ANDROID_LOG_ERROR, TAG, "JS exception: %s", message.c_str());
        }
    }

    delete event;
}

SpotifyTrack SpotifyPlusEngine::ReadTrackLocked(JNIEnv* env, jobject spotifyTrackObject)
{
    SpotifyTrack fallback{};
    if (!env || !spotifyTrackObject) return fallback;

    jstring title = (jstring)env->GetObjectField(spotifyTrackObject, m_spotifyTrackTitleField);
    jint trackNumber = env->GetIntField(spotifyTrackObject, m_spotifyTrackTrackNumberField);
    jlong duration = env->GetLongField(spotifyTrackObject, m_spotifyTrackDurationField);
    jboolean explicitTrack = env->GetBooleanField(spotifyTrackObject, m_spotifyTrackExplicitField);
    jstring uri = (jstring)env->GetObjectField(spotifyTrackObject, m_spotifyTrackUriField);
    jstring id = (jstring)env->GetObjectField(spotifyTrackObject, m_spotifyTrackIdField);
    jstring artist = (jstring)env->GetObjectField(spotifyTrackObject, m_spotifyTrackArtistField);
    jobjectArray artists = (jobjectArray)env->GetObjectField(spotifyTrackObject, m_spotifyTrackArtistsField);
    jobject albumObject = (jobject)env->GetObjectField(spotifyTrackObject, m_spotifyTrackAlbumField);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Exception while reading SpotifyTrack fields");
        env->ExceptionDescribe();
        env->ExceptionClear();

        if (title) env->DeleteLocalRef(title);
        if (uri) env->DeleteLocalRef(uri);
        if (id) env->DeleteLocalRef(id);
        if (artist) env->DeleteLocalRef(artist);
        if (artists) env->DeleteLocalRef(artists);
        if (albumObject) env->DeleteLocalRef(albumObject);
        return fallback;
    }

    SpotifyAlbum album{};
    if (albumObject)
    {
        jstring albumTitle = (jstring)env->GetObjectField(albumObject, m_spotifyAlbumTitleField);
        jstring albumArtist = (jstring)env->GetObjectField(albumObject, m_spotifyAlbumArtistField);
        jstring albumRelease = (jstring)env->GetObjectField(albumObject, m_spotifyAlbumReleaseField);
        jstring albumImage = (jstring)env->GetObjectField(albumObject, m_spotifyAlbumImageField);

        if (env->ExceptionCheck())
        {
            __android_log_write(ANDROID_LOG_ERROR, TAG, "Exception while reading SpotifyAlbum fields");
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        else
        {
            album.title = JStringToStdString(env, albumTitle);
            album.artist = JStringToStdString(env, albumArtist);
            album.date = JStringToStdString(env, albumRelease);
            album.image = JStringToStdString(env, albumImage);
        }

        if (albumTitle) env->DeleteLocalRef(albumTitle);
        if (albumArtist) env->DeleteLocalRef(albumArtist);
        if (albumRelease) env->DeleteLocalRef(albumRelease);
        if (albumImage) env->DeleteLocalRef(albumImage);
        env->DeleteLocalRef(albumObject);
    }

    SpotifyTrack result = {JStringToStdString(env, title),
                           static_cast<int>(trackNumber),
                           static_cast<long>(duration),
                           explicitTrack == JNI_TRUE,
                           JStringToStdString(env, uri),
                           JStringToStdString(env, id),
                           JStringToStdString(env, artist),
                           JStringArrayToVector(env, artists),
                           album};

    if (title) env->DeleteLocalRef(title);
    if (uri) env->DeleteLocalRef(uri);
    if (id) env->DeleteLocalRef(id);
    if (artist) env->DeleteLocalRef(artist);
    if (artists) env->DeleteLocalRef(artists);

    return result;
}

void SpotifyPlusEngine::LoadApk(const std::string& scriptId, const std::string& apkPath, const std::string& pluginClass)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_loadApkMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring scriptIdValue = StdStringToJString(env, scriptId);
    jstring apkPathValue = StdStringToJString(env, apkPath);
    jstring pluginClassValue = StdStringToJString(env, pluginClass);

    env->CallVoidMethod(m_bridge, m_loadApkMethod, scriptIdValue, apkPathValue, pluginClassValue);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in LoadApk()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    if (scriptIdValue) env->DeleteLocalRef(scriptIdValue);
    if (apkPathValue) env->DeleteLocalRef(apkPathValue);
    if (pluginClassValue) env->DeleteLocalRef(pluginClassValue);

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::UnregisterScript(const std::string& scriptId)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_unregisterScriptMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring scriptIdValue = StdStringToJString(env, scriptId);
    env->CallVoidMethod(m_bridge, m_unregisterScriptMethod, scriptIdValue);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in UnregisterScript()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    if (scriptIdValue) env->DeleteLocalRef(scriptIdValue);
    DetachIfNeeded(didAttach);
}

PlatformData SpotifyPlusEngine::GetPlatformData()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    PlatformData fallback{"", "android", "", 0};

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_getPlatformDataMethod)
    {
        DetachIfNeeded(didAttach);
        return fallback;
    }

    jobject platformDataObject = env->CallObjectMethod(m_bridge, m_getPlatformDataMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in getPlatformData()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        DetachIfNeeded(didAttach);
        return fallback;
    }

    if (!platformDataObject)
    {
        DetachIfNeeded(didAttach);
        return fallback;
    }

    jstring clientVersion = (jstring)env->GetObjectField(platformDataObject, m_platformDataClientVersionField);
    jstring osName = (jstring)env->GetObjectField(platformDataObject, m_platformDataOsNameField);
    jstring osVersion = (jstring)env->GetObjectField(platformDataObject, m_platformDataOsVersionField);
    jint sdkVersion = env->GetIntField(platformDataObject, m_platformDataSdkVersionField);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Exception while reading PlatformData fields");
        env->ExceptionDescribe();
        env->ExceptionClear();
        env->DeleteLocalRef(platformDataObject);
        DetachIfNeeded(didAttach);
        return fallback;
    }

    PlatformData result{JStringToStdString(env, clientVersion), JStringToStdString(env, osName), JStringToStdString(env, osVersion), static_cast<int>(sdkVersion)};

    if (clientVersion) env->DeleteLocalRef(clientVersion);
    if (osName) env->DeleteLocalRef(osName);
    if (osVersion) env->DeleteLocalRef(osVersion);
    env->DeleteLocalRef(platformDataObject);

    DetachIfNeeded(didAttach);
    return result;
}

SessionData SpotifyPlusEngine::GetSession()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    SessionData fallback{""};

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_getSessionMethod)
    {
        DetachIfNeeded(didAttach);
        return fallback;
    }

    jstring accessToken = (jstring)env->CallObjectMethod(m_bridge, m_getSessionMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in getAccessToken()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        DetachIfNeeded(didAttach);
        return fallback;
    }

    SessionData result{JStringToStdString(env, accessToken)};
    if (accessToken) env->DeleteLocalRef(accessToken);

    DetachIfNeeded(didAttach);
    return result;
}

void SpotifyPlusEngine::Log(const char* message)
{
    __android_log_write(ANDROID_LOG_INFO, TAG, message ? message : "");
}

SpotifyTrack SpotifyPlusEngine::GetCurrentTrack()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    SpotifyTrack fallback{};

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_getCurrentTrackMethod)
    {
        DetachIfNeeded(didAttach);
        return fallback;
    }

    jobject spotifyTrackObject = env->CallObjectMethod(m_bridge, m_getCurrentTrackMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in getCurrentTrack()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        DetachIfNeeded(didAttach);
        return fallback;
    }

    if (!spotifyTrackObject)
    {
        DetachIfNeeded(didAttach);
        return fallback;
    }

    SpotifyTrack result = ReadTrackLocked(env, spotifyTrackObject);
    env->DeleteLocalRef(spotifyTrackObject);

    DetachIfNeeded(didAttach);
    return result;
}

SpotifyTrack SpotifyPlusEngine::GetTrack(const std::string& uri)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    SpotifyTrack fallback{};

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_getTrackMethod)
    {
        DetachIfNeeded(didAttach);
        return fallback;
    }

    jstring uriValue = StdStringToJString(env, uri);
    jobject spotifyTrackObject = env->CallObjectMethod(m_bridge, m_getTrackMethod, uriValue);
    if (uriValue) env->DeleteLocalRef(uriValue);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in getTrack()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        DetachIfNeeded(didAttach);
        return fallback;
    }

    if (!spotifyTrackObject)
    {
        DetachIfNeeded(didAttach);
        return fallback;
    }

    SpotifyTrack result = ReadTrackLocked(env, spotifyTrackObject);
    env->DeleteLocalRef(spotifyTrackObject);

    DetachIfNeeded(didAttach);
    return result;
}

double SpotifyPlusEngine::GetPlaybackPosition()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_getPlaybackPositionMethod)
    {
        DetachIfNeeded(didAttach);
        return std::numeric_limits<double>::quiet_NaN();
    }

    jdouble result = env->CallDoubleMethod(m_bridge, m_getPlaybackPositionMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in getPlaybackPosition()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        DetachIfNeeded(didAttach);
        return std::numeric_limits<double>::quiet_NaN();
    }

    DetachIfNeeded(didAttach);
    return static_cast<double>(result);
}

void SpotifyPlusEngine::Seek(long position)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_seekMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    env->CallVoidMethod(m_bridge, m_seekMethod, (jlong)position);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in seek()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::Play()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_playMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    env->CallVoidMethod(m_bridge, m_playMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in play()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::Pause()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_pauseMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    env->CallVoidMethod(m_bridge, m_pauseMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in pause()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::TogglePlay()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_togglePlayMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    env->CallVoidMethod(m_bridge, m_togglePlayMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in togglePlay()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::SkipNext()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_skipNextMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    env->CallVoidMethod(m_bridge, m_skipNextMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in skipNext()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::SkipPrevious()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_skipPreviousMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    env->CallVoidMethod(m_bridge, m_skipPreviousMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in skipPrevious()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::Toast(const std::string& text, bool longLength)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_toastMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring textValue = StdStringToJString(env, text);
    env->CallVoidMethod(m_bridge, m_toastMethod, textValue, longLength ? JNI_TRUE : JNI_FALSE);
    if (textValue) env->DeleteLocalRef(textValue);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in toast()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

bool SpotifyPlusEngine::Navigate(const std::string& uri, const std::string& target)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_navigateMethod)
    {
        DetachIfNeeded(didAttach);
        return false;
    }

    jstring uriValue = StdStringToJString(env, uri);
    jstring targetValue = StdStringToJString(env, target);
    jboolean result = env->CallBooleanMethod(m_bridge, m_navigateMethod, uriValue, targetValue);
    if (uriValue) env->DeleteLocalRef(uriValue);
    if (targetValue) env->DeleteLocalRef(targetValue);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in navigate()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        result = JNI_FALSE;
    }

    DetachIfNeeded(didAttach);
    return result == JNI_TRUE;
}

bool SpotifyPlusEngine::NavigateBack()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_navigateBackMethod)
    {
        DetachIfNeeded(didAttach);
        return false;
    }

    jboolean result = env->CallBooleanMethod(m_bridge, m_navigateBackMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in navigateBack()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        result = JNI_FALSE;
    }

    DetachIfNeeded(didAttach);
    return result == JNI_TRUE;
}

void SpotifyPlusEngine::StorageSet(const std::string& scriptId, const std::string& key, const std::string& value)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_storageSetMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring scriptIdValue = StdStringToJString(env, scriptId);
    jstring keyValue = StdStringToJString(env, key);
    jstring valueString = StdStringToJString(env, value);

    env->CallVoidMethod(m_bridge, m_storageSetMethod, scriptIdValue, keyValue, valueString);

    if (scriptIdValue) env->DeleteLocalRef(scriptIdValue);
    if (keyValue) env->DeleteLocalRef(keyValue);
    if (valueString) env->DeleteLocalRef(valueString);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in storageSet()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

StorageValueResult SpotifyPlusEngine::StorageGet(const std::string& scriptId, const std::string& key)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    StorageValueResult fallback{false, ""};

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_storageGetMethod)
    {
        DetachIfNeeded(didAttach);
        return fallback;
    }

    jstring scriptIdValue = StdStringToJString(env, scriptId);
    jstring keyValue = StdStringToJString(env, key);
    jstring value = (jstring)env->CallObjectMethod(m_bridge, m_storageGetMethod, scriptIdValue, keyValue);

    if (scriptIdValue) env->DeleteLocalRef(scriptIdValue);
    if (keyValue) env->DeleteLocalRef(keyValue);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in storageGet()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        DetachIfNeeded(didAttach);
        return fallback;
    }

    StorageValueResult result{value != nullptr, JStringToStdString(env, value)};
    if (value) env->DeleteLocalRef(value);

    DetachIfNeeded(didAttach);
    return result;
}

void SpotifyPlusEngine::StorageRemove(const std::string& scriptId, const std::string& key)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_storageRemoveMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring scriptIdValue = StdStringToJString(env, scriptId);
    jstring keyValue = StdStringToJString(env, key);

    env->CallVoidMethod(m_bridge, m_storageRemoveMethod, scriptIdValue, keyValue);

    if (scriptIdValue) env->DeleteLocalRef(scriptIdValue);
    if (keyValue) env->DeleteLocalRef(keyValue);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in storageRemove()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::StorageWriteText(const std::string& scriptId, const std::string& path, const std::string& value)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_storageWriteTextMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring scriptIdValue = StdStringToJString(env, scriptId);
    jstring pathValue = StdStringToJString(env, path);
    jstring valueString = StdStringToJString(env, value);

    env->CallVoidMethod(m_bridge, m_storageWriteTextMethod, scriptIdValue, pathValue, valueString);

    if (scriptIdValue) env->DeleteLocalRef(scriptIdValue);
    if (pathValue) env->DeleteLocalRef(pathValue);
    if (valueString) env->DeleteLocalRef(valueString);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in storageWriteText()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::StorageWriteJson(const std::string& scriptId, const std::string& path, const std::string& value)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_storageWriteJsonMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring scriptIdValue = StdStringToJString(env, scriptId);
    jstring pathValue = StdStringToJString(env, path);
    jstring valueString = StdStringToJString(env, value);

    env->CallVoidMethod(m_bridge, m_storageWriteJsonMethod, scriptIdValue, pathValue, valueString);

    if (scriptIdValue) env->DeleteLocalRef(scriptIdValue);
    if (pathValue) env->DeleteLocalRef(pathValue);
    if (valueString) env->DeleteLocalRef(valueString);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in storageWriteJson()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::StorageWriteBinary(const std::string& scriptId, const std::string& path, const std::string& data)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_storageWriteBinaryMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring scriptIdValue = StdStringToJString(env, scriptId);
    jstring pathValue = StdStringToJString(env, path);
    jstring dataValue = StdStringToJString(env, data);

    env->CallVoidMethod(m_bridge, m_storageWriteBinaryMethod, scriptIdValue, pathValue, dataValue);

    if (scriptIdValue) env->DeleteLocalRef(scriptIdValue);
    if (pathValue) env->DeleteLocalRef(pathValue);
    if (dataValue) env->DeleteLocalRef(dataValue);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in storageWriteBinary()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

StorageReadResult SpotifyPlusEngine::StorageRead(const std::string& scriptId, const std::string& path)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    StorageReadResult fallback{false, "", "", ""};

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_storageReadMethod)
    {
        DetachIfNeeded(didAttach);
        return fallback;
    }

    jstring scriptIdValue = StdStringToJString(env, scriptId);
    jstring pathValue = StdStringToJString(env, path);
    jobject readResult = env->CallObjectMethod(m_bridge, m_storageReadMethod, scriptIdValue, pathValue);

    if (scriptIdValue) env->DeleteLocalRef(scriptIdValue);
    if (pathValue) env->DeleteLocalRef(pathValue);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in storageRead()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        DetachIfNeeded(didAttach);
        return fallback;
    }

    if (!readResult)
    {
        DetachIfNeeded(didAttach);
        return fallback;
    }

    jboolean found = env->GetBooleanField(readResult, m_storageReadFoundField);
    jstring type = (jstring)env->GetObjectField(readResult, m_storageReadTypeField);
    jstring value = (jstring)env->GetObjectField(readResult, m_storageReadValueField);
    jstring data = (jstring)env->GetObjectField(readResult, m_storageReadDataField);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Exception while reading StorageReadResult fields");
        env->ExceptionDescribe();
        env->ExceptionClear();
        env->DeleteLocalRef(readResult);
        DetachIfNeeded(didAttach);
        return fallback;
    }

    StorageReadResult result{found == JNI_TRUE, JStringToStdString(env, type), JStringToStdString(env, value), JStringToStdString(env, data)};

    if (type) env->DeleteLocalRef(type);
    if (value) env->DeleteLocalRef(value);
    if (data) env->DeleteLocalRef(data);
    env->DeleteLocalRef(readResult);

    DetachIfNeeded(didAttach);
    return result;
}

bool SpotifyPlusEngine::GetDeveloperMode()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_getDeveloperModeMethod)
    {
        DetachIfNeeded(didAttach);
        return false;
    }

    jboolean result = env->CallBooleanMethod(m_bridge, m_getDeveloperModeMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in getDeveloperMode()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        result = JNI_FALSE;
    }

    DetachIfNeeded(didAttach);
    return result == JNI_TRUE;
}

void SpotifyPlusEngine::SetDeveloperMode(bool enabled)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_setDeveloperModeMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    env->CallVoidMethod(m_bridge, m_setDeveloperModeMethod, enabled ? JNI_TRUE : JNI_FALSE);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in setDeveloperMode()");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

bool SpotifyPlusEngine::PickLocalExtensionsFolder()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_pickLocalExtensionsFolderMethod)
    {
        DetachIfNeeded(didAttach);
        return false;
    }

    jboolean result = env->CallBooleanMethod(m_bridge, m_pickLocalExtensionsFolderMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in pickLocalExtensionsFolder()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        result = JNI_FALSE;
    }

    DetachIfNeeded(didAttach);
    return result == JNI_TRUE;
}

std::string SpotifyPlusEngine::GetLocalExtensionsFolderDisplayName()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_getLocalExtensionsFolderDisplayNameMethod)
    {
        DetachIfNeeded(didAttach);
        return "";
    }

    jstring value = (jstring)env->CallObjectMethod(m_bridge, m_getLocalExtensionsFolderDisplayNameMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in getLocalExtensionsFolderDisplayName()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        DetachIfNeeded(didAttach);
        return "";
    }

    std::string result = JStringToStdString(env, value);
    if (value) env->DeleteLocalRef(value);
    DetachIfNeeded(didAttach);
    return result;
}

std::string SpotifyPlusEngine::ListLocalExtensions()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_listLocalExtensionsMethod)
    {
        DetachIfNeeded(didAttach);
        return "[]";
    }

    jstring value = (jstring)env->CallObjectMethod(m_bridge, m_listLocalExtensionsMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in listLocalExtensions()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        DetachIfNeeded(didAttach);
        return "[]";
    }

    std::string result = JStringToStdString(env, value);
    if (value) env->DeleteLocalRef(value);
    DetachIfNeeded(didAttach);
    return result.empty() ? "[]" : result;
}

std::string SpotifyPlusEngine::RefreshLocalExtensions()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_refreshLocalExtensionsMethod)
    {
        DetachIfNeeded(didAttach);
        return "[]";
    }

    jstring value = (jstring)env->CallObjectMethod(m_bridge, m_refreshLocalExtensionsMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in refreshLocalExtensions()");
        env->ExceptionDescribe();
        env->ExceptionClear();
        DetachIfNeeded(didAttach);
        return "[]";
    }

    std::string result = JStringToStdString(env, value);
    if (value) env->DeleteLocalRef(value);
    DetachIfNeeded(didAttach);
    return result.empty() ? "[]" : result;
}

void SpotifyPlusEngine::RegisterContextMenu(const std::string& id, const std::string& scriptId, const std::string& title)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_registerContextMenuMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring idString = StdStringToJString(env, id);
    jstring scriptIdString = StdStringToJString(env, scriptId);
    jstring titleString = StdStringToJString(env, title);

    env->CallVoidMethod(m_bridge, m_registerContextMenuMethod, idString, scriptIdString, titleString);

    if (env->ExceptionCheck())
    {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    if (idString) env->DeleteLocalRef(idString);
    if (scriptIdString) env->DeleteLocalRef(scriptIdString);
    if (titleString) env->DeleteLocalRef(titleString);

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::RegisterSideDrawer(const std::string& id, const std::string& scriptId, const std::string& title)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_registerSideDrawerMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring idString = StdStringToJString(env, id);
    jstring scriptIdString = StdStringToJString(env, scriptId);
    jstring titleString = StdStringToJString(env, title);

    env->CallVoidMethod(m_bridge, m_registerSideDrawerMethod, idString, scriptIdString, titleString);

    if (env->ExceptionCheck())
    {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    if (idString) env->DeleteLocalRef(idString);
    if (scriptIdString) env->DeleteLocalRef(scriptIdString);
    if (titleString) env->DeleteLocalRef(titleString);

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::RegisterSurface(const std::string& surfaceId)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_registerSurfaceMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring surfaceIdValue = StdStringToJString(env, surfaceId);
    env->CallVoidMethod(m_bridge, m_registerSurfaceMethod, surfaceIdValue);

    if (env->ExceptionCheck())
    {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    if (surfaceIdValue) env->DeleteLocalRef(surfaceIdValue);
    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::UnregisterSurface(const std::string& surfaceId)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_unregisterSurfaceMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring surfaceIdValue = StdStringToJString(env, surfaceId);
    env->CallVoidMethod(m_bridge, m_unregisterSurfaceMethod, surfaceIdValue);

    if (env->ExceptionCheck())
    {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    if (surfaceIdValue) env->DeleteLocalRef(surfaceIdValue);
    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::CommitSurface(const std::string& surfaceId, const std::string& opsJson)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);

    if (!env || !EnsureBridgeLocked(env) || !m_commitSurfaceMethod)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "CommitSurface failed before CallVoidMethod");
        DetachIfNeeded(didAttach);
        return;
    }

    jstring surfaceIdValue = StdStringToJString(env, surfaceId);
    jstring opsValue = StdStringToJString(env, opsJson);

    env->CallVoidMethod(m_bridge, m_commitSurfaceMethod, surfaceIdValue, opsValue);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception in CommitSurface");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    if (surfaceIdValue) env->DeleteLocalRef(surfaceIdValue);
    if (opsValue) env->DeleteLocalRef(opsValue);
    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::RegisterSideDrawerWithIcon(const std::string& id, const std::string& scriptId, const std::string& title, const std::string& iconRegistrationJson)
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_registerSideDrawerWithIconMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring idString = StdStringToJString(env, id);
    jstring scriptIdString = StdStringToJString(env, scriptId);
    jstring titleString = StdStringToJString(env, title);
    jstring iconRegistrationString = StdStringToJString(env, iconRegistrationJson);

    env->CallVoidMethod(m_bridge, m_registerSideDrawerWithIconMethod, idString, scriptIdString, titleString, iconRegistrationString);

    if (env->ExceptionCheck())
    {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    if (idString) env->DeleteLocalRef(idString);
    if (scriptIdString) env->DeleteLocalRef(scriptIdString);
    if (titleString) env->DeleteLocalRef(titleString);
    if (iconRegistrationString) env->DeleteLocalRef(iconRegistrationString);

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::RequestWorkletFrame()
{
    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_requestWorkletFrameMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    env->CallVoidMethod(m_bridge, m_requestWorkletFrameMethod);
    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception while requesting a worklet frame");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    DetachIfNeeded(didAttach);
}

void SpotifyPlusEngine::DispatchWorkletUpdates(int64_t frameTimeNanos, const std::vector<WorkletViewUpdate>& updates)
{
    if (updates.empty()) return;

    std::lock_guard<std::mutex> lock(m_mutex);

    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_dispatchWorkletUpdatesMethod)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass)
    {
        if (env->ExceptionCheck()) env->ExceptionClear();
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to resolve java.lang.String for worklet updates");
        DetachIfNeeded(didAttach);
        return;
    }

    const jsize count = static_cast<jsize>(updates.size());
    jobjectArray surfaceIds = env->NewObjectArray(count, stringClass, nullptr);
    jintArray nodeIds = env->NewIntArray(count);
    jobjectArray properties = env->NewObjectArray(count, stringClass, nullptr);
    jintArray valueTypes = env->NewIntArray(count);
    jdoubleArray numberValues = env->NewDoubleArray(count);
    jobjectArray stringValues = env->NewObjectArray(count, stringClass, nullptr);

    if (!surfaceIds || !nodeIds || !properties || !valueTypes || !numberValues || !stringValues)
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Failed to allocate worklet update arrays");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(stringClass);
        DetachIfNeeded(didAttach);
        return;
    }

    std::vector<jint> nodeIdValues(updates.size());
    std::vector<jint> typeValues(updates.size());
    std::vector<jdouble> numberValueArray(updates.size());

    for (jsize i = 0; i < count; i++)
    {
        const WorkletViewUpdate& update = updates[static_cast<size_t>(i)];
        jstring surfaceId = StdStringToJString(env, update.surfaceId);
        jstring property = StdStringToJString(env, update.property);
        jstring stringValue = update.type == WorkletUpdateType::String
                                  ? StdStringToJString(env, update.stringValue)
                                  : nullptr;

        env->SetObjectArrayElement(surfaceIds, i, surfaceId);
        env->SetObjectArrayElement(properties, i, property);
        if (stringValue) env->SetObjectArrayElement(stringValues, i, stringValue);

        if (surfaceId) env->DeleteLocalRef(surfaceId);
        if (property) env->DeleteLocalRef(property);
        if (stringValue) env->DeleteLocalRef(stringValue);

        nodeIdValues[static_cast<size_t>(i)] = update.nodeId;
        typeValues[static_cast<size_t>(i)] = static_cast<jint>(update.type);
        numberValueArray[static_cast<size_t>(i)] = update.numberValue;
    }

    env->SetIntArrayRegion(nodeIds, 0, count, nodeIdValues.data());
    env->SetIntArrayRegion(valueTypes, 0, count, typeValues.data());
    env->SetDoubleArrayRegion(numberValues, 0, count, numberValueArray.data());

    env->CallVoidMethod(
        m_bridge,
        m_dispatchWorkletUpdatesMethod,
        static_cast<jlong>(frameTimeNanos),
        surfaceIds,
        nodeIds,
        properties,
        valueTypes,
        numberValues,
        stringValues);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception while dispatching worklet updates");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    env->DeleteLocalRef(surfaceIds);
    env->DeleteLocalRef(nodeIds);
    env->DeleteLocalRef(properties);
    env->DeleteLocalRef(valueTypes);
    env->DeleteLocalRef(numberValues);
    env->DeleteLocalRef(stringValues);
    env->DeleteLocalRef(stringClass);
    DetachIfNeeded(didAttach);
}

std::string SpotifyPlusEngine::MeasureWorkletView(const std::string& surfaceId, int32_t nodeId)
{
    std::lock_guard<std::mutex> lock(m_mutex);
    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_measureWorkletViewMethod)
    {
        DetachIfNeeded(didAttach);
        return "";
    }

    jstring surfaceIdValue = StdStringToJString(env, surfaceId);
    auto result = static_cast<jstring>(env->CallObjectMethod(
        m_bridge,
        m_measureWorkletViewMethod,
        surfaceIdValue,
        static_cast<jint>(nodeId)));

    std::string value;
    if (env->ExceptionCheck())
    {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    else
    {
        value = JStringToStdString(env, result);
    }

    if (result) env->DeleteLocalRef(result);
    if (surfaceIdValue) env->DeleteLocalRef(surfaceIdValue);
    DetachIfNeeded(didAttach);
    return value;
}

std::string SpotifyPlusEngine::GetWorkletRelativeCoords(
    const std::string& surfaceId,
    int32_t nodeId,
    double absoluteX,
    double absoluteY)
{
    std::lock_guard<std::mutex> lock(m_mutex);
    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_getWorkletRelativeCoordsMethod)
    {
        DetachIfNeeded(didAttach);
        return "";
    }

    jstring surfaceIdValue = StdStringToJString(env, surfaceId);
    auto result = static_cast<jstring>(env->CallObjectMethod(
        m_bridge,
        m_getWorkletRelativeCoordsMethod,
        surfaceIdValue,
        static_cast<jint>(nodeId),
        static_cast<jdouble>(absoluteX),
        static_cast<jdouble>(absoluteY)));

    std::string value;
    if (env->ExceptionCheck())
    {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    else
    {
        value = JStringToStdString(env, result);
    }

    if (result) env->DeleteLocalRef(result);
    if (surfaceIdValue) env->DeleteLocalRef(surfaceIdValue);
    DetachIfNeeded(didAttach);
    return value;
}

bool SpotifyPlusEngine::ScrollWorkletView(
    const std::string& surfaceId,
    int32_t nodeId,
    double x,
    double y,
    bool animated)
{
    std::lock_guard<std::mutex> lock(m_mutex);
    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_scrollWorkletViewMethod)
    {
        DetachIfNeeded(didAttach);
        return false;
    }

    jstring surfaceIdValue = StdStringToJString(env, surfaceId);
    jboolean result = env->CallBooleanMethod(
        m_bridge,
        m_scrollWorkletViewMethod,
        surfaceIdValue,
        static_cast<jint>(nodeId),
        static_cast<jdouble>(x),
        static_cast<jdouble>(y),
        animated ? JNI_TRUE : JNI_FALSE);

    if (env->ExceptionCheck())
    {
        env->ExceptionDescribe();
        env->ExceptionClear();
        result = JNI_FALSE;
    }

    if (surfaceIdValue) env->DeleteLocalRef(surfaceIdValue);
    DetachIfNeeded(didAttach);
    return result == JNI_TRUE;
}

bool SpotifyPlusEngine::DispatchWorkletCommand(
    const std::string& surfaceId,
    int32_t nodeId,
    const std::string& command,
    const std::string& argsJson)
{
    std::lock_guard<std::mutex> lock(m_mutex);
    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    if (!env || !EnsureBridgeLocked(env) || !m_dispatchWorkletCommandMethod)
    {
        DetachIfNeeded(didAttach);
        return false;
    }

    jstring surfaceIdValue = StdStringToJString(env, surfaceId);
    jstring commandValue = StdStringToJString(env, command);
    jstring argsValue = StdStringToJString(env, argsJson);
    jboolean result = env->CallBooleanMethod(
        m_bridge,
        m_dispatchWorkletCommandMethod,
        surfaceIdValue,
        static_cast<jint>(nodeId),
        commandValue,
        argsValue);

    if (env->ExceptionCheck())
    {
        env->ExceptionDescribe();
        env->ExceptionClear();
        result = JNI_FALSE;
    }

    if (surfaceIdValue) env->DeleteLocalRef(surfaceIdValue);
    if (commandValue) env->DeleteLocalRef(commandValue);
    if (argsValue) env->DeleteLocalRef(argsValue);
    DetachIfNeeded(didAttach);
    return result == JNI_TRUE;
}

void SpotifyPlusEngine::SetWorkletSourceActive(
    const std::string& sourceId,
    const std::string& configJson,
    bool active)
{
    std::lock_guard<std::mutex> lock(m_mutex);
    bool didAttach = false;
    JNIEnv* env = GetEnv(&didAttach);
    jmethodID method = active
                           ? m_activateWorkletSourceMethod
                           : m_deactivateWorkletSourceMethod;
    if (!env || !EnsureBridgeLocked(env))
    {
        DetachIfNeeded(didAttach);
        return;
    }

    method = active
                 ? m_activateWorkletSourceMethod
                 : m_deactivateWorkletSourceMethod;
    if (!method)
    {
        DetachIfNeeded(didAttach);
        return;
    }

    jstring sourceIdValue = StdStringToJString(env, sourceId);
    jstring configValue = StdStringToJString(env, configJson);
    env->CallVoidMethod(m_bridge, method, sourceIdValue, configValue);

    if (env->ExceptionCheck())
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Java exception while changing a worklet source subscription");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    if (sourceIdValue) env->DeleteLocalRef(sourceIdValue);
    if (configValue) env->DeleteLocalRef(configValue);
    DetachIfNeeded(didAttach);
}
