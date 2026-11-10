#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

namespace {
std::mutex gMutex;
std::atomic<bool> gCancel{false};
llama_model * gModel = nullptr;
llama_context * gContext = nullptr;
llama_sampler * gSampler = nullptr;
uint32_t gContextSize = 0;
bool gBackendReady = false;

std::string fromJava(JNIEnv * env, jstring value) {
    if (!value) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
jstring toJava(JNIEnv * env, const std::string & value) {
    return env->NewStringUTF(value.c_str());
}
jstring throwError(JNIEnv * env, const std::string & message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls) { env->ThrowNew(cls, message.c_str()); env->DeleteLocalRef(cls); }
    return nullptr;
}
void unloadLocked() {
    if (gSampler) { llama_sampler_free(gSampler); gSampler = nullptr; }
    if (gContext) { llama_free(gContext); gContext = nullptr; }
    if (gModel) { llama_model_free(gModel); gModel = nullptr; }
    gContextSize = 0;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mikael_ai_LlamaInferenceEngine_nativeLoad(JNIEnv * env, jobject, jstring path, jint requestedContext, jint threads) {
    std::lock_guard<std::mutex> lock(gMutex);
    gCancel.store(true);
    unloadLocked();
    try {
        const std::string file = fromJava(env, path);
        if (file.empty() || file.size() > 4096) return toJava(env, "Caminho do GGUF inválido.");
        if (requestedContext < 128 || requestedContext > 2048) return toJava(env, "Contexto GGUF fora do limite seguro (128–2048).");
        if (!gBackendReady) { llama_backend_init(); gBackendReady = true; }
        gCancel.store(false);

        llama_model_params modelParams = llama_model_default_params();
        modelParams.n_gpu_layers = 0;
        modelParams.use_extra_bufts = false;
        modelParams.check_tensors = true;
        gModel = llama_model_load_from_file(file.c_str(), modelParams);
        if (!gModel) return toJava(env, "Não foi possível carregar o GGUF. Verifique o arquivo e a memória disponível.");

        llama_context_params contextParams = llama_context_default_params();
        contextParams.n_ctx = static_cast<uint32_t>(requestedContext);
        contextParams.n_batch = static_cast<uint32_t>(std::min(requestedContext, 256));
        contextParams.n_ubatch = static_cast<uint32_t>(std::min(requestedContext, 128));
        contextParams.n_seq_max = 1;
        contextParams.n_threads = std::clamp(threads, 1, 3);
        contextParams.n_threads_batch = std::clamp(threads, 1, 3);
        contextParams.offload_kqv = false;
        contextParams.op_offload = false;
        gContext = llama_init_from_model(gModel, contextParams);
        if (!gContext) { unloadLocked(); return toJava(env, "Não foi possível criar o contexto GGUF. Reduza o contexto ou escolha um modelo menor."); }
        gContextSize = llama_n_ctx(gContext);
        if (gContextSize < 128) { unloadLocked(); return toJava(env, "O runtime retornou um contexto inválido."); }
        gSampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        if (!gSampler) { unloadLocked(); return toJava(env, "Não foi possível inicializar o amostrador."); }
        llama_sampler_chain_add(gSampler, llama_sampler_init_greedy());
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
    const int32_t required = -llama_tokenize(vocab, input.c_str(), static_cast<int32_t>(input.size()), nullptr, 0, true, false);
    if (required <= 0 || required > static_cast<int32_t>(gContextSize) - 2) return throwError(env, "O prompt excede o contexto disponível.");
    std::vector<llama_token> tokens(static_cast<size_t>(required));
    const int32_t tokenCount = llama_tokenize(vocab, input.c_str(), static_cast<int32_t>(input.size()), tokens.data(), required, true, false);
    if (tokenCount <= 0 || tokenCount > required) return throwError(env, "Falha ao tokenizar o prompt.");
    llama_memory_clear(llama_get_memory(gContext), true);
    llama_sampler_reset(gSampler);
    llama_batch batch = llama_batch_get_one(tokens.data(), tokenCount);
    if (llama_decode(gContext, batch) != 0) return throwError(env, "Falha ao processar o prompt no llama.cpp.");

    std::string output;
    const int limit = std::clamp(maxTokens, 1, 256);
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
        if (llama_decode(gContext, nextBatch) != 0) break;
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
