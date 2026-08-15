/*
 * Copyright 2026 吴思毅
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <algorithm>
#include <cstdint>
#include <filesystem>
#include <mutex>
#include <sstream>
#include <string>
#include <stdexcept>
#include <vector>

#include <MNN/expr/Expr.hpp>
#include <MNN/expr/NeuralNetWorkOp.hpp>
#include <llm/llm.hpp>

namespace {
using MNN::Transformer::ChatMessages;
using MNN::Transformer::Llm;
using MNN::Transformer::LlmContext;
using MNN::Transformer::LlmStatus;
using MNN::Transformer::MultimodalPrompt;
using MNN::Transformer::PromptImagePart;

constexpr const char* kTag = "CiMnn";
std::mutex g_model_mutex;
Llm* g_llm = nullptr;
std::atomic_bool g_cancel_requested{false};

std::string fromJString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* raw = env->GetStringUTFChars(value, nullptr);
    std::string result = raw == nullptr ? "" : raw;
    if (raw != nullptr) env->ReleaseStringUTFChars(value, raw);
    return result;
}

void throwIllegalState(JNIEnv* env, const std::string& message) {
    auto clazz = env->FindClass("java/lang/IllegalStateException");
    if (clazz != nullptr) env->ThrowNew(clazz, message.c_str());
}

void restoreSteppingStatus(Llm* llm) {
    if (llm == nullptr || llm->getContext() == nullptr) return;
    auto* context = const_cast<LlmContext*>(llm->getContext());
    if (context->status == LlmStatus::MAX_TOKENS_FINISHED ||
        context->status == LlmStatus::NORMAL_FINISHED ||
        context->status == LlmStatus::USER_CANCEL) {
        context->status = LlmStatus::RUNNING;
    }
}

std::string cleanOutput(std::string output) {
    constexpr const char* marker = "<eop>";
    std::size_t position = 0;
    while ((position = output.find(marker, position)) != std::string::npos) {
        output.erase(position, std::char_traits<char>::length(marker));
    }
    return output;
}

template <typename Prefill>
std::string generateStepping(Llm* llm, bool thinking, int max_tokens, Prefill&& prefill) {
    if (llm == nullptr) throw std::runtime_error("本地模型尚未加载");
    g_cancel_requested.store(false);
    llm->reset();
    restoreSteppingStatus(llm);
    const std::string config = std::string("{\"jinja\":{\"context\":{\"enable_thinking\":") +
        (thinking ? "true" : "false") + "}}}";
    if (!llm->set_config(config)) throw std::runtime_error("无法切换思考模式");

    std::stringstream output;
    prefill(output);
    restoreSteppingStatus(llm);
    const int limit = std::max(1, max_tokens);
    for (int index = 0; index < limit && !g_cancel_requested.load(); ++index) {
        llm->generate(1);
        const auto* context = llm->getContext();
        if (context == nullptr) throw std::runtime_error("MNN 推理上下文丢失");
        if (context->status == LlmStatus::INTERNAL_ERROR || context->status == LlmStatus::TIMEOUT) {
            throw std::runtime_error("MNN 推理异常终止");
        }
        if (context->status == LlmStatus::NORMAL_FINISHED) break;
        if (context->status == LlmStatus::MAX_TOKENS_FINISHED) restoreSteppingStatus(llm);
    }
    if (g_cancel_requested.load()) throw std::runtime_error("本地模型请求已取消");
    return cleanOutput(output.str());
}
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wsy_ci_localmodel_runtime_JniMnnNativeBridge_nativeLoad(
    JNIEnv* env, jobject, jstring model_path) {
    std::lock_guard<std::mutex> lock(g_model_mutex);
    try {
        if (g_llm != nullptr) return JNI_TRUE;
        const std::string config_path = fromJString(env, model_path);
        if (!std::filesystem::is_regular_file(config_path)) {
            throw std::runtime_error("模型配置文件不存在：" + config_path);
        }
        g_llm = Llm::createLLM(config_path);
        if (g_llm == nullptr) throw std::runtime_error("MNN createLLM 返回空实例");
        const auto cache_dir = std::filesystem::path(config_path).parent_path() / ".mnn-cache";
        std::filesystem::create_directories(cache_dir);
        const std::string config = "{\"backend_type\":\"cpu\",\"thread_num\":4,\"precision\":\"low\",\"memory\":\"low\",\"use_mmap\":true,\"tmp_path\":\"" +
            cache_dir.string() + "\"}";
        g_llm->set_config(config);
        if (!g_llm->load()) {
            Llm::destroy(g_llm);
            g_llm = nullptr;
            throw std::runtime_error("MNN load 返回失败");
        }
        __android_log_print(ANDROID_LOG_INFO, kTag, "Qwen3.5-2B MNN 加载完成");
        return JNI_TRUE;
    } catch (const std::exception& error) {
        if (g_llm != nullptr) {
            Llm::destroy(g_llm);
            g_llm = nullptr;
        }
        throwIllegalState(env, error.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_wsy_ci_localmodel_runtime_JniMnnNativeBridge_nativeUnload(JNIEnv*, jobject) {
    g_cancel_requested.store(true);
    std::lock_guard<std::mutex> lock(g_model_mutex);
    if (g_llm != nullptr) {
        Llm::destroy(g_llm);
        g_llm = nullptr;
        __android_log_print(ANDROID_LOG_INFO, kTag, "MNN 模型已释放");
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wsy_ci_localmodel_runtime_JniMnnNativeBridge_nativeGenerate(
    JNIEnv* env, jobject, jstring system_prompt, jstring user_prompt,
    jboolean thinking, jint max_tokens) {
    std::lock_guard<std::mutex> lock(g_model_mutex);
    try {
        ChatMessages messages;
        const auto system = fromJString(env, system_prompt);
        if (!system.empty()) messages.emplace_back("system", system);
        messages.emplace_back("user", fromJString(env, user_prompt));
        const auto result = generateStepping(g_llm, thinking == JNI_TRUE, max_tokens,
            [&](std::ostream& output) { g_llm->response(messages, &output, "<eop>", 0); });
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception& error) {
        throwIllegalState(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wsy_ci_localmodel_runtime_JniMnnNativeBridge_nativeGenerateImage(
    JNIEnv* env, jobject, jbyteArray rgba, jint width, jint height,
    jstring system_prompt, jstring user_prompt, jboolean thinking, jint max_tokens) {
    std::lock_guard<std::mutex> lock(g_model_mutex);
    try {
        const auto rgba_size = env->GetArrayLength(rgba);
        if (width <= 0 || height <= 0 || rgba_size != width * height * 4) {
            throw std::runtime_error("RGBA 图片尺寸不合法");
        }
        std::vector<jbyte> source(static_cast<std::size_t>(rgba_size));
        env->GetByteArrayRegion(rgba, 0, rgba_size, source.data());
        std::vector<std::uint8_t> rgb(static_cast<std::size_t>(width * height * 3));
        for (std::size_t src = 0, dst = 0; src < source.size(); src += 4, dst += 3) {
            rgb[dst] = static_cast<std::uint8_t>(source[src]);
            rgb[dst + 1] = static_cast<std::uint8_t>(source[src + 1]);
            rgb[dst + 2] = static_cast<std::uint8_t>(source[src + 2]);
        }
        MultimodalPrompt prompt;
        prompt.prompt_template = fromJString(env, system_prompt) + "\n<img>image_0</img>\n" +
            fromJString(env, user_prompt);
        PromptImagePart image_part;
        image_part.image_data = MNN::Express::_Const(
            rgb.data(), {height, width, 3}, MNN::Express::NHWC, halide_type_of<std::uint8_t>());
        image_part.width = width;
        image_part.height = height;
        prompt.images["image_0"] = image_part;
        const auto result = generateStepping(g_llm, thinking == JNI_TRUE, max_tokens,
            [&](std::ostream& output) { g_llm->response(prompt, &output, "<eop>", 0); });
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception& error) {
        throwIllegalState(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_wsy_ci_localmodel_runtime_JniMnnNativeBridge_nativeCancel(JNIEnv*, jobject) {
    g_cancel_requested.store(true);
}
