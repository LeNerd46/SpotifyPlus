#include <android/log.h>
#include <dlfcn.h>
#include <node.h>
#include <node_api.h>

#include <string>

#include "spotifyengine.h"

typedef void (*SendToJavaFn)(const char*);
typedef char* (*PollFromJavaFn)();
typedef void (*FreeStringFn)(char*);

typedef bool (*SetEventHandlerFn)(napi_env, napi_value);
typedef void (*LoadApkFn)(const char* scriptId, const char* apkPath, const char* pluginClass);
typedef void (*UnregisterScriptFn)(const char* scriptId);
typedef void (*GetPlatformDataFn)(PlatformData* data);
typedef void (*GetSessionFn)(SessionData* data);
typedef void (*LogFn)(const char*);

typedef void (*GetCurrentTrackFn)(SpotifyTrack* data);
typedef void (*GetTrackFn)(const char* uri, SpotifyTrack* data);
typedef double (*GetPlaybackPositionFn)();
typedef void (*SeekFn)(long);
typedef void (*PlayFn)();
typedef void (*PauseFn)();
typedef void (*TogglePlayFn)();
typedef void (*SkipNextFn)();
typedef void (*SkipPreviousFn)();

typedef void (*ToastFn)(const char* text, bool longLength);
typedef bool (*NavigateFn)(const char* uri, const char* target);
typedef bool (*NavigateBackFn)();
typedef void (*LegacyOpenUriFn)(const char* uri);
typedef void (*StorageSetFn)(const char* scriptId, const char* key, const char* value);
typedef void (*StorageGetFn)(const char* scriptId, const char* key, StorageValueResult* data);
typedef void (*StorageRemoveFn)(const char* scriptId, const char* key);
typedef void (*StorageWriteTextFn)(const char* scriptId, const char* path, const char* value);
typedef void (*StorageWriteJsonFn)(const char* scriptId, const char* path, const char* value);
typedef void (*StorageWriteBinaryFn)(const char* scriptId, const char* path, const char* value);
typedef void (*StorageReadFn)(const char* scriptId, const char* path, StorageReadResult* data);
typedef bool (*GetDeveloperModeFn)();
typedef void (*SetDeveloperModeFn)(bool enabled);
typedef bool (*PickLocalExtensionsFolderFn)();
typedef void (*GetLocalExtensionsFolderDisplayNameFn)(StorageValueResult* data);
typedef void (*ListLocalExtensionsFn)(StorageValueResult* data);
typedef void (*RefreshLocalExtensionsFn)(StorageValueResult* data);

typedef void (*RegisterContextMenuFn)(const char* id, const char* scriptId, const char* title);
typedef void (*RegisterSideDrawerFn)(const char* id, const char* scriptId, const char* title);
typedef void (*RegisterSideDrawerWithIconFn)(const char* id, const char* scriptId, const char* title, const char* iconRegistrationJson);

typedef void (*RegisterSurfaceFn)(const char* surfaceId);
typedef void (*UnregisterSurfaceFn)(const char* surfaceId);
typedef void (*CommitSurfaceFn)(const char* surfaceId, const char* opsJson);

typedef bool (*WorkletConfigurePlatformFn)(void* platform);
typedef bool (*WorkletIsAvailableFn)();
typedef bool (*WorkletCreateContextFn)(const char* scriptId, uint64_t generation);
typedef bool (*WorkletDisposeContextFn)(const char* scriptId, uint64_t generation);
typedef bool (*WorkletDisposeScriptFn)(const char* scriptId);
typedef bool (*WorkletRegisterFn)(const char* scriptId, uint64_t generation, const char* workletId, const char* source, const char* closureJson);
typedef bool (*WorkletUnregisterFn)(const char* scriptId, uint64_t generation, const char* workletId);
typedef bool (*WorkletInstallGlobalsFn)(const char* scriptId, uint64_t generation, const char* moduleName, const char* source);
typedef bool (*WorkletScheduleFn)(const char* scriptId, uint64_t generation, const char* workletId, const char* argsJson);
typedef bool (*WorkletRegisterMapperFn)(const char* scriptId, uint64_t generation, const char* mapperId, const char* workletId, const char* surfaceId, int32_t nodeId, int32_t priority, bool runEveryFrame);
typedef bool (*WorkletUnregisterMapperFn)(const char* scriptId, uint64_t generation, const char* mapperId);
typedef bool (*WorkletSetSharedValueFn)(const char* scriptId, uint64_t generation, const char* sharedValueId, const char* valueJson);
typedef char* (*WorkletGetSharedValueFn)(const char* scriptId, uint64_t generation, const char* sharedValueId);
typedef bool (*WorkletDeleteSharedValueFn)(const char* scriptId, uint64_t generation, const char* sharedValueId);
typedef bool (*WorkletCancelAnimationFn)(const char* scriptId, uint64_t generation, const char* sharedValueId);
typedef bool (*WorkletGetReducedMotionFn)();
typedef bool (*WorkletSetReducedMotionOverrideFn)(const char* scriptId, uint64_t generation, const char* modeJson);
typedef bool (*WorkletRegisterSourceFn)(const char* scriptId, uint64_t generation, const char* sourceId, const char* sharedValueId, const char* configJson);
typedef bool (*WorkletUnregisterSourceFn)(const char* scriptId, uint64_t generation, const char* sourceId, const char* sharedValueId);
typedef char* (*WorkletTakeErrorJsonFn)();
typedef void (*WorkletFreeStringFn)(char* value);

static SetEventHandlerFn g_setEventHandler = nullptr;
static GetPlatformDataFn g_getPlatformData = nullptr;
static GetSessionFn g_getSession = nullptr;
static LogFn g_log = nullptr;

static GetCurrentTrackFn g_getCurrentTrack = nullptr;
static GetTrackFn g_getTrack = nullptr;
static LoadApkFn g_loadApk = nullptr;
static UnregisterScriptFn g_unregisterScript = nullptr;
static GetPlaybackPositionFn g_getPlaybackPosition = nullptr;
static SeekFn g_seek = nullptr;
static PlayFn g_play = nullptr;
static PauseFn g_pause = nullptr;
static TogglePlayFn g_togglePlay = nullptr;
static SkipNextFn g_skipNext = nullptr;
static SkipPreviousFn g_skipPrevious = nullptr;

static ToastFn g_toast = nullptr;
static NavigateFn g_navigate = nullptr;
static NavigateBackFn g_navigateBack = nullptr;
static LegacyOpenUriFn g_legacyOpenUri = nullptr;
static StorageSetFn g_storageSet = nullptr;
static StorageGetFn g_storageGet = nullptr;
static StorageRemoveFn g_storageRemove = nullptr;
static StorageWriteTextFn g_storageWriteText = nullptr;
static StorageWriteJsonFn g_storageWriteJson = nullptr;
static StorageWriteBinaryFn g_storageWriteBinary = nullptr;
static StorageReadFn g_storageRead = nullptr;
static GetDeveloperModeFn g_getDeveloperMode = nullptr;
static SetDeveloperModeFn g_setDeveloperMode = nullptr;
static PickLocalExtensionsFolderFn g_pickLocalExtensionsFolder = nullptr;
static GetLocalExtensionsFolderDisplayNameFn g_getLocalExtensionsFolderDisplayName = nullptr;
static ListLocalExtensionsFn g_listLocalExtensions = nullptr;
static RefreshLocalExtensionsFn g_refreshLocalExtensions = nullptr;

