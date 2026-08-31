// #include <android/log.h>
// #include <jni.h>
// #include <pthread.h>
// #include <unistd.h>

// #include <cstdlib>
// #include <cstring>
// #include <mutex>
// #include <queue>
// #include <string>

// #include "node.h"
// #include "spotifyengine.h"

// // int pipe_stdout[2];
// // int pipe_stderr[2];
// // pthread_t thread_stdout;
// // pthread_t thread_stderr;
// // const char* ADBTAG = "SpotifyPlus";

// // static JavaVM* g_vm = nullptr;
// // static jobject g_scriptManager = nullptr;
// // static jmethodID g_onMessageFromNode = nullptr;

// // static std::queue<std::string> g_javaToNodeQueue;
// // static std::mutex g_queueMutex;

// // static bool should_skip_log(const char* msg)
// // {
// //     if (msg == nullptr)
// //         return true;

// //     return strstr(msg, "s_glBindAttribLocation:") != nullptr;
// // }

// // void* thread_stderr_func(void*)
// // {
// //     ssize_t redirect_size;
// //     char buf[2048];
// //     while ((redirect_size = read(pipe_stderr[0], buf, sizeof buf - 1)) > 0)
// //     {
// //         if (buf[redirect_size - 1] == '\n')
// //             --redirect_size;
// //         buf[redirect_size] = 0;

// //         if (should_skip_log(buf))
// //             continue;

// //         __android_log_write(ANDROID_LOG_ERROR, ADBTAG, buf);
// //     }
// //     return 0;
// // }

// // void* thread_stdout_func(void*)
// // {
// //     ssize_t redirect_size;
// //     char buf[2048];
// //     while ((redirect_size = read(pipe_stdout[0], buf, sizeof buf - 1)) > 0)
// //     {
// //         if (buf[redirect_size - 1] == '\n')
// //             --redirect_size;
// //         buf[redirect_size] = 0;

// //         if (should_skip_log(buf))
// //             continue;

// //         __android_log_write(ANDROID_LOG_INFO, ADBTAG, buf);
// //     }
// //     return 0;
// // }

// // int start_redirecting_stdout_stderr()
// // {
// //     // set stdout as unbuffered.
// //     setvbuf(stdout, 0, _IONBF, 0);
// //     pipe(pipe_stdout);
// //     dup2(pipe_stdout[1], STDOUT_FILENO);

// //     // set stderr as unbuffered.
// //     setvbuf(stderr, 0, _IONBF, 0);
// //     pipe(pipe_stderr);
// //     dup2(pipe_stderr[1], STDERR_FILENO);

// //     if (pthread_create(&thread_stdout, 0, thread_stdout_func, 0) == -1)
// //         return -1;
// //     pthread_detach(thread_stdout);

// //     if (pthread_create(&thread_stderr, 0, thread_stderr_func, 0) == -1)
// //         return -1;
// //     pthread_detach(thread_stderr);

// //     return 0;
// // }

// // jint JNI_OnLoad(JavaVM *vm, void *)
// // {
// //     g_vm = vm;
// //     return JNI_VERSION_1_6;
// // }

// // extern "C" double SpotifyPlus_GetPlaybackPosition()
// // {
// //     return SpotifyPlusEngine::Get().GetPlaybackPosition();
// // }

// static void dispatch_to_java(const std::string& json)
// {
//     if (g_vm == nullptr || g_scriptManager == nullptr || g_onMessageFromNode == nullptr)
//         return;

//     JNIEnv* env = nullptr;
//     bool didAttach = false;

//     if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK)
//     {
//         if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK)
//         {
//             __android_log_write(ANDROID_LOG_ERROR, ADBTAG, "Failed to attach thread to JVM");
//             return;
//         }

//         didAttach = true;
//     }

//     jstring jjson = env->NewStringUTF(json.c_str());
//     env->CallVoidMethod(g_scriptManager, g_onMessageFromNode, jjson);

//     if (env->ExceptionCheck())
//     {
//         env->ExceptionDescribe();
//         env->ExceptionClear();
//     }

//     env->DeleteLocalRef(jjson);

//     if (didAttach)
//         g_vm->DetachCurrentThread();
// }

// extern "C" jint JNICALL
// Java_com_lenerd_spotifyplus_module_scripting_ScriptManager_startNodeWithArguments(JNIEnv* env, jobject /* this */, jobjectArray arguments)
// {
//     // argc
//     jsize argument_count = env->GetArrayLength(arguments);

//     // Compute byte size need for all arguments in contiguous memory.
//     int c_arguments_size = 0;
//     for (int i = 0; i < argument_count; i++)
//     {
//         c_arguments_size += strlen(env->GetStringUTFChars((jstring)env->GetObjectArrayElement(arguments, i), 0));
//         c_arguments_size++;  // for '\0'
//     }

