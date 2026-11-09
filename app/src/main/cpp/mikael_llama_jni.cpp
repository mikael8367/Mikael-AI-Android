#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <climits>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

namespace {
constexpr const char* kTag = "MikaelAI";
std::mutex g_mutex;
std::atomic<bool> g_cancel{false};
std::once_flag g_backend_once;
llama_model* g_model = nullptr;
llama_context* g_context = nullptr;
const llama_vocab* g_vocab = nullptr;
uint32_t g_context_size = 0;

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
    g_context_size = 0;
}

jstring errorString(JNIEnv* env, const char* message) {
    return env->NewStringUTF(message);
}

std::string javaToUtf8(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    std::string output;
    output.reserve(static_cast<size_t>(length) * 3);
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length) {
            const uint32_t low = chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                cp = 0x10000 + ((cp - 0xD800) << 10) + (low - 0xDC00);
                ++i;
            } else {
                cp = 0xFFFD;
            }
        } else if (cp >= 0xDC00 && cp <= 0xDFFF) {
            cp = 0xFFFD;
        }
        if (cp <= 0x7F) {
            output.push_back(static_cast<char>(cp));
        } else if (cp <= 0x7FF) {
            output.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            output.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp <= 0xFFFF) {
            output.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            output.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            output.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            output.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return output;
}

jstring utf8ToJava(JNIEnv* env, const std::string& value) {
    std::vector<jchar> utf16;
    utf16.reserve(value.size());
    size_t i = 0;
    while (i < value.size()) {
        const uint8_t first = static_cast<uint8_t>(value[i]);
        uint32_t codePoint = 0xFFFD;
        size_t count = 1;
        if (first < 0x80) {
            codePoint = first;
        } else if ((first & 0xE0) == 0xC0 && i + 1 < value.size()) {
            codePoint = ((first & 0x1F) << 6) | (static_cast<uint8_t>(value[i + 1]) & 0x3F);
            count = 2;
        } else if ((first & 0xF0) == 0xE0 && i + 2 < value.size()) {
            codePoint = ((first & 0x0F) << 12)
                    | ((static_cast<uint8_t>(value[i + 1]) & 0x3F) << 6)
                    | (static_cast<uint8_t>(value[i + 2]) & 0x3F);
            count = 3;
        } else if ((first & 0xF8) == 0xF0 && i + 3 < value.size()) {
            codePoint = ((first & 0x07) << 18)
                    | ((static_cast<uint8_t>(value[i + 1]) & 0x3F) << 12)
                    | ((static_cast<uint8_t>(value[i + 2]) & 0x3F) << 6)
                    | (static_cast<uint8_t>(value[i + 3]) & 0x3F);
            count = 4;
        }
        bool valid = true;
        for (size_t j = 1; j < count; ++j) {
            if ((static_cast<uint8_t>(value[i + j]) & 0xC0) != 0x80) valid = false;
        }
        const uint32_t minimum = count == 2 ? 0x80 : (count == 3 ? 0x800 : (count == 4 ? 0x10000 : 0));
        if (!valid || codePoint < minimum || codePoint > 0x10FFFF ||
                (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            codePoint = 0xFFFD;
            count = 1;
        }
        if (codePoint <= 0xFFFF) {
            utf16.push_back(static_cast<jchar>(codePoint));
        } else {
            codePoint -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (codePoint >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (codePoint & 0x3FF)));
        }
        i += count;
    }
    if (utf16.size() > static_cast<size_t>(INT_MAX)) return nullptr;
    static const jchar emptyText[] = {0};
    const jchar* chars = utf16.empty() ? emptyText : utf16.data();
    return env->NewString(chars, static_cast<jsize>(utf16.size()));
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikael_ai_LocalInferenceEngine_nativeLoad(
        JNIEnv* env, jobject, jstring modelPath, jint contextTokens, jint threads) {
    if (modelPath == nullptr || contextTokens < 256 || contextTokens > 8192) {
        return errorString(env, "Caminho ou tamanho de contexto inválido.");
    }

    const std::string path = javaToUtf8(env, modelPath);
    if (path.empty()) return errorString(env, "Não foi possível ler o caminho do modelo.");

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
    g_context_size = static_cast<uint32_t>(contextTokens);
    __android_log_print(ANDROID_LOG_INFO, kTag, "Modelo local carregado.");
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikael_ai_LocalInferenceEngine_nativeGenerate(
        JNIEnv* env, jobject, jstring promptValue, jint maxTokens) {
    if (promptValue == nullptr) return errorString(env, "A mensagem está vazia.");
    const std::string prompt = javaToUtf8(env, promptValue);
    if (prompt.empty()) return errorString(env, "Não foi possível ler a mensagem.");
    if (prompt.empty() || prompt.size() > 24000 || prompt.size() > static_cast<size_t>(INT_MAX)) {
        return errorString(env, "A mensagem precisa ter entre 1 e 24000 bytes.");
    }

    g_cancel.store(false);
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model == nullptr || g_context == nullptr || g_vocab == nullptr || g_context_size == 0) {
        return errorString(env, "Nenhum modelo local está carregado.");
    }

    llama_memory_clear(llama_get_memory(g_context), true);

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
    if (static_cast<uint32_t>(tokenCount) >= g_context_size) {
        return errorString(env, "A conversa ultrapassou o contexto configurado. Reduza o histórico ou aumente o contexto.");
    }

    constexpr int32_t batchLimit = 128;
    for (int32_t offset = 0; offset < tokenCount; offset += batchLimit) {
        if (g_cancel.load()) return utf8ToJava(env, "");
        const int32_t count = std::min(batchLimit, tokenCount - offset);
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, count);
        if (llama_decode(g_context, batch) != 0) {
            return errorString(env, "Falha ao processar a mensagem no modelo local.");
        }
    }

    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (sampler == nullptr) return errorString(env, "Não foi possível iniciar o amostrador.");
    llama_sampler* greedy = llama_sampler_init_greedy();
    if (greedy == nullptr) {
        llama_sampler_free(sampler);
        return errorString(env, "Não foi possível iniciar o amostrador guloso.");
    }
    llama_sampler_chain_add(sampler, greedy);

    std::string output;
    std::vector<char> piece(4096);
    const int remainingContext = static_cast<int>(g_context_size) - tokenCount;
    const int limit = std::clamp(std::min(static_cast<int>(maxTokens), remainingContext), 1, 512);
    bool decodeFailed = false;
    for (int i = 0; i < limit && !g_cancel.load(); ++i) {
        const llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (token == LLAMA_TOKEN_NULL || llama_vocab_is_eog(g_vocab, token)) break;

        int32_t pieceSize = llama_token_to_piece(
                g_vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
        if (pieceSize < 0) {
            piece.resize(static_cast<size_t>(-pieceSize));
            pieceSize = llama_token_to_piece(
                    g_vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
        }
        if (pieceSize > 0 && pieceSize <= static_cast<int32_t>(piece.size())) {
            output.append(piece.data(), static_cast<size_t>(pieceSize));
        }

        llama_token nextToken = token;
        llama_batch batch = llama_batch_get_one(&nextToken, 1);
        if (llama_decode(g_context, batch) != 0) {
            decodeFailed = true;
            break;
        }
    }

    llama_sampler_free(sampler);
    if (decodeFailed) return errorString(env, "A geração foi interrompida por uma falha no motor local.");
    return utf8ToJava(env, output);
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