static RegisterContextMenuFn g_registerContextMenu = nullptr;
static RegisterSideDrawerFn g_registerSideDrawer = nullptr;
static RegisterSideDrawerWithIconFn g_registerSideDrawerWithIcon = nullptr;

static RegisterSurfaceFn g_registerSurface = nullptr;
static UnregisterSurfaceFn g_unregisterSurface = nullptr;
static CommitSurfaceFn g_commitSurface = nullptr;

static WorkletConfigurePlatformFn g_workletConfigurePlatform = nullptr;
static WorkletIsAvailableFn g_workletIsAvailable = nullptr;
static WorkletCreateContextFn g_workletCreateContext = nullptr;
static WorkletDisposeContextFn g_workletDisposeContext = nullptr;
static WorkletDisposeScriptFn g_workletDisposeScript = nullptr;
static WorkletRegisterFn g_workletRegister = nullptr;
static WorkletUnregisterFn g_workletUnregister = nullptr;
static WorkletInstallGlobalsFn g_workletInstallGlobals = nullptr;
static WorkletScheduleFn g_workletSchedule = nullptr;
static WorkletRegisterMapperFn g_workletRegisterMapper = nullptr;
static WorkletUnregisterMapperFn g_workletUnregisterMapper = nullptr;
static WorkletSetSharedValueFn g_workletSetSharedValue = nullptr;
static WorkletGetSharedValueFn g_workletGetSharedValue = nullptr;
static WorkletDeleteSharedValueFn g_workletDeleteSharedValue = nullptr;
static WorkletCancelAnimationFn g_workletCancelAnimation = nullptr;
static WorkletGetReducedMotionFn g_workletGetReducedMotion = nullptr;
static WorkletSetReducedMotionOverrideFn g_workletSetReducedMotionOverride = nullptr;
static WorkletRegisterSourceFn g_workletRegisterSource = nullptr;
static WorkletUnregisterSourceFn g_workletUnregisterSource = nullptr;
static WorkletTakeErrorJsonFn g_workletTakeErrorJson = nullptr;
static WorkletFreeStringFn g_workletFreeString = nullptr;

static SendToJavaFn g_sendToJava = nullptr;
static PollFromJavaFn g_pollFromJava = nullptr;
static FreeStringFn g_freeString = nullptr;
static bool g_symbolsResolved = false;
static void* g_nativeLibHandle = nullptr;
static const char* TAG = "SpotifyPlusBridge";

static napi_value CreateJsString(napi_env env, const std::string& value)
{
    napi_value result;
    napi_create_string_utf8(env, value.c_str(), NAPI_AUTO_LENGTH, &result);
    return result;
}

static std::string GetStringArg(napi_env env, napi_value value)
{
    size_t strSize = 0;
    napi_get_value_string_utf8(env, value, nullptr, 0, &strSize);

    std::string result;
    result.resize(strSize + 1);
    napi_get_value_string_utf8(env, value, result.data(), strSize + 1, &strSize);
    result.resize(strSize);
    return result;
}

static napi_value BuildTrackResult(napi_env env, const SpotifyTrack& track)
{
    napi_value result;
    napi_create_object(env, &result);

    napi_set_named_property(env, result, "title", CreateJsString(env, track.title));
    napi_set_named_property(env, result, "artist", CreateJsString(env, track.artist));
    napi_set_named_property(env, result, "uri", CreateJsString(env, track.uri));
    napi_set_named_property(env, result, "id", CreateJsString(env, track.id));

    napi_value explicitValue;
    napi_get_boolean(env, track.explicitTrack, &explicitValue);
    napi_set_named_property(env, result, "explicit", explicitValue);

    napi_value trackNumber;
    napi_create_int32(env, track.trackNumber, &trackNumber);
    napi_set_named_property(env, result, "trackNumber", trackNumber);

    napi_value duration;
    napi_create_int64(env, track.durationMs, &duration);
    napi_set_named_property(env, result, "durationMs", duration);

    napi_value artistsArray;
    napi_create_array_with_length(env, track.artists.size(), &artistsArray);
    for (size_t i = 0; i < track.artists.size(); i++)
    {
        napi_set_element(env, artistsArray, i, CreateJsString(env, track.artists[i]));
    }
    napi_set_named_property(env, result, "artists", artistsArray);

    napi_value albumObject;
    napi_create_object(env, &albumObject);
    napi_set_named_property(env, albumObject, "title", CreateJsString(env, track.album.title));
    napi_set_named_property(env, albumObject, "artist", CreateJsString(env, track.album.artist));
    napi_set_named_property(env, albumObject, "release", CreateJsString(env, track.album.date));
    napi_set_named_property(env, albumObject, "image", CreateJsString(env, track.album.image));
    napi_set_named_property(env, result, "album", albumObject);

    return result;
}

