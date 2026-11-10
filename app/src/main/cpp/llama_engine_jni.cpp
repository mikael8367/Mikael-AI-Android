#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cctype>
#include <chrono>
#include <mutex>
#include <limits>
#include <string>
#include <vector>

namespace {
std::mutex gMutex;
std::atomic<bool> gCancel{false};
llama_model * gModel = nullptr;
llama_context * gContext = nullptr;
llama_sampler * gSampler = nullptr;
uint32_t gContextSize = 0;
std::string gModelPath;
bool gBackendReady = false;

std::string fromJava(JNIEnv * env, jstring value) {
    if (!value) return {};
    const jchar * chars = env->GetStringChars(value, nullptr);
    if (!chars) return {};
    const jsize length = env->GetStringLength(value);
    std::string result;
    result.reserve(static_cast<size_t>(length) * 3);
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length &&
            chars[i + 1] >= 0xDC00 && chars[i + 1] <= 0xDFFF) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (chars[++i] - 0xDC00);
        } else if (cp >= 0xD800 && cp <= 0xDFFF) {
            cp = 0xFFFD;
        }
        if (cp < 0x80) result.push_back(static_cast<char>(cp));
        else if (cp < 0x800) {
            result.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            result.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
            result.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            result.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            result.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            result.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return result;
}
jstring toJava(JNIEnv * env, const std::string & value) {
    std::vector<jchar> output;
    output.reserve(value.size());
    size_t i = 0;
    while (i < value.size()) {
        const uint8_t first = static_cast<uint8_t>(value[i]);
        uint32_t cp = 0xFFFD;
        size_t count = 1;
        if (first < 0x80) cp = first;
        else if ((first & 0xE0) == 0xC0 && i + 1 < value.size()) {
            cp = ((first & 0x1F) << 6) | (static_cast<uint8_t>(value[i + 1]) & 0x3F); count = 2;
        } else if ((first & 0xF0) == 0xE0 && i + 2 < value.size()) {
            cp = ((first & 0x0F) << 12) | ((static_cast<uint8_t>(value[i + 1]) & 0x3F) << 6) |
                 (static_cast<uint8_t>(value[i + 2]) & 0x3F); count = 3;
        } else if ((first & 0xF8) == 0xF0 && i + 3 < value.size()) {
            cp = ((first & 0x07) << 18) | ((static_cast<uint8_t>(value[i + 1]) & 0x3F) << 12) |
                 ((static_cast<uint8_t>(value[i + 2]) & 0x3F) << 6) | (static_cast<uint8_t>(value[i + 3]) & 0x3F);
            count = 4;
        }
        bool valid = true;
        for (size_t j = 1; j < count; ++j) {
            if ((static_cast<uint8_t>(value[i + j]) & 0xC0) != 0x80) valid = false;
        }
        const uint32_t minimum = count == 2 ? 0x80 : (count == 3 ? 0x800 : (count == 4 ? 0x10000 : 0));
        if (!valid || cp < minimum || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
            cp = 0xFFFD; count = 1;
        }
        if (cp <= 0xFFFF) output.push_back(static_cast<jchar>(cp));
        else {
            cp -= 0x10000;
            output.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            output.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        }
        i += count;
    }
    if (output.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
    static const jchar empty[] = {0};
    return env->NewString(output.empty() ? empty : output.data(), static_cast<jsize>(output.size()));
}
jstring throwError(JNIEnv * env, const std::string & message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls) { env->ThrowNew(cls, message.c_str()); env->DeleteLocalRef(cls); }
    return nullptr;
}
std::string applyTinyLlamaTemplate(
    const std::vector<std::string> & roles,
    const std::vector<std::string> & contents
) {
    std::string output;
    std::string system;
    for (size_t i = 0; i < roles.size(); ++i) {
        if (roles[i] == "system") {
            if (!system.empty()) system += "\n";
            system += contents[i];
        }
    }
    if (!system.empty()) output += "<|system|>\n" + system + "</s>\n";
    for (size_t i = 0; i < roles.size(); ++i) {
        const std::string & role = roles[i];
        if (role == "system") continue;
        if (role != "user" && role != "assistant") continue;
        output += role == "user" ? "<|user|>\n" : "<|assistant|>\n";
        output += contents[i];
        output += "</s>\n";
    }
    output += "<|assistant|>\n";
    return output;
}

