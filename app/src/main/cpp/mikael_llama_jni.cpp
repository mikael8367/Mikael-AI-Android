#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <climits>
#include <cstdint>
#include <chrono>
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

void nativeLog(JNIEnv* env, const char* level, const std::string& message) {
    __android_log_print(std::string(level) == "ERROR" ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO, kTag, "[%s] %s", level, message.c_str());
    jclass bridge = env->FindClass("com/mikael/ai/LocalInferenceEngine");
    if (bridge == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }
    jmethodID callback = env->GetStaticMethodID(bridge, "onNativeLog", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (callback == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(bridge);
        return;
    }
    jstring javaLevel = env->NewStringUTF(level);
    jstring javaMessage = env->NewStringUTF(message.c_str());
    if (javaLevel != nullptr && javaMessage != nullptr) env->CallStaticVoidMethod(bridge, callback, javaLevel, javaMessage);
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (javaLevel != nullptr) env->DeleteLocalRef(javaLevel);
    if (javaMessage != nullptr) env->DeleteLocalRef(javaMessage);
    env->DeleteLocalRef(bridge);
}

jstring errorString(JNIEnv* env, const char* code, const char* message) {
    const std::string tagged = std::string("MIKAEL_NATIVE_ERROR:") + code + " • " + message;
    nativeLog(env, "ERROR", tagged);
    return env->NewStringUTF(tagged.c_str());
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
        return errorString(env, "E_LOAD_INVALID_ARGUMENT", "Caminho ou tamanho de contexto inválido.");
    }

    const std::string path = javaToUtf8(env, modelPath);
    if (path.empty()) return errorString(env, "E_LOAD_PATH_UTF8", "Não foi possível ler o caminho do modelo.");

    std::lock_guard<std::mutex> lock(g_mutex);
    nativeLog(env, "INFO", "LOAD_BEGIN • contexto=" + std::to_string(contextTokens) + " • threads=" + std::to_string(threads));
    g_cancel.store(true);
    unloadLocked();
    g_cancel.store(false);
    std::call_once(g_backend_once, [] { llama_backend_init(); });

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0;
    nativeLog(env, "INFO", "LOAD_MODEL_BEGIN • abrindo GGUF");
    const auto modelStart = std::chrono::steady_clock::now();
    g_model = llama_model_load_from_file(path.c_str(), modelParams);
    if (g_model == nullptr) {
        return errorString(env, "E_MODEL_OPEN", "O llama.cpp não conseguiu abrir este GGUF. Verifique se o arquivo está completo e é compatível.");
    }

    nativeLog(env, "INFO", "LOAD_MODEL_OK • ms=" + std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - modelStart).count()));
    nativeLog(env, "INFO", "LOAD_CONTEXT_BEGIN");
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
        return errorString(env, "E_CONTEXT_ALLOC", "Não foi possível reservar memória para o contexto. Reduza o contexto ou escolha um modelo menor.");
    }

    g_vocab = llama_model_get_vocab(g_model);
    if (g_vocab == nullptr) {
        unloadLocked();
        return errorString(env, "E_VOCAB_NULL", "O modelo não forneceu um vocabulário válido.");
    }
    g_context_size = static_cast<uint32_t>(contextTokens);
    nativeLog(env, "INFO", "LOAD_READY • contexto=" + std::to_string(g_context_size));
    return nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikael_ai_LocalInferenceEngine_nativeGenerate(
        JNIEnv* env, jobject, jstring promptValue, jint maxTokens) {
    if (promptValue == nullptr) return errorString(env, "E_INPUT_EMPTY", "A mensagem está vazia.");
    const std::string prompt = javaToUtf8(env, promptValue);
    if (prompt.empty()) return errorString(env, "E_INPUT_UTF8", "Não foi possível ler a mensagem.");
    if (prompt.empty() || prompt.size() > 24000 || prompt.size() > static_cast<size_t>(INT_MAX)) {
        return errorString(env, "E_INPUT_SIZE", "A mensagem precisa ter entre 1 e 24000 bytes.");
    }

    g_cancel.store(false);
    std::lock_guard<std::mutex> lock(g_mutex);
    const auto generationStart = std::chrono::steady_clock::now();
    nativeLog(env, "INFO", "GEN_BEGIN • prompt_bytes=" + std::to_string(prompt.size()) + " • max_tokens=" + std::to_string(maxTokens));
    if (g_model == nullptr || g_context == nullptr || g_vocab == nullptr || g_context_size == 0) {
        return errorString(env, "E_MODEL_NOT_LOADED", "Nenhum modelo local está carregado.");
    }

    llama_memory_clear(llama_get_memory(g_context), true);

    const int32_t capacity = static_cast<int32_t>(std::min<size_t>(prompt.size() + 64, INT_MAX));
    std::vector<llama_token> tokens(static_cast<size_t>(capacity));
    nativeLog(env, "INFO", "TOKENIZE_BEGIN");
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
        return errorString(env, "E_TOKENIZE", "Não foi possível converter a mensagem em tokens.");
    }
    nativeLog(env, "INFO", "TOKENIZE_OK • prompt_tokens=" + std::to_string(tokenCount));
    // Preserve the most recent part of the prompt and reserve context for a useful answer.
    // Long restored histories must not make the chat permanently unusable on small devices.
    const int32_t requestedOutput = std::clamp(static_cast<int>(maxTokens), 16, 256);
    const int32_t outputReserve = std::min(
            requestedOutput,
            std::max(32, static_cast<int32_t>(g_context_size / 4)));
    const int32_t promptLimit = std::max(1, static_cast<int32_t>(g_context_size) - outputReserve);
    if (tokenCount > promptLimit) {
        const int32_t removeCount = tokenCount - promptLimit;
        tokens.erase(tokens.begin(), tokens.begin() + removeCount);
        tokenCount = promptLimit;
    }
    if (tokenCount <= 0) {
        return errorString(env, "E_CONTEXT_TRUNCATED_EMPTY", "A mensagem ficou vazia após ajustar o contexto. Envie uma mensagem menor.");
    }

    nativeLog(env, "INFO", "PREFILL_BEGIN • tokens=" + std::to_string(tokenCount));
    constexpr int32_t batchLimit = 128;
    for (int32_t offset = 0; offset < tokenCount; offset += batchLimit) {
        if (g_cancel.load()) return utf8ToJava(env, "");
        const int32_t count = std::min(batchLimit, tokenCount - offset);
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, count);
        nativeLog(env, "INFO", "PREFILL_BATCH_BEGIN • offset=" + std::to_string(offset) + " • count=" + std::to_string(count));
        const auto batchStart = std::chrono::steady_clock::now();
        const int decodeResult = llama_decode(g_context, batch);
        nativeLog(env, decodeResult == 0 ? "INFO" : "ERROR", "PREFILL_BATCH_END • offset=" + std::to_string(offset) + " • count=" + std::to_string(count) + " • llama_decode_rc=" + std::to_string(decodeResult) + " • ms=" + std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - batchStart).count()));
        if (decodeResult != 0) return errorString(env, "E_PREFILL_DECODE", ("llama_decode retornou " + std::to_string(decodeResult)).c_str());
    }

    nativeLog(env, "INFO", "PREFILL_DONE • ms=" + std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - generationStart).count()));
    nativeLog(env, "INFO", "SAMPLER_INIT_BEGIN");
    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (sampler == nullptr) return errorString(env, "E_SAMPLER_CHAIN", "Não foi possível iniciar o amostrador.");
    llama_sampler* greedy = llama_sampler_init_greedy();
    if (greedy == nullptr) {
        llama_sampler_free(sampler);
        return errorString(env, "E_SAMPLER_GREEDY", "Não foi possível iniciar o amostrador guloso.");
    }
    llama_sampler_chain_add(sampler, greedy);

    nativeLog(env, "INFO", "SAMPLER_READY • geração iniciada");
    std::string output;
    std::vector<char> piece(4096);
    int generatedTokens = 0;
    const int remainingContext = static_cast<int>(g_context_size) - tokenCount;
    const int limit = std::clamp(std::min(requestedOutput, remainingContext), 1, 256);
    bool decodeFailed = false;
    for (int i = 0; i < limit && !g_cancel.load(); ++i) {
        const bool traceToken = generatedTokens == 0 || (generatedTokens + 1) % 8 == 0;
        if (traceToken) nativeLog(env, "INFO", "SAMPLE_BEGIN • next_token=" + std::to_string(generatedTokens + 1));
        const auto sampleStart = std::chrono::steady_clock::now();
        const llama_token token = llama_sampler_sample(sampler, g_context, -1);
        ++generatedTokens;
        if (traceToken) nativeLog(env, "INFO", "SAMPLE_DONE • token=" + std::to_string(generatedTokens) + " • ms=" + std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - sampleStart).count()));
        if (generatedTokens == 1 || generatedTokens % 8 == 0) nativeLog(env, "INFO", "GEN_PROGRESS • token=" + std::to_string(generatedTokens) + "/" + std::to_string(limit));
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
        if (traceToken) nativeLog(env, "INFO", "GEN_DECODE_BEGIN • token=" + std::to_string(generatedTokens));
        const auto decodeStart = std::chrono::steady_clock::now();
        const int decodeResult = llama_decode(g_context, batch);
        if (traceToken || decodeResult != 0) nativeLog(env, decodeResult == 0 ? "INFO" : "ERROR", "GEN_DECODE_END • token=" + std::to_string(generatedTokens) + " • llama_decode_rc=" + std::to_string(decodeResult) + " • ms=" + std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - decodeStart).count()));
        if (decodeResult != 0) {
            decodeFailed = true;
            break;
        }
    }

    llama_sampler_free(sampler);
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - generationStart).count();
    nativeLog(env, decodeFailed ? "ERROR" : "INFO", "GEN_END • tokens=" + std::to_string(generatedTokens) + " • chars=" + std::to_string(output.size()) + " • ms=" + std::to_string(elapsed) + " • cancelled=" + std::to_string(g_cancel.load()));
    if (decodeFailed) return errorString(env, "E_GENERATION_DECODE", "Falha em llama_decode durante a geração; consulte GEN_DECODE_FAILED para o código de retorno.");
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