//     // Stores arguments in contiguous memory.
//     char* args_buffer = (char*)calloc(c_arguments_size, sizeof(char));

//     // argv to pass into node.
//     char* argv[argument_count];

//     // To iterate through the expected start position of each argument in args_buffer.
//     char* current_args_position = args_buffer;

//     // Populate the args_buffer and argv.
//     for (int i = 0; i < argument_count; i++)
//     {
//         const char* current_argument = env->GetStringUTFChars((jstring)env->GetObjectArrayElement(arguments, i), 0);

//         // Copy current argument to its expected position in args_buffer
//         strncpy(current_args_position, current_argument, strlen(current_argument));

//         // Save current argument start position in argv
//         argv[i] = current_args_position;

//         // Increment to the next argument's expected position.
//         current_args_position += strlen(current_args_position) + 1;
//     }

//     // Enable for full debug logging. Honestly this is really only disabled because it prints a ton of errors
//     bool enable = true;
//     if (enable && start_redirecting_stdout_stderr() == -1)
//     {
//         __android_log_write(ANDROID_LOG_ERROR, ADBTAG, "Couldn't start redirecting stdout and stderr to logcat.");
//     }

//     // Start node, with argc and argv.
//     int node_result = node::Start(argument_count, argv);
//     free(args_buffer);

//     return jint(node_result);
// }

// extern "C" JNIEXPORT void JNICALL
// Java_com_lenerd_spotifyplus_module_scripting_ScriptManager_nativeInit(JNIEnv* env, jobject thiz)
// {
//     if (g_scriptManager != nullptr)
//     {
//         env->DeleteGlobalRef(g_scriptManager);
//         g_scriptManager = nullptr;
//     }

//     g_scriptManager = env->NewGlobalRef(thiz);

//     jclass cls = env->GetObjectClass(thiz);
//     g_onMessageFromNode = env->GetMethodID(cls, "onMessageFromNode", "(Ljava/lang/String;)V");
//     env->DeleteLocalRef(cls);

//     if (g_onMessageFromNode == nullptr)
//     {
//         __android_log_write(ANDROID_LOG_ERROR, ADBTAG, "Failed to find onMessageFromNode(String)");
//         return;
//     }

//     dispatch_to_java("{\"type\":\"native\",\"name\":\"bridgeReady\"}");
// }

// extern "C" JNIEXPORT void JNICALL
// Java_com_lenerd_spotifyplus_module_scripting_ScriptManager_nativeSendToNode(JNIEnv* env, jobject, jstring json_)
// {
//     if (json_ == nullptr)
//         return;

//     const char* json = env->GetStringUTFChars(json_, nullptr);
//     if (json == nullptr)
//         return;

//     {
//         std::lock_guard<std::mutex> lock(g_queueMutex);
//         g_javaToNodeQueue.push(std::string(json));
//     }

//     __android_log_print(ANDROID_LOG_INFO, ADBTAG, "Queued message for Node: %s", json);
//     env->ReleaseStringUTFChars(json_, json);
// }

// // extern "C" bool SpotifyPlus_SetEventHandler(napi_env env, napi_value callback)
// // {
// //     return SpotifyPlusEngine::Get().SetEventHandler(env, callback);
// // }

// static std::string pop_java_to_node_message()
// {
//     std::lock_guard<std::mutex> lock(g_queueMutex);
//     if (g_javaToNodeQueue.empty())
//         return "";
//     std::string value = g_javaToNodeQueue.front();
//     g_javaToNodeQueue.pop();
//     return value;
// }

// extern "C" __attribute__((visibility("default"))) void SpotifyPlusBridge_SendToJava(const char* json)
// {
//     if (json == nullptr)
//         return;
//     dispatch_to_java(std::string(json));
// }

// extern "C" __attribute__((visibility("default"))) char* SpotifyPlusBridge_PollFromJava()
// {
//     std::lock_guard<std::mutex> lock(g_queueMutex);
//     if (g_javaToNodeQueue.empty())
//         return nullptr;

//     std::string value = g_javaToNodeQueue.front();
//     g_javaToNodeQueue.pop();

//     char* result = (char*)malloc(value.size() + 1);
//     if (result == nullptr)
//         return nullptr;

//     memcpy(result, value.c_str(), value.size());
//     result[value.size()] = '\0';
//     return result;
// }

// extern "C" __attribute__((visibility("default"))) void SpotifyPlusBridge_FreeString(char* str)
// {
//     if (str != nullptr)
//         free(str);
// }