std::string applyFallbackChatML(
    const std::vector<std::string> & roles,
    const std::vector<std::string> & contents
) {
    std::string output;
    for (size_t i = 0; i < roles.size(); ++i) {
        const std::string & role = roles[i];
        if (role != "system" && role != "user" && role != "assistant") continue;
        output += "<|im_start|>" + role + "\n" + contents[i] + "<|im_end|>\n";
    }
    output += "<|im_start|>assistant\n";
    return output;
}

bool modelMetadataContains(const llama_model * model, const char * key, const std::string & needle) {
    char value[512] = {};
    const int32_t length = llama_model_meta_val_str(model, key, value, sizeof(value));
    if (length <= 0) return false;
    std::string normalized(value);
    std::transform(normalized.begin(), normalized.end(), normalized.begin(),
        [](unsigned char ch) { return static_cast<char>(std::tolower(ch)); });
    return normalized.find(needle) != std::string::npos;
}

bool continueModelLoad(float, void *) {
    return !gCancel.load();
}

std::string applyModelChatTemplate(const llama_model * model, const std::string & prompt) {
    const char * templateText = llama_model_chat_template(model, nullptr);
    const std::string start = "<|im_start|>";
    const std::string end = "<|im_end|>";
    std::vector<std::string> roles;
    std::vector<std::string> contents;
    size_t cursor = 0;
    while ((cursor = prompt.find(start, cursor)) != std::string::npos) {
        cursor += start.size();
        const size_t roleEnd = prompt.find('\n', cursor);
        if (roleEnd == std::string::npos) break;
        const size_t contentEnd = prompt.find(end, roleEnd + 1);
        if (contentEnd == std::string::npos) break;
        roles.emplace_back(prompt.substr(cursor, roleEnd - cursor));
        contents.emplace_back(prompt.substr(roleEnd + 1, contentEnd - roleEnd - 1));
        cursor = contentEnd + end.size();
    }

    // Revisões internas também precisam receber o template do modelo: texto simples
    // vira uma mensagem user, em vez de ser tokenizado sem estrutura de conversa.
    if (roles.empty()) {
        roles.emplace_back("user");
        contents.emplace_back(prompt);
    }

    // Modelos de chat normalmente aceitam apenas uma mensagem system inicial.
    // Consolida blocos system adjacentes (instruções + contexto GitHub) sem
    // reordenar mensagens user/assistant do histórico.
    std::vector<std::string> normalizedRoles;
    std::vector<std::string> normalizedContents;
    normalizedRoles.reserve(roles.size());
    normalizedContents.reserve(contents.size());
    for (size_t i = 0; i < roles.size(); ++i) {
        if (roles[i] == "system" && !normalizedRoles.empty() &&
            normalizedRoles.back() == "system") {
            normalizedContents.back() += "\n" + contents[i];
        } else {
            normalizedRoles.push_back(roles[i]);
            normalizedContents.push_back(contents[i]);
        }
    }
    roles.swap(normalizedRoles);
    contents.swap(normalizedContents);

    if (templateText) {
        std::vector<llama_chat_message> messages;
        messages.reserve(roles.size());
        for (size_t i = 0; i < roles.size(); ++i) {
            messages.push_back({roles[i].c_str(), contents[i].c_str()});
        }
        std::vector<char> formatted(std::max<size_t>(4096, prompt.size() * 4 + 1024));
        const int32_t needed = llama_chat_apply_template(
            templateText, messages.data(), messages.size(), true, formatted.data(),
            static_cast<int32_t>(formatted.size()));
        if (needed > 0) {
            if (static_cast<size_t>(needed) <= formatted.size()) {
                return std::string(formatted.data(), static_cast<size_t>(needed));
            }
            formatted.resize(static_cast<size_t>(needed) + 1);
            const int32_t retry = llama_chat_apply_template(
                templateText, messages.data(), messages.size(), true, formatted.data(),
                static_cast<int32_t>(formatted.size()));
            if (retry > 0 && static_cast<size_t>(retry) <= formatted.size()) {
                return std::string(formatted.data(), static_cast<size_t>(retry));
            }
        }
    }

    // Alguns GGUF antigos não incluem tokenizer.chat_template. TinyLlama Chat
    // exige os marcadores próprios; não deve receber ChatML cru.
    std::string modelPath = gModelPath;
    std::transform(modelPath.begin(), modelPath.end(), modelPath.begin(),
        [](unsigned char ch) { return static_cast<char>(std::tolower(ch)); });
    const bool isTinyLlama =
        modelMetadataContains(model, "general.name", "tinyllama") ||
        modelMetadataContains(model, "general.basename", "tinyllama") ||
        modelPath.find("tinyllama") != std::string::npos;
    if (isTinyLlama) {
        return applyTinyLlamaTemplate(roles, contents);
    }
    return applyFallbackChatML(roles, contents);
}

