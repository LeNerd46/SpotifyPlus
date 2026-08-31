#pragma once

#include <cstddef>
#include <cstdint>

extern "C"
{
bool SpotifyPlus_WorkletConfigurePlatform(void* platform);
bool SpotifyPlus_WorkletIsAvailable();
bool SpotifyPlus_WorkletCreateContext(const char* scriptId, uint64_t generation);
bool SpotifyPlus_WorkletDisposeContext(const char* scriptId, uint64_t generation);
bool SpotifyPlus_WorkletDisposeScript(const char* scriptId);
bool SpotifyPlus_WorkletRegister(
    const char* scriptId,
    uint64_t generation,
    const char* workletId,
    const char* source,
    const char* closureJson);
bool SpotifyPlus_WorkletUnregister(
    const char* scriptId,
    uint64_t generation,
    const char* workletId);
bool SpotifyPlus_WorkletInstallGlobals(
    const char* scriptId,
    uint64_t generation,
    const char* moduleName,
    const char* source);
bool SpotifyPlus_WorkletSchedule(
    const char* scriptId,
    uint64_t generation,
    const char* workletId,
    const char* argsJson);
bool SpotifyPlus_WorkletRegisterMapper(
    const char* scriptId,
    uint64_t generation,
    const char* mapperId,
    const char* workletId,
    const char* surfaceId,
    int32_t nodeId,
    int32_t priority,
    bool runEveryFrame);
bool SpotifyPlus_WorkletUnregisterMapper(
    const char* scriptId,
    uint64_t generation,
    const char* mapperId);
bool SpotifyPlus_WorkletSetSharedValue(
    const char* scriptId,
    uint64_t generation,
    const char* sharedValueId,
    const char* valueJson);
char* SpotifyPlus_WorkletGetSharedValue(
    const char* scriptId,
    uint64_t generation,
    const char* sharedValueId);
bool SpotifyPlus_WorkletDeleteSharedValue(
    const char* scriptId,
    uint64_t generation,
    const char* sharedValueId);
bool SpotifyPlus_WorkletCancelAnimation(
    const char* scriptId,
    uint64_t generation,
    const char* sharedValueId);
bool SpotifyPlus_WorkletGetReducedMotion();
bool SpotifyPlus_WorkletSetReducedMotionOverride(
    const char* scriptId,
    uint64_t generation,
    const char* modeJson);
bool SpotifyPlus_WorkletRegisterSource(
    const char* scriptId,
    uint64_t generation,
    const char* sourceId,
    const char* sharedValueId,
    const char* configJson);
bool SpotifyPlus_WorkletUnregisterSource(
    const char* scriptId,
    uint64_t generation,
    const char* sourceId,
    const char* sharedValueId);
bool SpotifyPlus_WorkletPublishSourceValue(const char* sourceId, const char* valueJson);
char* SpotifyPlus_WorkletTakeErrorJson();
void SpotifyPlus_WorkletFreeString(char* value);
}