static bool resolve_symbols()
{
    if (g_symbolsResolved)
    {
        return g_setEventHandler && g_loadApk && g_getPlatformData && g_getSession && g_log && g_getCurrentTrack && g_getTrack && g_getPlaybackPosition && g_seek && g_play && g_pause && g_togglePlay && g_skipNext && g_skipPrevious && g_toast && (g_navigate || g_legacyOpenUri) && g_storageSet && g_storageGet && g_storageRemove && g_storageWriteText && g_storageWriteJson && g_storageWriteBinary && g_storageRead && g_getDeveloperMode && g_setDeveloperMode && g_pickLocalExtensionsFolder && g_getLocalExtensionsFolderDisplayName && g_listLocalExtensions && g_refreshLocalExtensions && g_unregisterScript && g_workletConfigurePlatform && g_workletIsAvailable && g_workletCreateContext && g_workletDisposeContext && g_workletDisposeScript && g_workletRegister && g_workletUnregister && g_workletInstallGlobals && g_workletSchedule && g_workletRegisterMapper && g_workletUnregisterMapper && g_workletSetSharedValue && g_workletGetSharedValue && g_workletDeleteSharedValue && g_workletCancelAnimation && g_workletGetReducedMotion && g_workletSetReducedMotionOverride && g_workletRegisterSource && g_workletUnregisterSource && g_workletTakeErrorJson && g_workletFreeString;
    }

    dlerror();

    g_nativeLibHandle = dlopen("libnative-lib.so", RTLD_NOW | RTLD_GLOBAL);
    if (!g_nativeLibHandle)
    {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "dlopen(libnative-lib.so) failed: %s", dlerror());
        g_symbolsResolved = true;
        return false;
    }

    g_setEventHandler = (SetEventHandlerFn)dlsym(g_nativeLibHandle, "SpotifyPlus_SetEventHandler");
    g_loadApk = (LoadApkFn)dlsym(g_nativeLibHandle, "SpotifyPlus_LoadApk");
    g_unregisterScript = (UnregisterScriptFn)dlsym(g_nativeLibHandle, "SpotifyPlus_UnregisterScript");
    g_getPlatformData = (GetPlatformDataFn)dlsym(g_nativeLibHandle, "SpotifyPlus_GetPlatformData");
    g_getSession = (GetSessionFn)dlsym(g_nativeLibHandle, "SpotifyPlus_GetSession");
    g_log = (LogFn)dlsym(g_nativeLibHandle, "SpotifyPlus_Log");

    g_getCurrentTrack = (GetCurrentTrackFn)dlsym(g_nativeLibHandle, "SpotifyPlus_GetCurrentTrack");
    g_getTrack = (GetTrackFn)dlsym(g_nativeLibHandle, "SpotifyPlus_GetTrack");
    g_getPlaybackPosition = (GetPlaybackPositionFn)dlsym(g_nativeLibHandle, "SpotifyPlus_GetPlaybackPosition");
    g_seek = (SeekFn)dlsym(g_nativeLibHandle, "SpotifyPlus_Seek");
    g_play = (PlayFn)dlsym(g_nativeLibHandle, "SpotifyPlus_Play");
    g_pause = (PauseFn)dlsym(g_nativeLibHandle, "SpotifyPlus_Pause");
    g_togglePlay = (TogglePlayFn)dlsym(g_nativeLibHandle, "SpotifyPlus_TogglePlay");
    g_skipNext = (SkipNextFn)dlsym(g_nativeLibHandle, "SpotifyPlus_SkipNext");
    g_skipPrevious = (SkipPreviousFn)dlsym(g_nativeLibHandle, "SpotifyPlus_SkipPrevious");

    g_toast = (ToastFn)dlsym(g_nativeLibHandle, "SpotifyPlus_Toast");
    g_navigate = (NavigateFn)dlsym(g_nativeLibHandle, "SpotifyPlus_Navigate");
    g_navigateBack = (NavigateBackFn)dlsym(g_nativeLibHandle, "SpotifyPlus_NavigateBack");
    g_legacyOpenUri = (LegacyOpenUriFn)dlsym(g_nativeLibHandle, "SpotifyPlus_OpenUri");
    g_storageSet = (StorageSetFn)dlsym(g_nativeLibHandle, "SpotifyPlus_StorageSet");
    g_storageGet = (StorageGetFn)dlsym(g_nativeLibHandle, "SpotifyPlus_StorageGet");
    g_storageRemove = (StorageRemoveFn)dlsym(g_nativeLibHandle, "SpotifyPlus_StorageRemove");
    g_storageWriteText = (StorageWriteTextFn)dlsym(g_nativeLibHandle, "SpotifyPlus_StorageWriteText");
    g_storageWriteJson = (StorageWriteJsonFn)dlsym(g_nativeLibHandle, "SpotifyPlus_StorageWriteJson");
    g_storageWriteBinary = (StorageWriteBinaryFn)dlsym(g_nativeLibHandle, "SpotifyPlus_StorageWriteBinary");
    g_storageRead = (StorageReadFn)dlsym(g_nativeLibHandle, "SpotifyPlus_StorageRead");
    g_getDeveloperMode = (GetDeveloperModeFn)dlsym(g_nativeLibHandle, "SpotifyPlus_GetDeveloperMode");
    g_setDeveloperMode = (SetDeveloperModeFn)dlsym(g_nativeLibHandle, "SpotifyPlus_SetDeveloperMode");
    g_pickLocalExtensionsFolder = (PickLocalExtensionsFolderFn)dlsym(g_nativeLibHandle, "SpotifyPlus_PickLocalExtensionsFolder");
    g_getLocalExtensionsFolderDisplayName = (GetLocalExtensionsFolderDisplayNameFn)dlsym(g_nativeLibHandle, "SpotifyPlus_GetLocalExtensionsFolderDisplayName");
    g_listLocalExtensions = (ListLocalExtensionsFn)dlsym(g_nativeLibHandle, "SpotifyPlus_ListLocalExtensions");
    g_refreshLocalExtensions = (RefreshLocalExtensionsFn)dlsym(g_nativeLibHandle, "SpotifyPlus_RefreshLocalExtensions");

    g_registerContextMenu = (RegisterContextMenuFn)dlsym(g_nativeLibHandle, "SpotifyPlus_RegisterContextMenu");
    g_registerSideDrawer = (RegisterSideDrawerFn)dlsym(g_nativeLibHandle, "SpotifyPlus_RegisterSideDrawer");
    g_registerSideDrawerWithIcon = (RegisterSideDrawerWithIconFn)dlsym(g_nativeLibHandle, "SpotifyPlus_RegisterSideDrawerWithIcon");

    g_registerSurface = (RegisterSurfaceFn)dlsym(g_nativeLibHandle, "SpotifyPlus_RegisterSurface");
    g_unregisterSurface = (UnregisterSurfaceFn)dlsym(g_nativeLibHandle, "SpotifyPlus_UnregisterSurface");
    g_commitSurface = (CommitSurfaceFn)dlsym(g_nativeLibHandle, "SpotifyPlus_CommitSurface");

    g_workletConfigurePlatform = (WorkletConfigurePlatformFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletConfigurePlatform");
    g_workletIsAvailable = (WorkletIsAvailableFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletIsAvailable");
    g_workletCreateContext = (WorkletCreateContextFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletCreateContext");
    g_workletDisposeContext = (WorkletDisposeContextFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletDisposeContext");
    g_workletDisposeScript = (WorkletDisposeScriptFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletDisposeScript");
    g_workletRegister = (WorkletRegisterFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletRegister");
    g_workletUnregister = (WorkletUnregisterFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletUnregister");
    g_workletInstallGlobals = (WorkletInstallGlobalsFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletInstallGlobals");
    g_workletSchedule = (WorkletScheduleFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletSchedule");
    g_workletRegisterMapper = (WorkletRegisterMapperFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletRegisterMapper");
    g_workletUnregisterMapper = (WorkletUnregisterMapperFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletUnregisterMapper");
    g_workletSetSharedValue = (WorkletSetSharedValueFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletSetSharedValue");
    g_workletGetSharedValue = (WorkletGetSharedValueFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletGetSharedValue");
    g_workletDeleteSharedValue = (WorkletDeleteSharedValueFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletDeleteSharedValue");
    g_workletCancelAnimation = (WorkletCancelAnimationFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletCancelAnimation");
    g_workletGetReducedMotion = (WorkletGetReducedMotionFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletGetReducedMotion");
    g_workletSetReducedMotionOverride = (WorkletSetReducedMotionOverrideFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletSetReducedMotionOverride");
    g_workletRegisterSource = (WorkletRegisterSourceFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletRegisterSource");
    g_workletUnregisterSource = (WorkletUnregisterSourceFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletUnregisterSource");
    g_workletTakeErrorJson = (WorkletTakeErrorJsonFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletTakeErrorJson");
    g_workletFreeString = (WorkletFreeStringFn)dlsym(g_nativeLibHandle, "SpotifyPlus_WorkletFreeString");

    g_symbolsResolved = true;

    if (!g_setEventHandler || !g_loadApk || !g_getPlatformData || !g_getSession || !g_log || !g_getCurrentTrack || !g_getTrack || !g_getPlaybackPosition || !g_seek || !g_play || !g_pause || !g_togglePlay || !g_skipNext || !g_skipPrevious || !g_toast || (!g_navigate && !g_legacyOpenUri) || !g_storageSet || !g_storageGet || !g_storageRemove || !g_storageWriteText || !g_storageWriteJson || !g_storageWriteBinary || !g_storageRead || !g_getDeveloperMode || !g_setDeveloperMode || !g_pickLocalExtensionsFolder || !g_getLocalExtensionsFolderDisplayName || !g_listLocalExtensions || !g_refreshLocalExtensions || !g_registerSurface || !g_unregisterSurface || !g_commitSurface || !g_unregisterScript || !g_workletConfigurePlatform || !g_workletIsAvailable || !g_workletCreateContext || !g_workletDisposeContext || !g_workletDisposeScript || !g_workletRegister || !g_workletUnregister || !g_workletInstallGlobals || !g_workletSchedule || !g_workletRegisterMapper || !g_workletUnregisterMapper || !g_workletSetSharedValue || !g_workletGetSharedValue || !g_workletDeleteSharedValue || !g_workletCancelAnimation || !g_workletGetReducedMotion || !g_workletSetReducedMotionOverride || !g_workletRegisterSource || !g_workletUnregisterSource || !g_workletTakeErrorJson || !g_workletFreeString)
    {
        const char* err = dlerror();
        __android_log_print(ANDROID_LOG_ERROR, TAG, "dlsym failed: %s", err ? err : "null");
        return false;
    }

    return true;
}

static napi_value setEventHandler(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 1 || !resolve_symbols()) return undefined;

    bool result = g_setEventHandler(env, args[0]);
    if (!result) __android_log_write(ANDROID_LOG_ERROR, TAG, "setEventHandler failed");
    return undefined;
}

static napi_value loadApk(napi_env env, napi_callback_info info)
{
    size_t argc = 3;
    napi_value args[3];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 3 || !resolve_symbols()) return undefined;

    std::string scriptId = GetStringArg(env, args[0]);
    std::string apkPath = GetStringArg(env, args[1]);
    std::string pluginClass = GetStringArg(env, args[2]);

    g_loadApk(scriptId.c_str(), apkPath.c_str(), pluginClass.c_str());

    return undefined;
}

static napi_value unregisterScript(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 1 || !resolve_symbols()) return undefined;

    std::string scriptId = GetStringArg(env, args[0]);
    g_unregisterScript(scriptId.c_str());

    return undefined;
}

static napi_value getPlatformData(napi_env env, napi_callback_info info)
{
    napi_value result;
    if (!resolve_symbols())
    {
        napi_get_undefined(env, &result);
        return result;
    }

    PlatformData platformData{};
    g_getPlatformData(&platformData);

    napi_create_object(env, &result);
    napi_set_named_property(env, result, "clientVersion", CreateJsString(env, platformData.clientVersion));
    napi_set_named_property(env, result, "osName", CreateJsString(env, platformData.osName));
    napi_set_named_property(env, result, "osVersion", CreateJsString(env, platformData.osVersion));

    napi_value sdkVersion;
    napi_create_int32(env, platformData.sdkVersion, &sdkVersion);
    napi_set_named_property(env, result, "sdkVersion", sdkVersion);
    return result;
}

static napi_value getAccessToken(napi_env env, napi_callback_info info)
{
    napi_value result;
    if (!resolve_symbols())
    {
        napi_get_undefined(env, &result);
        return result;
    }

    SessionData session{};
    g_getSession(&session);
    napi_create_string_utf8(env, session.accessToken.c_str(), NAPI_AUTO_LENGTH, &result);
    return result;
}

static napi_value log(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 1 || !resolve_symbols()) return undefined;
    g_log(GetStringArg(env, args[0]).c_str());
    return undefined;
}

static napi_value getCurrentTrack(napi_env env, napi_callback_info info)
{
    napi_value result;
    if (!resolve_symbols())
    {
        napi_get_undefined(env, &result);
        return result;
    }

    SpotifyTrack track{};
    g_getCurrentTrack(&track);
    if (track.uri.empty() && track.title.empty())
    {
        napi_get_undefined(env, &result);
        return result;
    }

    return BuildTrackResult(env, track);
}

static napi_value getTrack(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1];
    napi_value result;
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 1 || !resolve_symbols())
    {
        napi_get_undefined(env, &result);
        return result;
    }

    std::string uri = GetStringArg(env, args[0]);
    SpotifyTrack track{};
    g_getTrack(uri.c_str(), &track);

    if (track.uri.empty() && track.title.empty())
    {
        napi_get_undefined(env, &result);
        return result;
    }

    return BuildTrackResult(env, track);
}

static napi_value getPlaybackPosition(napi_env env, napi_callback_info)
{
    napi_value result;
    if (!resolve_symbols())
    {
        napi_get_undefined(env, &result);
        return result;
    }

    double position = g_getPlaybackPosition();
    napi_create_double(env, position, &result);
    return result;
}

static napi_value seek(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 1 || !resolve_symbols()) return undefined;

    int64_t position = 0;
    if (napi_get_value_int64(env, args[0], &position) != napi_ok) return undefined;
    g_seek((long)position);
    return undefined;
}

static napi_value play(napi_env env, napi_callback_info)
{
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    if (resolve_symbols()) g_play();
    return undefined;
}

static napi_value pause(napi_env env, napi_callback_info)
{
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    if (resolve_symbols()) g_pause();
    return undefined;
}

static napi_value togglePlay(napi_env env, napi_callback_info)
{
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    if (resolve_symbols()) g_togglePlay();
    return undefined;
}

static napi_value skipNext(napi_env env, napi_callback_info)
{
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    if (resolve_symbols()) g_skipNext();
    return undefined;
}

static napi_value skipPrevious(napi_env env, napi_callback_info)
{
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    if (resolve_symbols()) g_skipPrevious();
    return undefined;
}

static napi_value toast(napi_env env, napi_callback_info info)
{
    size_t argc = 2;
    napi_value args[2];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 1 || !resolve_symbols()) return undefined;

    std::string text = GetStringArg(env, args[0]);
    bool longLength = false;
    if (argc > 1) napi_get_value_bool(env, args[1], &longLength);

    g_toast(text.c_str(), longLength);
    return undefined;
}

static napi_value navigate(napi_env env, napi_callback_info info)
{
    size_t argc = 2;
    napi_value args[2];
    napi_value result;
    napi_get_boolean(env, false, &result);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 2 || !resolve_symbols()) return result;

    std::string uri = GetStringArg(env, args[0]);
    std::string target = GetStringArg(env, args[1]);
    if (g_navigate)
    {
        napi_get_boolean(env, g_navigate(uri.c_str(), target.c_str()), &result);
    }
    else if (g_legacyOpenUri && target == "auto")
    {
        g_legacyOpenUri(uri.c_str());
        napi_get_boolean(env, true, &result);
    }
    return result;
}

static napi_value navigateBack(napi_env env, napi_callback_info)
{
    napi_value result;
    napi_get_boolean(env, false, &result);
    if (!resolve_symbols() || !g_navigateBack) return result;

    napi_get_boolean(env, g_navigateBack(), &result);
    return result;
}

static napi_value storageSet(napi_env env, napi_callback_info info)
{
    size_t argc = 3;
    napi_value args[3];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 3 || !resolve_symbols()) return undefined;

    std::string scriptId = GetStringArg(env, args[0]);
    std::string key = GetStringArg(env, args[1]);
    std::string value = GetStringArg(env, args[2]);
    g_storageSet(scriptId.c_str(), key.c_str(), value.c_str());
    return undefined;
}

static napi_value storageGet(napi_env env, napi_callback_info info)
{
    size_t argc = 2;
    napi_value args[2];
    napi_value result;
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 2 || !resolve_symbols())
    {
        napi_get_undefined(env, &result);
        return result;
    }

    std::string scriptId = GetStringArg(env, args[0]);
    std::string key = GetStringArg(env, args[1]);

    StorageValueResult value{};
    g_storageGet(scriptId.c_str(), key.c_str(), &value);
    if (!value.found)
    {
        napi_get_undefined(env, &result);
        return result;
    }

    napi_create_string_utf8(env, value.value.c_str(), NAPI_AUTO_LENGTH, &result);
    return result;
}

static napi_value storageRemove(napi_env env, napi_callback_info info)
{
    size_t argc = 2;
    napi_value args[2];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 2 || !resolve_symbols()) return undefined;

    std::string scriptId = GetStringArg(env, args[0]);
    std::string key = GetStringArg(env, args[1]);
    g_storageRemove(scriptId.c_str(), key.c_str());
    return undefined;
}

static napi_value storageWriteText(napi_env env, napi_callback_info info)
{
    size_t argc = 3;
    napi_value args[3];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 3 || !resolve_symbols()) return undefined;

    std::string scriptId = GetStringArg(env, args[0]);
    std::string path = GetStringArg(env, args[1]);
    std::string value = GetStringArg(env, args[2]);
    g_storageWriteText(scriptId.c_str(), path.c_str(), value.c_str());
    return undefined;
}

static napi_value storageWriteJson(napi_env env, napi_callback_info info)
{
    size_t argc = 3;
    napi_value args[3];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 3 || !resolve_symbols()) return undefined;

    std::string scriptId = GetStringArg(env, args[0]);
    std::string path = GetStringArg(env, args[1]);
    std::string value = GetStringArg(env, args[2]);
    g_storageWriteJson(scriptId.c_str(), path.c_str(), value.c_str());
    return undefined;
}

static napi_value storageWriteBinary(napi_env env, napi_callback_info info)
{
    size_t argc = 3;
    napi_value args[3];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 3 || !resolve_symbols()) return undefined;

    std::string scriptId = GetStringArg(env, args[0]);
    std::string path = GetStringArg(env, args[1]);
    std::string data = GetStringArg(env, args[2]);
    g_storageWriteBinary(scriptId.c_str(), path.c_str(), data.c_str());
    return undefined;
}

static napi_value storageRead(napi_env env, napi_callback_info info)
{
    size_t argc = 2;
    napi_value args[2];
    napi_value result;
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 2 || !resolve_symbols())
    {
        napi_get_undefined(env, &result);
        return result;
    }

    std::string scriptId = GetStringArg(env, args[0]);
    std::string path = GetStringArg(env, args[1]);

    StorageReadResult readResult{};
    g_storageRead(scriptId.c_str(), path.c_str(), &readResult);
    if (!readResult.found)
    {
        napi_get_undefined(env, &result);
        return result;
    }

    napi_create_object(env, &result);
    napi_set_named_property(env, result, "type", CreateJsString(env, readResult.type));
    napi_set_named_property(env, result, "value", CreateJsString(env, readResult.value));
    napi_set_named_property(env, result, "data", CreateJsString(env, readResult.data));
    return result;
}

static uint64_t GetGenerationArg(napi_env env, napi_value value)
{
    double generation = 0;
    if (napi_get_value_double(env, value, &generation) != napi_ok || generation < 0) return 0;
    return static_cast<uint64_t>(generation);
}

static napi_value CreateBoolean(napi_env env, bool value)
{
    napi_value result;
    napi_get_boolean(env, value, &result);
    return result;
}

static bool ParseJson(napi_env env, const std::string& json, napi_value* result)
{
    napi_value global;
    napi_value jsonObject;
    napi_value parse;
    napi_value input = CreateJsString(env, json);
    if (napi_get_global(env, &global) != napi_ok ||
        napi_get_named_property(env, global, "JSON", &jsonObject) != napi_ok ||
        napi_get_named_property(env, jsonObject, "parse", &parse) != napi_ok)
    {
        return false;
    }

    return napi_call_function(env, jsonObject, parse, 1, &input, result) == napi_ok;
}

static napi_value getDeveloperMode(napi_env env, napi_callback_info)
{
    napi_value result;
    bool enabled = resolve_symbols() && g_getDeveloperMode();
    napi_get_boolean(env, enabled, &result);
    return result;
}

static napi_value setDeveloperMode(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 1 || !resolve_symbols()) return undefined;

    bool enabled = false;
    napi_get_value_bool(env, args[0], &enabled);
    g_setDeveloperMode(enabled);
    return undefined;
}

static napi_value pickLocalExtensionsFolder(napi_env env, napi_callback_info)
{
    napi_value result;
    bool opened = resolve_symbols() && g_pickLocalExtensionsFolder();
    napi_get_boolean(env, opened, &result);
    return result;
}

static napi_value getLocalExtensionsFolderDisplayName(napi_env env, napi_callback_info)
{
    napi_value result;
    if (!resolve_symbols())
    {
        napi_get_undefined(env, &result);
        return result;
    }

    StorageValueResult value{};
    g_getLocalExtensionsFolderDisplayName(&value);
    if (!value.found)
    {
        napi_get_undefined(env, &result);
        return result;
    }

    return CreateJsString(env, value.value);
}

static napi_value listLocalExtensions(napi_env env, napi_callback_info)
{
    napi_value result;
    if (!resolve_symbols())
    {
        napi_create_string_utf8(env, "[]", NAPI_AUTO_LENGTH, &result);
        return result;
    }

    StorageValueResult value{};
    g_listLocalExtensions(&value);
    return CreateJsString(env, value.found ? value.value : "[]");
}

static napi_value refreshLocalExtensions(napi_env env, napi_callback_info)
{
    napi_value result;
    if (!resolve_symbols())
    {
        napi_create_string_utf8(env, "[]", NAPI_AUTO_LENGTH, &result);
        return result;
    }

    StorageValueResult value{};
    g_refreshLocalExtensions(&value);
    return CreateJsString(env, value.found ? value.value : "[]");
}

static napi_value registerContextMenu(napi_env env, napi_callback_info info)
{
    size_t argc = 3;
    napi_value args[3];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 3 || !resolve_symbols()) return undefined;

    std::string id = GetStringArg(env, args[0]);
    std::string scriptId = GetStringArg(env, args[1]);
    std::string title = GetStringArg(env, args[2]);
    g_registerContextMenu(id.c_str(), scriptId.c_str(), title.c_str());
    return undefined;
}

static napi_value registerSideDrawer(napi_env env, napi_callback_info info)
{
    size_t argc = 4;
    napi_value args[4];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 3 || !resolve_symbols()) return undefined;

    std::string id = GetStringArg(env, args[0]);
    std::string scriptId = GetStringArg(env, args[1]);
    std::string title = GetStringArg(env, args[2]);
    if (argc >= 4 && g_registerSideDrawerWithIcon)
    {
        std::string iconRegistrationJson = GetStringArg(env, args[3]);
        g_registerSideDrawerWithIcon(id.c_str(), scriptId.c_str(), title.c_str(), iconRegistrationJson.c_str());
    }
    else
    {
        g_registerSideDrawer(id.c_str(), scriptId.c_str(), title.c_str());
    }
    return undefined;
}

