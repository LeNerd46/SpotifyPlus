#include <android/log.h>
#include <jni.h>

#include "spotifyengine.h"
#include "worklet-runtime.h"

static constexpr const char* TAG = "SpotifyPlus::Exports";

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*)
{
    __android_log_print(ANDROID_LOG_INFO, TAG, "JNI_OnLoad vm=%p", vm);
    SpotifyPlusEngine::Get().Initialize(vm);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_lenerd_spotifyplus_module_scripting_ScriptManager_initializeNativeBridge(JNIEnv* env, jobject, jobject bridge, jobjectArray args)
{
    bool result = SpotifyPlusEngine::Get().SetBridge(env, bridge, args);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL Java_com_lenerd_spotifyplus_module_scripting_SpotifyNativeBridge_sendEvent(JNIEnv* env, jclass, jstring type, jstring payload)
{
    const char* typeChars = type ? env->GetStringUTFChars(type, nullptr) : nullptr;
    const char* payloadChars = payload ? env->GetStringUTFChars(payload, nullptr) : nullptr;

    std::string typeStr = typeChars ? typeChars : "";
    std::string payloadStr = payloadChars ? payloadChars : "";

    __android_log_write(ANDROID_LOG_INFO, TAG, payloadChars);

    if (typeChars) env->ReleaseStringUTFChars(type, typeChars);
    if (payloadChars) env->ReleaseStringUTFChars(payload, payloadChars);

    SpotifyPlusEngine::Get().EmitEvent(typeStr, payloadStr);
}

extern "C" bool SpotifyPlus_SetEventHandler(napi_env env, napi_value callback)
{
    return SpotifyPlusEngine::Get().SetEventHandler(env, callback);
}

extern "C" void SpotifyPlus_LoadApk(const char* scriptId, const char* apkPath, const char* pluginClass)
{
    SpotifyPlusEngine::Get().LoadApk(scriptId ? scriptId : "", apkPath ? apkPath : "", pluginClass ? pluginClass : "");
}

extern "C" void SpotifyPlus_UnregisterScript(const char* scriptId)
{
    const char* safeScriptId = scriptId ? scriptId : "";
    SpotifyPlus_WorkletDisposeScript(safeScriptId);
    SpotifyPlusEngine::Get().UnregisterScript(safeScriptId);
}

extern "C" void SpotifyPlus_GetPlatformData(PlatformData* data)
{
    if (!data) return;
    *data = SpotifyPlusEngine::Get().GetPlatformData();
}

extern "C" void SpotifyPlus_GetSession(SessionData* data)
{
    if (!data) return;
    *data = SpotifyPlusEngine::Get().GetSession();
}

extern "C" void SpotifyPlus_Log(const char* message)
{
    SpotifyPlusEngine::Get().Log(message);
}

extern "C" void SpotifyPlus_GetCurrentTrack(SpotifyTrack* data)
{
    if (!data) return;
    *data = SpotifyPlusEngine::Get().GetCurrentTrack();
}

extern "C" void SpotifyPlus_GetTrack(const char* uri, SpotifyTrack* data)
{
    if (!data) return;
    *data = SpotifyPlusEngine::Get().GetTrack(uri ? uri : "");
}

extern "C" double SpotifyPlus_GetPlaybackPosition()
{
    return SpotifyPlusEngine::Get().GetPlaybackPosition();
}

extern "C" void SpotifyPlus_Seek(long position)
{
    SpotifyPlusEngine::Get().Seek(position);
}

extern "C" void SpotifyPlus_Play()
{
    SpotifyPlusEngine::Get().Play();
}

extern "C" void SpotifyPlus_Pause()
{
    SpotifyPlusEngine::Get().Pause();
}

extern "C" void SpotifyPlus_TogglePlay()
{
    SpotifyPlusEngine::Get().TogglePlay();
}

extern "C" void SpotifyPlus_SkipNext()
{
    SpotifyPlusEngine::Get().SkipNext();
}

extern "C" void SpotifyPlus_SkipPrevious()
{
    SpotifyPlusEngine::Get().SkipPrevious();
}

extern "C" void SpotifyPlus_Toast(const char* text, bool longLength)
{
    SpotifyPlusEngine::Get().Toast(text ? text : "", longLength);
}

extern "C" bool SpotifyPlus_Navigate(const char* uri, const char* target)
{
    return SpotifyPlusEngine::Get().Navigate(uri ? uri : "", target ? target : "auto");
}

// Retained for Node bridge binaries extracted by an earlier SpotifyPlus build.
extern "C" void SpotifyPlus_OpenUri(const char* uri)
{
    SpotifyPlusEngine::Get().Navigate(uri ? uri : "", "auto");
}

extern "C" bool SpotifyPlus_NavigateBack()
{
    return SpotifyPlusEngine::Get().NavigateBack();
}

extern "C" void SpotifyPlus_StorageSet(const char* scriptId, const char* key, const char* value)
{
    SpotifyPlusEngine::Get().StorageSet(scriptId ? scriptId : "", key ? key : "", value ? value : "");
}

extern "C" void SpotifyPlus_StorageGet(const char* scriptId, const char* key, StorageValueResult* data)
{
    if (!data) return;
    *data = SpotifyPlusEngine::Get().StorageGet(scriptId ? scriptId : "", key ? key : "");
}

extern "C" void SpotifyPlus_StorageRemove(const char* scriptId, const char* key)
{
    SpotifyPlusEngine::Get().StorageRemove(scriptId ? scriptId : "", key ? key : "");
}

extern "C" void SpotifyPlus_StorageWriteText(const char* scriptId, const char* path, const char* value)
{
    SpotifyPlusEngine::Get().StorageWriteText(scriptId ? scriptId : "", path ? path : "", value ? value : "");
}

extern "C" void SpotifyPlus_StorageWriteJson(const char* scriptId, const char* path, const char* value)
{
    SpotifyPlusEngine::Get().StorageWriteJson(scriptId ? scriptId : "", path ? path : "", value ? value : "");
}

extern "C" void SpotifyPlus_StorageWriteBinary(const char* scriptId, const char* path, const char* data)
{
    SpotifyPlusEngine::Get().StorageWriteBinary(scriptId ? scriptId : "", path ? path : "", data ? data : "");
}

extern "C" void SpotifyPlus_StorageRead(const char* scriptId, const char* path, StorageReadResult* data)
{
    if (!data) return;
    *data = SpotifyPlusEngine::Get().StorageRead(scriptId ? scriptId : "", path ? path : "");
}

extern "C" bool SpotifyPlus_GetDeveloperMode()
{
    return SpotifyPlusEngine::Get().GetDeveloperMode();
}

extern "C" void SpotifyPlus_SetDeveloperMode(bool enabled)
{
    SpotifyPlusEngine::Get().SetDeveloperMode(enabled);
}

extern "C" bool SpotifyPlus_PickLocalExtensionsFolder()
{
    return SpotifyPlusEngine::Get().PickLocalExtensionsFolder();
}

extern "C" void SpotifyPlus_GetLocalExtensionsFolderDisplayName(StorageValueResult* data)
{
    if (!data) return;
    std::string value = SpotifyPlusEngine::Get().GetLocalExtensionsFolderDisplayName();
    *data = StorageValueResult{!value.empty(), value};
}

extern "C" void SpotifyPlus_ListLocalExtensions(StorageValueResult* data)
{
    if (!data) return;
    *data = StorageValueResult{true, SpotifyPlusEngine::Get().ListLocalExtensions()};
}

extern "C" void SpotifyPlus_RefreshLocalExtensions(StorageValueResult* data)
{
    if (!data) return;
    *data = StorageValueResult{true, SpotifyPlusEngine::Get().RefreshLocalExtensions()};
}

extern "C" void SpotifyPlus_RegisterContextMenu(const char* id, const char* scriptId, const char* title)
{
    SpotifyPlusEngine::Get().RegisterContextMenu(id ? id : "", scriptId ? scriptId : "", title ? title : "");
}

extern "C" void SpotifyPlus_RegisterSideDrawer(const char* id, const char* scriptId, const char* title)
{
    SpotifyPlusEngine::Get().RegisterSideDrawer(id ? id : "", scriptId ? scriptId : "", title ? title : "");
}

extern "C" void SpotifyPlus_RegisterSideDrawerWithIcon(const char* id, const char* scriptId, const char* title, const char* iconRegistrationJson)
{
    SpotifyPlusEngine::Get().RegisterSideDrawerWithIcon(id ? id : "", scriptId ? scriptId : "", title ? title : "", iconRegistrationJson ? iconRegistrationJson : "");
}

extern "C" void SpotifyPlus_RegisterSurface(const char* surfaceId)
{
    SpotifyPlusEngine::Get().RegisterSurface(surfaceId ? surfaceId : "");
}

extern "C" void SpotifyPlus_UnregisterSurface(const char* surfaceId)
{
    SpotifyPlusEngine::Get().UnregisterSurface(surfaceId ? surfaceId : "");
}

extern "C" void SpotifyPlus_CommitSurface(const char* surfaceId, const char* opsJson)
{
    SpotifyPlusEngine::Get().CommitSurface(surfaceId ? surfaceId : "", opsJson ? opsJson : "[]");
}
