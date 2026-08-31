#pragma once

#include <jni.h>
#include <node_api.h>

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

struct PlatformData
{
    std::string clientVersion;
    std::string osName;
    std::string osVersion;
    int sdkVersion;
};

struct SessionData
{
    std::string accessToken;
};

struct SpotifyAlbum
{
    std::string title;
    std::string artist;
    std::string date;
    std::string image;
};

struct SpotifyTrack
{
    std::string title;
    int trackNumber;
    long durationMs;
    bool explicitTrack;
    std::string uri;
    std::string id;
    std::string artist;
    std::vector<std::string> artists;
    SpotifyAlbum album;
};

struct StorageValueResult
{
    bool found;
    std::string value;
};

struct StorageReadResult
{
    bool found;
    std::string type;
    std::string value;
    std::string data;
};

enum class WorkletUpdateType : int32_t
{
    Null = 0,
    Number = 1,
    Boolean = 2,
    String = 3,
    Object = 4,
    Array = 5,
};

struct WorkletViewUpdate
{
    std::string surfaceId;
    int32_t nodeId;
    std::string property;
    WorkletUpdateType type;
    double numberValue;
    std::string stringValue;
};

class SpotifyPlusEngine
{
   public:
    static SpotifyPlusEngine& Get();

    bool Initialize(JavaVM* v);
    bool SetBridge(JNIEnv* env, jobject bridge, jobjectArray args);

    void LoadApk(const std::string& scriptId, const std::string& apkPath, const std::string& pluginClass);
    void UnregisterScript(const std::string& scriptId);

    PlatformData GetPlatformData();
    SessionData GetSession();
    void Log(const char* message);

    // Player

    SpotifyTrack GetCurrentTrack();
    SpotifyTrack GetTrack(const std::string& uri);
    double GetPlaybackPosition();
    void Seek(long position);
    void Play();
    void Pause();
    void TogglePlay();
    void SkipNext();
    void SkipPrevious();

    // Non-surface API

    void Toast(const std::string& text, bool longLength);
    bool Navigate(const std::string& uri, const std::string& target);
    bool NavigateBack();

    // Storage

    void StorageSet(const std::string& scriptId, const std::string& key, const std::string& value);
    StorageValueResult StorageGet(const std::string& scriptId, const std::string& key);
    void StorageRemove(const std::string& scriptId, const std::string& key);
    void StorageWriteText(const std::string& scriptId, const std::string& path, const std::string& value);
    void StorageWriteJson(const std::string& scriptId, const std::string& path, const std::string& value);
    void StorageWriteBinary(const std::string& scriptId, const std::string& path, const std::string& data);
    StorageReadResult StorageRead(const std::string& scriptId, const std::string& path);
    bool GetDeveloperMode();
    void SetDeveloperMode(bool enabled);
    bool PickLocalExtensionsFolder();
    std::string GetLocalExtensionsFolderDisplayName();
    std::string ListLocalExtensions();
    std::string RefreshLocalExtensions();

    // Buttons

    void RegisterContextMenu(const std::string& id, const std::string& scriptId, const std::string& title);
    void RegisterSideDrawer(const std::string& id, const std::string& scriptId, const std::string& title);
    void RegisterSideDrawerWithIcon(const std::string& id, const std::string& scriptId, const std::string& title, const std::string& iconRegistrationJson);

    // React

    void RegisterSurface(const std::string& surfaceId);
    void UnregisterSurface(const std::string& surfaceId);
    void CommitSurface(const std::string& surfaceId, const std::string& opsJson);

    void RequestWorkletFrame();
    void DispatchWorkletUpdates(int64_t frameTimeNanos, const std::vector<WorkletViewUpdate>& updates);
    std::string MeasureWorkletView(const std::string& surfaceId, int32_t nodeId);
    std::string GetWorkletRelativeCoords(const std::string& surfaceId, int32_t nodeId, double absoluteX, double absoluteY);
    bool ScrollWorkletView(const std::string& surfaceId, int32_t nodeId, double x, double y, bool animated);
    bool DispatchWorkletCommand(const std::string& surfaceId, int32_t nodeId, const std::string& command, const std::string& argsJson);
    void SetWorkletSourceActive(const std::string& sourceId, const std::string& configJson, bool active);

    bool SetEventHandler(napi_env evn, napi_value callback);
    void EmitEvent(const std::string& type, const std::string& payload);

    bool IsReady() const;

   private:
    SpotifyPlusEngine() = default;
    ~SpotifyPlusEngine();
    SpotifyPlusEngine(const SpotifyPlusEngine&) = delete;
    SpotifyPlusEngine& operator=(const SpotifyPlusEngine&) = delete;

    JNIEnv* GetEnv(bool* didAttach = nullptr);
    void LoadNodeRuntime(JNIEnv* env, jobjectArray args);
    void StartNodeRuntimeAsync(JNIEnv* env, jobjectArray args);
    void DetachIfNeeded(bool didAttach);
    bool EnsureBridgeLocked(JNIEnv* env);
    void ClearBridgeLocked(JNIEnv* env);