static napi_value registerSurface(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 1 || !resolve_symbols()) return undefined;
    std::string surfaceId = GetStringArg(env, args[0]);
    g_registerSurface(surfaceId.c_str());
    return undefined;
}

static napi_value unregisterSurface(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 1 || !resolve_symbols()) return undefined;
    std::string surfaceId = GetStringArg(env, args[0]);
    g_unregisterSurface(surfaceId.c_str());
    return undefined;
}

static napi_value commitSurface(napi_env env, napi_callback_info info)
{
    size_t argc = 2;
    napi_value args[2];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 2 || !resolve_symbols()) return undefined;
    std::string surfaceId = GetStringArg(env, args[0]);
    std::string opsJson = GetStringArg(env, args[1]);
    g_commitSurface(surfaceId.c_str(), opsJson.c_str());
    return undefined;
}

static napi_value isWorkletRuntimeAvailable(napi_env env, napi_callback_info)
{
    return CreateBoolean(env, resolve_symbols() && g_workletIsAvailable());
}

static napi_value createWorkletContext(napi_env env, napi_callback_info info)
{
    size_t argc = 2;
    napi_value args[2];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 2 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    return CreateBoolean(env, g_workletCreateContext(scriptId.c_str(), GetGenerationArg(env, args[1])));
}

