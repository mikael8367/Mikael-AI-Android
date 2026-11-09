#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <climits>
#include <mutex>
#include <string>
#include <vector>
#include <algorithm>
#include "llama.h"

namespace {
constexpr const char* kTag = "MikaelAI";
std::mutex g_mutex;
std::atomic<bool> g_cancel{false};
std::once_flag g_backend_once;
llama_model* g_model = nullptr;
llama_context* g_context = nullptr;
const llama_vocab* g_vocab = nullptr;

void unloadLocked() {
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
}

jstring errorString(JNIEnv* env, const char* message) {
    return env->NewStringUTF(message);
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikael_ai_LocalInferenceEngine_nativeLoad(
        JNIEnv* env, jobject, jstring modelPath, jint contextTokens, jint threads) {
    if (modelPath == nullptr || contextTokens < 256 || contextTokens > 8192) {
        return errorString(env, "Caminho ou tamanho de contexto inválido.");
    }

    const char* pathChars = env->GetStringUTFChars(modelPath, nullptr);
    if (pathChars == nullptr) return errorString(env, "Não foi possível ler o caminho do modelo.");
    const std::string path(pathChars);
    env->ReleaseStringUTFChars(modelPath, pathChars);

    std::lock_guard<std::mutex> lock(g_mutex);
    g_cancel.store(true);
    unloadLocked();
    g_cancel.store(false);

    std::call_once(g_backend_once, [] { llama_backend_init(); });

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path.c_str(), modelParams);
    if (g_model == nullptr) {
        return errorString(env, "O llama.cpp não conseguiu abrir este GGUF. Verifique se o arquivo está completo e é compatível.");
    }

    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = static_cast<uint32_t>(contextTokens);
    contextParams.n_batch = 128;
    contextParams.n_ubatch = 128;
    const int safeThreads = std::clamp(static_cast<int>(threads), 1, 4);
    contextParams.n_threads = safeThreads;
    contextParams.n_threads_batch = safeThreads;
    g_context = llama_init_from_model(g_model, contextParams);
    if (g_context == nullptr) {
        unloadLocked();
        return errorString(env, "Não foi possível reservar memória para o contexto. Reduza o contexto ou escolha um modelo menor.");
    }

    g_vocab = llama_model_get_vocab(g_model);
    if (g_vocab == nullptr) {
        unloadLocked();
        return errorString(env, "O modelo não forneceu um vocabulário válido.");
    }

    __android_log_print(ANDROID_LOG_INFO, kTag, "Modelo local carregado.");
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikael_ai_LocalInferenceEngine_nativeGenerate(
        JNIEnv* env, jobject, jstring promptValue, jint maxTokens) {
    if (promptValue == nullptr) return errorString(env, "A mensagem está vazia.");
    const char* promptChars = env->GetStringUTFChars(promptValue, nullptr);
    if (promptChars == nullptr) return errorString(env, "Não foi possível ler a mensagem.");
    const std::string prompt(promptChars);
    env->ReleaseStringUTFChars(promptValue, promptChars);

    if (prompt.empty() || prompt.size() > 24000) {
        return errorString(env, "A mensagem precisa ter entre 1 e 24000 bytes.");
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr || g_context == nullptr || g_vocab == nullptr) {
        return errorString(env, "Nenhum modelo local está carregado.");
    }

    g_cancel.store(false);
    const int32_t capacity = static_cast<int32_t>(std::min<size_t>(prompt.size() + 64, INT_MAX));
    std::vector<llama_token> tokens(static_cast<size_t>(capacity));
    int32_t tokenCount = llama_tokenize(
            g_vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), capacity, true, true);
    if (tokenCount < 0) {
        tokens.resize(static_cast<size_t>(-tokenCount));
        tokenCount = llama_tokenize(
                g_vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (tokenCount <= 0 || tokenCount > static_cast<int32_t>(tokens.size())) {
        return errorString(env, "Não foi possível converter a mensagem em tokens.");
    }

    llama_batch batch = llama_batch_get_one(tokens.data(), tokenCount);
    if (llama_decode(g_context, batch) != 0) {
        return errorString(env, "Falha ao processar a mensagem no modelo local.");
    }

    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (sampler == nullptr) return errorString(env, "Não foi possível iniciar o amostrador.");
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string output;
    std::vector<char> piece(512);
    const int limit = std::clamp(static_cast<int>(maxTokens), 1, 512);
    for (int i = 0; i < limit && !g_cancel.load(); ++i) {
        const llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(g_vocab, token)) break;

        const int32_t pieceSize = llama_token_to_piece(
                g_vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
        if (pieceSize > 0) output.append(piece.data(), static_cast<size_t>(pieceSize));

        batch = llama_batch_get_one(const_cast<llama_token*>(&token), 1);
        if (llama_decode(g_context, batch) != 0) break;
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_mikael_ai_LocalInferenceEngine_nativeCancel(JNIEnv*, jobject) {
    g_cancel.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mikael_ai_LocalInferenceEngine_nativeUnload(JNIEnv*, jobject) {
    g_cancel.store(true);
    std::lock_guard<std::mutex> lock(g_mutex);
    unloadLocked();
}
