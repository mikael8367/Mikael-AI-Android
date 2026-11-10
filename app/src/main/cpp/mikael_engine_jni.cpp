#include <jni.h>
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <limits>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

namespace {
constexpr char kMagic[8] = {'M','K','A','I','0','0','0','1'};
constexpr uint32_t kVersion = 1;
constexpr uint32_t kVocab = 256;
std::mutex gMutex;
std::atomic<bool> gCancel{false};

struct LayerWeights {
    std::vector<float> normAtt, q, k, v, o;
    std::vector<float> normFfn, gate, up, down;
};
struct Model {
    uint32_t dim=0, layers=0, heads=0, ffn=0, maxSeq=0, activeContext=0;
    std::vector<float> tokenEmb, positionEmb, finalNorm, lmHead;
    std::vector<LayerWeights> layer;
    bool loaded=false;
} gModel;

bool readFloats(std::ifstream& in, std::vector<float>& out, size_t count) {
    if (count > 250000000ULL) return false;
    out.resize(count);
    if (count == 0) return true;
    in.read(reinterpret_cast<char*>(out.data()), static_cast<std::streamsize>(count * sizeof(float)));
    if (!in) return false;
    for (float x : out) if (!std::isfinite(x)) return false;
    return true;
}
size_t square(uint32_t a) { return static_cast<size_t>(a) * a; }
void rmsNorm(const float* x, const std::vector<float>& w, float* y, uint32_t n) {
    double sum=0.0; for(uint32_t i=0;i<n;++i) sum += double(x[i])*x[i];
    const float scale=1.0f/std::sqrt(static_cast<float>(sum/n)+1.0e-5f);
    for(uint32_t i=0;i<n;++i) y[i]=x[i]*scale*w[i];
}
void matvec(const std::vector<float>& w, const float* x, float* y, uint32_t rows, uint32_t cols) {
    for(uint32_t r=0;r<rows;++r) {
        const float* row=w.data()+static_cast<size_t>(r)*cols;
        double sum=0.0; for(uint32_t c=0;c<cols;++c) sum += double(row[c])*x[c];
        y[r]=static_cast<float>(sum);
    }
}
bool loadModel(const std::string& path, uint32_t requestedContext, std::string& error) {
    std::ifstream in(path, std::ios::binary);
    if(!in) { error="Não foi possível abrir o arquivo MKAI."; return false; }
    char magic[8]{}; uint32_t h[8]{};
    in.read(magic,8); in.read(reinterpret_cast<char*>(h),sizeof(h));
    if(!in || std::memcmp(magic,kMagic,8)!=0 || h[0]!=kVersion || h[7]!=0) {
        error="Formato inválido. O motor próprio aceita somente MKAI0001, não GGUF."; return false;
    }
    const uint32_t dim=h[1], layers=h[2], heads=h[3], ffn=h[4], vocab=h[5], maxSeq=h[6];
    if(requestedContext>maxSeq) { error="O contexto solicitado é maior que o suportado pelo modelo."; return false; }
    if(dim<16 || dim>1024 || layers==0 || layers>16 || heads==0 || heads>32 ||
       dim%heads!=0 || ffn<dim || ffn>4096 || vocab!=kVocab || maxSeq<16 || maxSeq>8192) {
        error="Configuração do modelo MKAI fora dos limites seguros."; return false;
    }
    const uint64_t d=dim, f=ffn, s=maxSeq, l=layers, v=vocab;
    const uint64_t totalFloats = v*d + s*d + l*(2*d + 4*d*d + 3*f*d) + d + v*d;
    if(totalFloats>60000000ULL) { error="Este modelo excede o limite de memória seguro do motor Android."; return false; }
    in.seekg(0,std::ios::end);
    const std::streamoff fileSize=static_cast<std::streamoff>(in.tellg());
    const uint64_t expectedBytes=40ULL+totalFloats*sizeof(float);
    if(fileSize<0 || static_cast<uint64_t>(fileSize)!=expectedBytes) {
        error="O tamanho do arquivo não corresponde às dimensões declaradas no cabeçalho MKAI."; return false;
    }
    in.seekg(40,std::ios::beg);
    Model candidate; candidate.dim=dim; candidate.layers=layers; candidate.heads=heads;
    candidate.ffn=ffn; candidate.maxSeq=maxSeq; candidate.activeContext=std::min(requestedContext,maxSeq);
    if(!readFloats(in,candidate.tokenEmb,static_cast<size_t>(vocab)*dim) ||
       !readFloats(in,candidate.positionEmb,static_cast<size_t>(maxSeq)*dim)) {
        error="Pesos de embedding ausentes ou corrompidos."; return false;
    }
    candidate.layer.resize(layers);
    for(auto& l:candidate.layer) {
        if(!readFloats(in,l.normAtt,dim) || !readFloats(in,l.q,square(dim)) ||
           !readFloats(in,l.k,square(dim)) || !readFloats(in,l.v,square(dim)) ||
           !readFloats(in,l.o,square(dim)) || !readFloats(in,l.normFfn,dim) ||
           !readFloats(in,l.gate,static_cast<size_t>(ffn)*dim) ||
           !readFloats(in,l.up,static_cast<size_t>(ffn)*dim) ||
           !readFloats(in,l.down,static_cast<size_t>(dim)*ffn)) {
            error="Pesos de uma camada Transformer estão incompletos ou corrompidos."; return false;
        }
    }
    if(!readFloats(in,candidate.finalNorm,dim) ||
       !readFloats(in,candidate.lmHead,static_cast<size_t>(vocab)*dim)) {
        error="Pesos finais do modelo ausentes ou corrompidos."; return false;
    }
    char extra; if(in.read(&extra,1)) { error="O arquivo contém dados extras após os pesos."; return false; }
    candidate.loaded=true; gModel=std::move(candidate);
    return true;
}
bool forward(const std::vector<uint8_t>& tokens, std::vector<float>& logits) {
    const uint32_t d=gModel.dim, headDim=d/gModel.heads, seq=static_cast<uint32_t>(tokens.size());
    if(seq==0 || seq>gModel.maxSeq) return false;
    std::vector<float> x(static_cast<size_t>(seq)*d), norm(static_cast<size_t>(seq)*d);
    for(uint32_t t=0;t<seq;++t) for(uint32_t i=0;i<d;++i)
        x[static_cast<size_t>(t)*d+i]=gModel.tokenEmb[static_cast<size_t>(tokens[t])*d+i]+gModel.positionEmb[static_cast<size_t>(t)*d+i];
    std::vector<float> q(d), k(d), v(d), projected(d), attn(d), scores(seq), gate(gModel.ffn), up(gModel.ffn), ff(d);
    for(const auto& layer:gModel.layer) {
        std::vector<float> keys(static_cast<size_t>(seq)*d), values(static_cast<size_t>(seq)*d);
        std::vector<float> next=x;
        for(uint32_t t=0;t<seq;++t) {
            float* xt=x.data()+static_cast<size_t>(t)*d;
            rmsNorm(xt,layer.normAtt,norm.data()+static_cast<size_t>(t)*d,d);
            const float* nt=norm.data()+static_cast<size_t>(t)*d;
            matvec(layer.q,nt,q.data(),d,d); matvec(layer.k,nt,k.data(),d,d); matvec(layer.v,nt,v.data(),d,d);
            std::copy(k.begin(),k.end(),keys.begin()+static_cast<size_t>(t)*d);
            std::copy(v.begin(),v.end(),values.begin()+static_cast<size_t>(t)*d);
            std::fill(attn.begin(),attn.end(),0.0f);
            for(uint32_t h=0;h<gModel.heads;++h) {
                const uint32_t base=h*headDim; float maxScore=-std::numeric_limits<float>::infinity();
                for(uint32_t j=0;j<=t;++j) {
                    double dot=0.0; const float* kj=keys.data()+static_cast<size_t>(j)*d+base;
                    for(uint32_t z=0;z<headDim;++z) dot+=double(q[base+z])*kj[z];
                    scores[j]=static_cast<float>(dot/std::sqrt(static_cast<float>(headDim)));
                    maxScore=std::max(maxScore,scores[j]);
                }
                double denom=0.0;
                for(uint32_t j=0;j<=t;++j) { scores[j]=std::exp(scores[j]-maxScore); denom+=scores[j]; }
                if(denom<=0.0 || !std::isfinite(denom)) return false;
                for(uint32_t j=0;j<=t;++j) {
                    const float weight=static_cast<float>(scores[j]/denom);
                    const float* vj=values.data()+static_cast<size_t>(j)*d+base;
                    for(uint32_t z=0;z<headDim;++z) attn[base+z]+=weight*vj[z];
                }
            }
            matvec(layer.o,attn.data(),projected.data(),d,d);
            for(uint32_t i=0;i<d;++i) next[static_cast<size_t>(t)*d+i]+=projected[i];
        }
        x.swap(next);
        for(uint32_t t=0;t<seq;++t) {
            float* xt=x.data()+static_cast<size_t>(t)*d;
            rmsNorm(xt,layer.normFfn,norm.data()+static_cast<size_t>(t)*d,d);
            const float* nt=norm.data()+static_cast<size_t>(t)*d;
            matvec(layer.gate,nt,gate.data(),gModel.ffn,d);
            matvec(layer.up,nt,up.data(),gModel.ffn,d);
            for(uint32_t i=0;i<gModel.ffn;++i) {
                const float a=std::clamp(gate[i],-20.0f,20.0f);
                gate[i]=(a/(1.0f+std::exp(-a)))*up[i];
            }
            matvec(layer.down,gate.data(),ff.data(),d,gModel.ffn);
            for(uint32_t i=0;i<d;++i) xt[i]+=ff[i];
        }
        if(gCancel.load()) return false;
    }
    rmsNorm(x.data()+static_cast<size_t>(seq-1)*d,gModel.finalNorm,projected.data(),d);
    matvec(gModel.lmHead,projected.data(),logits.data(),kVocab,d);
    return true;
}
std::string jstringToUtf8(JNIEnv* env,jstring value) {
    if(!value) return {};
    const jchar* p=env->GetStringChars(value,nullptr); if(!p) return {};
    jsize n=env->GetStringLength(value); std::string s; s.reserve(n*3);
    for(jsize i=0;i<n;++i) {
        uint32_t c=p[i];
        if(c>=0xD800 && c<=0xDBFF && i+1<n && p[i+1]>=0xDC00 && p[i+1]<=0xDFFF) { c=0x10000+((c-0xD800)<<10)+(p[++i]-0xDC00); }
        else if(c>=0xD800 && c<=0xDFFF) c=0xFFFD;
        if(c<0x80)s.push_back(static_cast<char>(c));
        else if(c<0x800){s.push_back(static_cast<char>(0xC0|(c>>6)));s.push_back(static_cast<char>(0x80|(c&63)));}
        else if(c<0x10000){s.push_back(static_cast<char>(0xE0|(c>>12)));s.push_back(static_cast<char>(0x80|((c>>6)&63)));s.push_back(static_cast<char>(0x80|(c&63)));}
        else{s.push_back(static_cast<char>(0xF0|(c>>18)));s.push_back(static_cast<char>(0x80|((c>>12)&63)));s.push_back(static_cast<char>(0x80|((c>>6)&63)));s.push_back(static_cast<char>(0x80|(c&63)));}
    }
    env->ReleaseStringChars(value,p); return s;
}
jstring utf8ToJava(JNIEnv* env,const std::string& value) {
    std::vector<jchar> out; out.reserve(value.size());
    size_t i=0;
    while(i<value.size()) {
        const uint8_t first=static_cast<uint8_t>(value[i]);
        uint32_t cp=0xFFFD; size_t count=1;
        if(first<0x80) cp=first;
        else if((first&0xE0)==0xC0 && i+1<value.size()) { cp=((first&0x1F)<<6)|(static_cast<uint8_t>(value[i+1])&0x3F); count=2; }
        else if((first&0xF0)==0xE0 && i+2<value.size()) { cp=((first&0x0F)<<12)|((static_cast<uint8_t>(value[i+1])&0x3F)<<6)|(static_cast<uint8_t>(value[i+2])&0x3F); count=3; }
        else if((first&0xF8)==0xF0 && i+3<value.size()) { cp=((first&7)<<18)|((static_cast<uint8_t>(value[i+1])&0x3F)<<12)|((static_cast<uint8_t>(value[i+2])&0x3F)<<6)|(static_cast<uint8_t>(value[i+3])&0x3F); count=4; }
        bool valid=true;
        for(size_t j=1;j<count;++j) if((static_cast<uint8_t>(value[i+j])&0xC0)!=0x80) valid=false;
        const uint32_t min=count==2?0x80:(count==3?0x800:(count==4?0x10000:0));
        if(!valid || cp<min || cp>0x10FFFF || (cp>=0xD800 && cp<=0xDFFF)) { cp=0xFFFD; count=1; }
        if(cp<=0xFFFF) out.push_back(static_cast<jchar>(cp));
        else { cp-=0x10000; out.push_back(static_cast<jchar>(0xD800+(cp>>10))); out.push_back(static_cast<jchar>(0xDC00+(cp&0x3FF))); }
        i+=count;
    }
    if(out.size()>static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
    static const jchar emptyText[] = {0};
    return env->NewString(out.empty() ? emptyText : out.data(),static_cast<jsize>(out.size()));
}
jstring error(JNIEnv* env,const std::string& s){return env->NewStringUTF(s.c_str());}
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_mikael_ai_LocalInferenceEngine_nativeLoad(JNIEnv* env,jobject,jstring path,jint context,jint) {
    std::lock_guard<std::mutex> lock(gMutex); gCancel.store(true); gModel=Model{}; gCancel.store(false);
    if(!path || context<16 || context>8192) return error(env,"Caminho ou contexto inválido.");
    std::string message;
    if(!loadModel(jstringToUtf8(env,path),static_cast<uint32_t>(context),message)) return error(env,message);
    return nullptr;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_mikael_ai_LocalInferenceEngine_nativeGenerate(JNIEnv* env,jobject,jstring prompt,jint maxTokens) {
    if(!prompt) return error(env,"Digite uma mensagem.");
    const std::string input=jstringToUtf8(env,prompt);
    if(input.empty() || input.size()>24000) return error(env,"Texto vazio ou muito longo.");
    std::lock_guard<std::mutex> lock(gMutex);
    if(!gModel.loaded) return error(env,"Nenhum modelo MKAI próprio está carregado.");
    std::vector<uint8_t> tokens(input.begin(),input.end());
    if(tokens.size()>=gModel.activeContext) return error(env,"O prompt ultrapassa o contexto do modelo.");
    std::string output; std::vector<float> logits(kVocab);
    const int limit=std::clamp(maxTokens,1,512);
    gCancel.store(false);
    for(int i=0;i<limit && tokens.size()<gModel.activeContext && !gCancel.load();++i) {
        if(!forward(tokens,logits)) {
            if(gCancel.load()) break;
            return error(env,"Falha numérica durante a inferência Transformer.");
        }
        const auto it=std::max_element(logits.begin(),logits.end());
        if(it==logits.end() || !std::isfinite(*it)) return error(env,"O modelo produziu logits inválidos.");
        const uint8_t next=static_cast<uint8_t>(std::distance(logits.begin(),it));
        tokens.push_back(next); output.push_back(static_cast<char>(next));
    }
    return utf8ToJava(env,output);
}
extern "C" JNIEXPORT jint JNICALL
Java_com_mikael_ai_LocalInferenceEngine_nativeContextCapacity(JNIEnv*,jobject){
    std::lock_guard<std::mutex> lock(gMutex);
    return gModel.loaded ? static_cast<jint>(gModel.activeContext) : 0;
}
extern "C" JNIEXPORT void JNICALL
Java_com_mikael_ai_LocalInferenceEngine_nativeCancel(JNIEnv*,jobject){gCancel.store(true);}
extern "C" JNIEXPORT void JNICALL
Java_com_mikael_ai_LocalInferenceEngine_nativeUnload(JNIEnv*,jobject){std::lock_guard<std::mutex> lock(gMutex);gCancel.store(true);gModel=Model{};}