static napi_value disposeWorkletContext(napi_env env, napi_callback_info info)
{
    size_t argc = 2;
    napi_value args[2];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 2 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    return CreateBoolean(env, g_workletDisposeContext(scriptId.c_str(), GetGenerationArg(env, args[1])));
}

static napi_value disposeWorkletScript(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 1 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    return CreateBoolean(env, g_workletDisposeScript(scriptId.c_str()));
}

static napi_value registerWorklet(napi_env env, napi_callback_info info)
{
    size_t argc = 5;
    napi_value args[5];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 5 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    std::string workletId = GetStringArg(env, args[2]);
    std::string source = GetStringArg(env, args[3]);
    std::string closureJson = GetStringArg(env, args[4]);
    return CreateBoolean(
        env,
        g_workletRegister(
            scriptId.c_str(),
            GetGenerationArg(env, args[1]),
            workletId.c_str(),
            source.c_str(),
            closureJson.c_str()));
}

static napi_value unregisterWorklet(napi_env env, napi_callback_info info)
{
    size_t argc = 3;
    napi_value args[3];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 3 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    std::string workletId = GetStringArg(env, args[2]);
    return CreateBoolean(
        env,
        g_workletUnregister(
            scriptId.c_str(),
            GetGenerationArg(env, args[1]),
            workletId.c_str()));
}