void unloadLocked() {
    if (gSampler) { llama_sampler_free(gSampler); gSampler = nullptr; }
    if (gContext) { llama_free(gContext); gContext = nullptr; }
    if (gModel) { llama_model_free(gModel); gModel = nullptr; }
    gContextSize = 0;
    gModelPath.clear();
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikael_ai_LlamaInferenceEngine_nativeLoad(JNIEnv * env, jobject, jstring path, jint requestedContext, jint threads) {
    std::lock_guard<std::mutex> lock(gMutex);
    gCancel.store(true);
    unloadLocked();
    // A new load starts with a clean cancellation state. Any cancellation
    // arriving after this point remains set and is observed by the progress callback.
    gCancel.store(false);
    try {
        const std::string file = fromJava(env, path);
        if (file.empty() || file.size() > 4096) return toJava(env, "Caminho do GGUF inválido.");
        if (requestedContext < 128 || requestedContext > 2048) return toJava(env, "Contexto GGUF fora do limite seguro (128–2048).");
        if (!gBackendReady) { llama_backend_init(); gBackendReady = true; }

        llama_model_params modelParams = llama_model_default_params();
        modelParams.n_gpu_layers = 0;
        modelParams.use_extra_bufts = false;
        modelParams.check_tensors = true;
        // llama.cpp permite abortar o carregamento pelo callback de progresso.
        // O callback só consulta o atomic: não acessa o mutex mantido nesta função.
        modelParams.progress_callback = continueModelLoad;
        modelParams.progress_callback_user_data = nullptr;
        gModelPath = file;
        gModel = llama_model_load_from_file(file.c_str(), modelParams);
        if (!gModel) {
            if (gCancel.load()) return toJava(env, "Carregamento cancelado pelo usuário.");
            return toJava(env, "Não foi possível carregar o GGUF. Verifique o arquivo e a memória disponível.");
        }
        if (gCancel.load()) {
            unloadLocked();
            return toJava(env, "Carregamento cancelado pelo usuário.");
        }

        llama_context_params contextParams = llama_context_default_params();
        contextParams.n_ctx = static_cast<uint32_t>(requestedContext);
        // n_batch must hold the full prompt batch; ubatch controls physical chunking.
        contextParams.n_batch = static_cast<uint32_t>(requestedContext);
        contextParams.n_ubatch = static_cast<uint32_t>(std::min(requestedContext, 128));
        contextParams.n_seq_max = 1;
        contextParams.n_threads = std::clamp(threads, 1, 3);
        contextParams.n_threads_batch = std::clamp(threads, 1, 3);
        contextParams.offload_kqv = false;
        contextParams.op_offload = false;
        gContext = llama_init_from_model(gModel, contextParams);
        if (!gContext) {
            unloadLocked();
            return toJava(env, gCancel.load()
                ? "Carregamento cancelado pelo usuário."
                : "Não foi possível criar o contexto GGUF. Reduza o contexto ou escolha um modelo menor.");
        }
        if (gCancel.load()) {
            unloadLocked();
            return toJava(env, "Carregamento cancelado pelo usuário.");
        }
        gContextSize = llama_n_ctx(gContext);
        if (gContextSize < 128) { unloadLocked(); return toJava(env, "O runtime retornou um contexto inválido."); }
        gSampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        if (!gSampler) { unloadLocked(); return toJava(env, "Não foi possível inicializar o amostrador."); }
        // Greedy sampling favorece respostas repetitivas (ex.: palavras duplicadas).
        // Amostragem moderada + penalidade leve melhora fluidez sem exagerar a criatividade.
        const llama_vocab * samplerVocab = llama_model_get_vocab(gModel);
        llama_sampler_chain_add(gSampler, llama_sampler_init_penalties(
            llama_vocab_n_tokens(samplerVocab), 64, 1.08f, 0.0f, 0.0f));
        llama_sampler_chain_add(gSampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(gSampler, llama_sampler_init_top_p(0.90f, 1));
        llama_sampler_chain_add(gSampler, llama_sampler_init_temp(0.70f));
        const auto seed = static_cast<uint32_t>(
            std::chrono::steady_clock::now().time_since_epoch().count());
        llama_sampler_chain_add(gSampler, llama_sampler_init_dist(seed));
        return nullptr;
    } catch (...) {
        unloadLocked();
        return toJava(env, "Falha inesperada ao inicializar llama.cpp.");
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikael_ai_LlamaInferenceEngine_nativeGenerate(JNIEnv * env, jobject, jstring prompt, jint maxTokens) {
    if (!prompt) return throwError(env, "Digite uma mensagem.");
    const std::string input = fromJava(env, prompt);
    if (input.empty() || input.size() > 24000) return throwError(env, "Prompt vazio ou maior que 24 KB.");
    std::lock_guard<std::mutex> lock(gMutex);
    if (!gModel || !gContext || !gSampler) return throwError(env, "Nenhum modelo GGUF carregado.");
    const llama_vocab * vocab = llama_model_get_vocab(gModel);
    const std::string modelPrompt = applyModelChatTemplate(gModel, input);
    const int32_t probe = llama_tokenize(
        vocab, modelPrompt.c_str(), static_cast<int32_t>(modelPrompt.size()),
        nullptr, 0, true, false);
    if (probe == std::numeric_limits<int32_t>::min()) {
        return throwError(env, "O resultado da tokenização excede o limite suportado.");
    }
    if (probe == 0) return throwError(env, "O prompt não produziu tokens.");
    // Com buffer de tamanho zero, llama.cpp retorna -N quando são necessários N tokens.
    const int32_t required = probe < 0 ? -probe : probe;
    if (required <= 0) return throwError(env, "Falha ao calcular os tokens do prompt.");
    if (static_cast<uint32_t>(required) >= gContextSize) {
        return throwError(env, "O prompt ocupa todo o contexto; não há espaço para gerar uma resposta.");
    }

    std::vector<llama_token> tokens(static_cast<size_t>(required));
    const int32_t tokenCount = llama_tokenize(
        vocab, modelPrompt.c_str(), static_cast<int32_t>(modelPrompt.size()),
        tokens.data(), required, true, false);
    if (tokenCount < 0) return throwError(env, "O buffer de tokens ficou pequeno durante a tokenização.");
    if (tokenCount == 0) return throwError(env, "O prompt não produziu tokens.");
    if (tokenCount != required) return throwError(env, "A contagem de tokens mudou durante a tokenização.");

    const int availableTokens = static_cast<int>(gContextSize - static_cast<uint32_t>(tokenCount));
    const int requestedLimit = std::clamp(maxTokens, 1, 1024);
    const int limit = std::min(requestedLimit, availableTokens);
    if (limit <= 0 || static_cast<uint32_t>(tokenCount) + static_cast<uint32_t>(limit) > gContextSize) {
        return throwError(env, "Não há espaço suficiente no contexto para gerar uma resposta.");
    }

    llama_memory_clear(llama_get_memory(gContext), true);
    llama_sampler_reset(gSampler);
    llama_batch batch = llama_batch_get_one(tokens.data(), tokenCount);
    if (llama_decode(gContext, batch) != 0) return throwError(env, "Falha ao processar o prompt no llama.cpp.");

    std::string output;
    for (int i = 0; i < limit && !gCancel.load(); ++i) {
        const llama_token next = llama_sampler_sample(gSampler, gContext, -1);
        if (llama_vocab_is_eog(vocab, next)) break;
        char piece[512];
        const int32_t pieceSize = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, false);
        if (pieceSize < 0) return throwError(env, "Token gerado excede o buffer de texto.");
        output.append(piece, static_cast<size_t>(pieceSize));
        llama_sampler_accept(gSampler, next);
        llama_token token = next;
        llama_batch nextBatch = llama_batch_get_one(&token, 1);
        if (llama_decode(gContext, nextBatch) != 0) return throwError(env, "Falha ao decodificar o próximo token no llama.cpp.");
    }
    if (gCancel.load()) return throwError(env, "Geração cancelada pelo usuário.");
    return toJava(env, output);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mikael_ai_LlamaInferenceEngine_nativeContextCapacity(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    return static_cast<jint>(gContextSize);
}
extern "C" JNIEXPORT void JNICALL
Java_com_mikael_ai_LlamaInferenceEngine_nativeResetCancel(JNIEnv *, jobject) { gCancel.store(false); }
extern "C" JNIEXPORT void JNICALL
Java_com_mikael_ai_LlamaInferenceEngine_nativeCancel(JNIEnv *, jobject) { gCancel.store(true); }
extern "C" JNIEXPORT void JNICALL
Java_com_mikael_ai_LlamaInferenceEngine_nativeUnload(JNIEnv *, jobject) {
    gCancel.store(true);
    std::lock_guard<std::mutex> lock(gMutex);
    unloadLocked();
}