    bool CacheMethodsLocked(JNIEnv* env);
    SpotifyTrack ReadTrackLocked(JNIEnv* env, jobject spotifyTrackObject);

    static void CallJs(napi_env evn, napi_value jsCallback, void* context, void* data);
    void ReleaseEventHandlerLocked();

   private:
    mutable std::mutex m_mutex;

    JavaVM* m_vm = nullptr;

    jobject m_bridge = nullptr;
    jclass m_bridgeClass = nullptr;

    jmethodID m_loadApkMethod = nullptr;
    jmethodID m_unregisterScriptMethod = nullptr;

    jmethodID m_getPlatformDataMethod = nullptr;
    jmethodID m_getSessionMethod = nullptr;

    jclass m_platformDataClass = nullptr;
    jfieldID m_platformDataClientVersionField = nullptr;
    jfieldID m_platformDataOsNameField = nullptr;
    jfieldID m_platformDataOsVersionField = nullptr;
    jfieldID m_platformDataSdkVersionField = nullptr;

    jmethodID m_getCurrentTrackMethod = nullptr;
    jmethodID m_getTrackMethod = nullptr;
    jclass m_spotifyTrackClass = nullptr;
    jclass m_spotifyAlbumClass = nullptr;

    jfieldID m_spotifyAlbumTitleField = nullptr;
    jfieldID m_spotifyAlbumArtistField = nullptr;
    jfieldID m_spotifyAlbumReleaseField = nullptr;
    jfieldID m_spotifyAlbumImageField = nullptr;

    jfieldID m_spotifyTrackTitleField = nullptr;
    jfieldID m_spotifyTrackTrackNumberField = nullptr;
    jfieldID m_spotifyTrackDurationField = nullptr;
    jfieldID m_spotifyTrackExplicitField = nullptr;
    jfieldID m_spotifyTrackUriField = nullptr;
    jfieldID m_spotifyTrackIdField = nullptr;
    jfieldID m_spotifyTrackArtistField = nullptr;
    jfieldID m_spotifyTrackArtistsField = nullptr;
    jfieldID m_spotifyTrackAlbumField = nullptr;

    jmethodID m_getPlaybackPositionMethod = nullptr;
    jmethodID m_seekMethod = nullptr;
    jmethodID m_playMethod = nullptr;
    jmethodID m_pauseMethod = nullptr;
    jmethodID m_togglePlayMethod = nullptr;
    jmethodID m_skipNextMethod = nullptr;
    jmethodID m_skipPreviousMethod = nullptr;

    jmethodID m_toastMethod = nullptr;
    jmethodID m_navigateMethod = nullptr;
    jmethodID m_navigateBackMethod = nullptr;
    jmethodID m_storageSetMethod = nullptr;
    jmethodID m_storageGetMethod = nullptr;
    jmethodID m_storageRemoveMethod = nullptr;
    jmethodID m_storageWriteTextMethod = nullptr;
    jmethodID m_storageWriteJsonMethod = nullptr;
    jmethodID m_storageWriteBinaryMethod = nullptr;
    jmethodID m_storageReadMethod = nullptr;
    jmethodID m_getDeveloperModeMethod = nullptr;
    jmethodID m_setDeveloperModeMethod = nullptr;
    jmethodID m_pickLocalExtensionsFolderMethod = nullptr;
    jmethodID m_getLocalExtensionsFolderDisplayNameMethod = nullptr;
    jmethodID m_listLocalExtensionsMethod = nullptr;
    jmethodID m_refreshLocalExtensionsMethod = nullptr;

    jclass m_storageReadResultClass = nullptr;
    jfieldID m_storageReadFoundField = nullptr;
    jfieldID m_storageReadTypeField = nullptr;
    jfieldID m_storageReadValueField = nullptr;
    jfieldID m_storageReadDataField = nullptr;

    jmethodID m_registerContextMenuMethod = nullptr;
    jmethodID m_registerSideDrawerMethod = nullptr;
    jmethodID m_registerSideDrawerWithIconMethod = nullptr;

    jmethodID m_registerSurfaceMethod = nullptr;
    jmethodID m_unregisterSurfaceMethod = nullptr;
    jmethodID m_commitSurfaceMethod = nullptr;
    jmethodID m_requestWorkletFrameMethod = nullptr;
    jmethodID m_dispatchWorkletUpdatesMethod = nullptr;
    jmethodID m_measureWorkletViewMethod = nullptr;
    jmethodID m_getWorkletRelativeCoordsMethod = nullptr;
    jmethodID m_scrollWorkletViewMethod = nullptr;
    jmethodID m_dispatchWorkletCommandMethod = nullptr;
    jmethodID m_activateWorkletSourceMethod = nullptr;
    jmethodID m_deactivateWorkletSourceMethod = nullptr;

    napi_threadsafe_function m_eventTSfn = nullptr;

    std::atomic<bool> m_nodeStarted = false;
};

struct NativeEvent
{
    std::string type;
    std::string payload;
};