static napi_value installWorkletGlobals(napi_env env, napi_callback_info info)
{
    size_t argc = 4;
    napi_value args[4];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 4 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    std::string moduleName = GetStringArg(env, args[2]);
    std::string source = GetStringArg(env, args[3]);
    return CreateBoolean(
        env,
        g_workletInstallGlobals(
            scriptId.c_str(),
            GetGenerationArg(env, args[1]),
            moduleName.c_str(),
            source.c_str()));
}

static napi_value scheduleWorklet(napi_env env, napi_callback_info info)
{
    size_t argc = 4;
    napi_value args[4];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 4 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    std::string workletId = GetStringArg(env, args[2]);
    std::string argsJson = GetStringArg(env, args[3]);
    return CreateBoolean(
        env,
        g_workletSchedule(
            scriptId.c_str(),
            GetGenerationArg(env, args[1]),
            workletId.c_str(),
            argsJson.c_str()));
}

static napi_value registerWorkletMapper(napi_env env, napi_callback_info info)
{
    size_t argc = 8;
    napi_value args[8];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 8 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    std::string mapperId = GetStringArg(env, args[2]);
    std::string workletId = GetStringArg(env, args[3]);
    std::string surfaceId = GetStringArg(env, args[4]);
    int32_t nodeId = 0;
    int32_t priority = 0;
    bool runEveryFrame = false;
    napi_get_value_int32(env, args[5], &nodeId);
    napi_get_value_int32(env, args[6], &priority);
    napi_get_value_bool(env, args[7], &runEveryFrame);

    return CreateBoolean(
        env,
        g_workletRegisterMapper(
            scriptId.c_str(),
            GetGenerationArg(env, args[1]),
            mapperId.c_str(),
            workletId.c_str(),
            surfaceId.c_str(),
            nodeId,
            priority,
            runEveryFrame));
}

static napi_value unregisterWorkletMapper(napi_env env, napi_callback_info info)
{
    size_t argc = 3;
    napi_value args[3];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 3 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    std::string mapperId = GetStringArg(env, args[2]);
    return CreateBoolean(
        env,
        g_workletUnregisterMapper(
            scriptId.c_str(),
            GetGenerationArg(env, args[1]),
            mapperId.c_str()));
}

static napi_value setWorkletSharedValue(napi_env env, napi_callback_info info)
{
    size_t argc = 4;
    napi_value args[4];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 4 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    std::string sharedValueId = GetStringArg(env, args[2]);
    std::string valueJson = GetStringArg(env, args[3]);
    return CreateBoolean(
        env,
        g_workletSetSharedValue(
            scriptId.c_str(),
            GetGenerationArg(env, args[1]),
            sharedValueId.c_str(),
            valueJson.c_str()));
}

static napi_value getWorkletSharedValue(napi_env env, napi_callback_info info)
{
    size_t argc = 3;
    napi_value args[3];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 3 || !resolve_symbols()) return undefined;

    std::string scriptId = GetStringArg(env, args[0]);
    std::string sharedValueId = GetStringArg(env, args[2]);
    char* value = g_workletGetSharedValue(
        scriptId.c_str(),
        GetGenerationArg(env, args[1]),
        sharedValueId.c_str());
    if (!value) return undefined;

    napi_value result = CreateJsString(env, value);
    g_workletFreeString(value);
    return result;
}

static napi_value deleteWorkletSharedValue(napi_env env, napi_callback_info info)
{
    size_t argc = 3;
    napi_value args[3];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 3 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    std::string sharedValueId = GetStringArg(env, args[2]);
    return CreateBoolean(
        env,
        g_workletDeleteSharedValue(
            scriptId.c_str(),
            GetGenerationArg(env, args[1]),
            sharedValueId.c_str()));
}

static napi_value cancelWorkletAnimation(napi_env env, napi_callback_info info)
{
    size_t argc = 3;
    napi_value args[3];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 3 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    std::string sharedValueId = GetStringArg(env, args[2]);
    return CreateBoolean(
        env,
        g_workletCancelAnimation(
            scriptId.c_str(),
            GetGenerationArg(env, args[1]),
            sharedValueId.c_str()));
}

static napi_value getWorkletReducedMotion(napi_env env, napi_callback_info)
{
    return CreateBoolean(env, resolve_symbols() && g_workletGetReducedMotion());
}

static napi_value setWorkletReducedMotionOverride(napi_env env, napi_callback_info info)
{
    size_t argc = 3;
    napi_value args[3];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 3 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    std::string modeJson = GetStringArg(env, args[2]);
    return CreateBoolean(
        env,
        g_workletSetReducedMotionOverride(
            scriptId.c_str(),
            GetGenerationArg(env, args[1]),
            modeJson.c_str()));
}

static napi_value registerWorkletSource(napi_env env, napi_callback_info info)
{
    size_t argc = 5;
    napi_value args[5];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 5 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    std::string sourceId = GetStringArg(env, args[2]);
    std::string sharedValueId = GetStringArg(env, args[3]);
    std::string configJson = GetStringArg(env, args[4]);
    return CreateBoolean(
        env,
        g_workletRegisterSource(
            scriptId.c_str(),
            GetGenerationArg(env, args[1]),
            sourceId.c_str(),
            sharedValueId.c_str(),
            configJson.c_str()));
}

static napi_value unregisterWorkletSource(napi_env env, napi_callback_info info)
{
    size_t argc = 4;
    napi_value args[4];
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);
    if (argc < 4 || !resolve_symbols()) return CreateBoolean(env, false);

    std::string scriptId = GetStringArg(env, args[0]);
    std::string sourceId = GetStringArg(env, args[2]);
    std::string sharedValueId = GetStringArg(env, args[3]);
    return CreateBoolean(
        env,
        g_workletUnregisterSource(
            scriptId.c_str(),
            GetGenerationArg(env, args[1]),
            sourceId.c_str(),
            sharedValueId.c_str()));
}

static napi_value drainWorkletErrors(napi_env env, napi_callback_info)
{
    napi_value result;
    napi_create_array(env, &result);
    if (!resolve_symbols()) return result;

    uint32_t index = 0;
    while (char* errorJson = g_workletTakeErrorJson())
    {
        napi_value error;
        if (!ParseJson(env, errorJson, &error)) error = CreateJsString(env, errorJson);
        g_workletFreeString(errorJson);
        napi_set_element(env, result, index++, error);
    }

    return result;
}

static void configureWorkletPlatform(napi_env)
{
    if (!resolve_symbols()) return;

    v8::Isolate* isolate = v8::Isolate::GetCurrent();
    if (!isolate) return;

    node::Environment* environment = node::GetCurrentEnvironment(isolate->GetCurrentContext());
    node::MultiIsolatePlatform* platform = environment ? node::GetMultiIsolatePlatform(environment) : nullptr;
    if (!platform || !g_workletConfigurePlatform(platform))
    {
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Unable to configure the UI worklet V8 platform");
    }
}

static napi_value sendToJava(napi_env env, napi_callback_info info)
{
    size_t argc = 1;
    napi_value args[1];
    napi_value undefined;
    napi_get_undefined(env, &undefined);
    napi_get_cb_info(env, info, &argc, args, nullptr, nullptr);

    if (argc < 1 || !resolve_symbols()) return undefined;
    g_sendToJava(GetStringArg(env, args[0]).c_str());
    return undefined;
}

static napi_value pollFromJava(napi_env env, napi_callback_info)
{
    napi_value result;
    if (!resolve_symbols())
    {
        napi_get_undefined(env, &result);
        return result;
    }

    char* msg = g_pollFromJava();
    if (!msg)
    {
        napi_get_undefined(env, &result);
        return result;
    }

    napi_create_string_utf8(env, msg, NAPI_AUTO_LENGTH, &result);
    g_freeString(msg);
    return result;
}

static napi_value init(napi_env env, napi_value exports)
{
    napi_value fn;

    configureWorkletPlatform(env);

    napi_create_function(env, "setEventHandler", NAPI_AUTO_LENGTH, setEventHandler, nullptr, &fn);
    napi_set_named_property(env, exports, "setEventHandler", fn);

    napi_create_function(env, "sendToJava", NAPI_AUTO_LENGTH, sendToJava, nullptr, &fn);
    napi_set_named_property(env, exports, "sendToJava", fn);

    napi_create_function(env, "pollFromJava", NAPI_AUTO_LENGTH, pollFromJava, nullptr, &fn);
    napi_set_named_property(env, exports, "pollFromJava", fn);

    napi_create_function(env, "loadApk", NAPI_AUTO_LENGTH, loadApk, nullptr, &fn);
    napi_set_named_property(env, exports, "loadApk", fn);

    napi_create_function(env, "unregisterScript", NAPI_AUTO_LENGTH, unregisterScript, nullptr, &fn);
    napi_set_named_property(env, exports, "unregisterScript", fn);

    napi_create_function(env, "getPlatformData", NAPI_AUTO_LENGTH, getPlatformData, nullptr, &fn);
    napi_set_named_property(env, exports, "getPlatformData", fn);

    napi_create_function(env, "getAccessToken", NAPI_AUTO_LENGTH, getAccessToken, nullptr, &fn);
    napi_set_named_property(env, exports, "getAccessToken", fn);

    napi_create_function(env, "log", NAPI_AUTO_LENGTH, log, nullptr, &fn);
    napi_set_named_property(env, exports, "log", fn);

    napi_create_function(env, "getCurrentTrack", NAPI_AUTO_LENGTH, getCurrentTrack, nullptr, &fn);
    napi_set_named_property(env, exports, "getCurrentTrack", fn);

    napi_create_function(env, "getTrack", NAPI_AUTO_LENGTH, getTrack, nullptr, &fn);
    napi_set_named_property(env, exports, "getTrack", fn);

    napi_create_function(env, "getPlaybackPosition", NAPI_AUTO_LENGTH, getPlaybackPosition, nullptr, &fn);
    napi_set_named_property(env, exports, "getPlaybackPosition", fn);

    napi_create_function(env, "seek", NAPI_AUTO_LENGTH, seek, nullptr, &fn);
    napi_set_named_property(env, exports, "seek", fn);

    napi_create_function(env, "play", NAPI_AUTO_LENGTH, play, nullptr, &fn);
    napi_set_named_property(env, exports, "play", fn);

    napi_create_function(env, "pause", NAPI_AUTO_LENGTH, pause, nullptr, &fn);
    napi_set_named_property(env, exports, "pause", fn);

    napi_create_function(env, "togglePlay", NAPI_AUTO_LENGTH, togglePlay, nullptr, &fn);
    napi_set_named_property(env, exports, "togglePlay", fn);

    napi_create_function(env, "skipNext", NAPI_AUTO_LENGTH, skipNext, nullptr, &fn);
    napi_set_named_property(env, exports, "skipNext", fn);

    napi_create_function(env, "skipPrevious", NAPI_AUTO_LENGTH, skipPrevious, nullptr, &fn);
    napi_set_named_property(env, exports, "skipPrevious", fn);

    napi_create_function(env, "toast", NAPI_AUTO_LENGTH, toast, nullptr, &fn);
    napi_set_named_property(env, exports, "toast", fn);

    napi_create_function(env, "navigate", NAPI_AUTO_LENGTH, navigate, nullptr, &fn);
    napi_set_named_property(env, exports, "navigate", fn);

    napi_create_function(env, "navigateBack", NAPI_AUTO_LENGTH, navigateBack, nullptr, &fn);
    napi_set_named_property(env, exports, "navigateBack", fn);

    napi_create_function(env, "storageSet", NAPI_AUTO_LENGTH, storageSet, nullptr, &fn);
    napi_set_named_property(env, exports, "storageSet", fn);

    napi_create_function(env, "storageGet", NAPI_AUTO_LENGTH, storageGet, nullptr, &fn);
    napi_set_named_property(env, exports, "storageGet", fn);

    napi_create_function(env, "storageRemove", NAPI_AUTO_LENGTH, storageRemove, nullptr, &fn);
    napi_set_named_property(env, exports, "storageRemove", fn);

    napi_create_function(env, "storageWriteText", NAPI_AUTO_LENGTH, storageWriteText, nullptr, &fn);
    napi_set_named_property(env, exports, "storageWriteText", fn);

    napi_create_function(env, "storageWriteJson", NAPI_AUTO_LENGTH, storageWriteJson, nullptr, &fn);
    napi_set_named_property(env, exports, "storageWriteJson", fn);

    napi_create_function(env, "storageWriteBinary", NAPI_AUTO_LENGTH, storageWriteBinary, nullptr, &fn);
    napi_set_named_property(env, exports, "storageWriteBinary", fn);

    napi_create_function(env, "storageRead", NAPI_AUTO_LENGTH, storageRead, nullptr, &fn);
    napi_set_named_property(env, exports, "storageRead", fn);

    napi_create_function(env, "getDeveloperMode", NAPI_AUTO_LENGTH, getDeveloperMode, nullptr, &fn);
    napi_set_named_property(env, exports, "getDeveloperMode", fn);

    napi_create_function(env, "setDeveloperMode", NAPI_AUTO_LENGTH, setDeveloperMode, nullptr, &fn);
    napi_set_named_property(env, exports, "setDeveloperMode", fn);

    napi_create_function(env, "pickLocalExtensionsFolder", NAPI_AUTO_LENGTH, pickLocalExtensionsFolder, nullptr, &fn);
    napi_set_named_property(env, exports, "pickLocalExtensionsFolder", fn);

    napi_create_function(env, "getLocalExtensionsFolderDisplayName", NAPI_AUTO_LENGTH, getLocalExtensionsFolderDisplayName, nullptr, &fn);
    napi_set_named_property(env, exports, "getLocalExtensionsFolderDisplayName", fn);

    napi_create_function(env, "listLocalExtensions", NAPI_AUTO_LENGTH, listLocalExtensions, nullptr, &fn);
    napi_set_named_property(env, exports, "listLocalExtensions", fn);

    napi_create_function(env, "refreshLocalExtensions", NAPI_AUTO_LENGTH, refreshLocalExtensions, nullptr, &fn);
    napi_set_named_property(env, exports, "refreshLocalExtensions", fn);

    napi_create_function(env, "registerContextMenu", NAPI_AUTO_LENGTH, registerContextMenu, nullptr, &fn);
    napi_set_named_property(env, exports, "registerContextMenu", fn);

    napi_create_function(env, "registerSideDrawer", NAPI_AUTO_LENGTH, registerSideDrawer, nullptr, &fn);
    napi_set_named_property(env, exports, "registerSideDrawer", fn);

    napi_create_function(env, "registerSurface", NAPI_AUTO_LENGTH, registerSurface, nullptr, &fn);
    napi_set_named_property(env, exports, "registerSurface", fn);

    napi_create_function(env, "unregisterSurface", NAPI_AUTO_LENGTH, unregisterSurface, nullptr, &fn);
    napi_set_named_property(env, exports, "unregisterSurface", fn);

    napi_create_function(env, "commitSurface", NAPI_AUTO_LENGTH, commitSurface, nullptr, &fn);
    napi_set_named_property(env, exports, "commitSurface", fn);

    napi_create_function(env, "isWorkletRuntimeAvailable", NAPI_AUTO_LENGTH, isWorkletRuntimeAvailable, nullptr, &fn);
    napi_set_named_property(env, exports, "isWorkletRuntimeAvailable", fn);

    napi_create_function(env, "createWorkletContext", NAPI_AUTO_LENGTH, createWorkletContext, nullptr, &fn);
    napi_set_named_property(env, exports, "createWorkletContext", fn);

    napi_create_function(env, "disposeWorkletContext", NAPI_AUTO_LENGTH, disposeWorkletContext, nullptr, &fn);
    napi_set_named_property(env, exports, "disposeWorkletContext", fn);

    napi_create_function(env, "disposeWorkletScript", NAPI_AUTO_LENGTH, disposeWorkletScript, nullptr, &fn);
    napi_set_named_property(env, exports, "disposeWorkletScript", fn);

    napi_create_function(env, "registerWorklet", NAPI_AUTO_LENGTH, registerWorklet, nullptr, &fn);
    napi_set_named_property(env, exports, "registerWorklet", fn);

    napi_create_function(env, "unregisterWorklet", NAPI_AUTO_LENGTH, unregisterWorklet, nullptr, &fn);
    napi_set_named_property(env, exports, "unregisterWorklet", fn);

    napi_create_function(env, "installWorkletGlobals", NAPI_AUTO_LENGTH, installWorkletGlobals, nullptr, &fn);
    napi_set_named_property(env, exports, "installWorkletGlobals", fn);

    napi_create_function(env, "scheduleWorklet", NAPI_AUTO_LENGTH, scheduleWorklet, nullptr, &fn);
    napi_set_named_property(env, exports, "scheduleWorklet", fn);

    napi_create_function(env, "registerWorkletMapper", NAPI_AUTO_LENGTH, registerWorkletMapper, nullptr, &fn);
    napi_set_named_property(env, exports, "registerWorkletMapper", fn);

    napi_create_function(env, "unregisterWorkletMapper", NAPI_AUTO_LENGTH, unregisterWorkletMapper, nullptr, &fn);
    napi_set_named_property(env, exports, "unregisterWorkletMapper", fn);

    napi_create_function(env, "setWorkletSharedValue", NAPI_AUTO_LENGTH, setWorkletSharedValue, nullptr, &fn);
    napi_set_named_property(env, exports, "setWorkletSharedValue", fn);

    napi_create_function(env, "getWorkletSharedValue", NAPI_AUTO_LENGTH, getWorkletSharedValue, nullptr, &fn);
    napi_set_named_property(env, exports, "getWorkletSharedValue", fn);

    napi_create_function(env, "deleteWorkletSharedValue", NAPI_AUTO_LENGTH, deleteWorkletSharedValue, nullptr, &fn);
    napi_set_named_property(env, exports, "deleteWorkletSharedValue", fn);

    napi_create_function(env, "cancelWorkletAnimation", NAPI_AUTO_LENGTH, cancelWorkletAnimation, nullptr, &fn);
    napi_set_named_property(env, exports, "cancelWorkletAnimation", fn);

    napi_create_function(env, "getWorkletReducedMotion", NAPI_AUTO_LENGTH, getWorkletReducedMotion, nullptr, &fn);
    napi_set_named_property(env, exports, "getWorkletReducedMotion", fn);

    napi_create_function(env, "setWorkletReducedMotionOverride", NAPI_AUTO_LENGTH, setWorkletReducedMotionOverride, nullptr, &fn);
    napi_set_named_property(env, exports, "setWorkletReducedMotionOverride", fn);

    napi_create_function(env, "registerWorkletSource", NAPI_AUTO_LENGTH, registerWorkletSource, nullptr, &fn);
    napi_set_named_property(env, exports, "registerWorkletSource", fn);

    napi_create_function(env, "unregisterWorkletSource", NAPI_AUTO_LENGTH, unregisterWorkletSource, nullptr, &fn);
    napi_set_named_property(env, exports, "unregisterWorkletSource", fn);

    napi_create_function(env, "drainWorkletErrors", NAPI_AUTO_LENGTH, drainWorkletErrors, nullptr, &fn);
    napi_set_named_property(env, exports, "drainWorkletErrors", fn);

    return exports;
}

NAPI_MODULE(NODE_GYP_MODULE_NAME, init)
