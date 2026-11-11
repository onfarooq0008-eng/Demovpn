// JNI glue between Kotlin (FastVpnService/OvpnBridge) and the OpenVPN 3 core client API.
// The core owns TLS, auth, the data channel and reconnects. This file only forwards TUN-builder
// calls to VpnService.Builder (via OvpnBridge) and reports events back.
#include <jni.h>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <android/log.h>
#include <client/ovpncli.hpp>

using namespace openvpn;

static JavaVM* g_vm = nullptr;
static jclass g_cls = nullptr;
static jmethodID m_tunNew, m_addAddr, m_reroute, m_addRoute, m_addDns, m_addSearch, m_setMtu, m_setSession,
    m_establish, m_teardown, m_protect, m_event, m_connected, m_finished, m_log;

struct Env {
    JNIEnv* e = nullptr; bool attached = false;
    Env() {
        if (g_vm->GetEnv((void**)&e, JNI_VERSION_1_6) == JNI_EDETACHED) { g_vm->AttachCurrentThread(&e, nullptr); attached = true; }
    }
    ~Env() { if (attached) g_vm->DetachCurrentThread(); }
    jstring s(const std::string& v) { return e->NewStringUTF(v.c_str()); }
};

class FastClient : public ClientAPI::OpenVPNClient {
public:
    explicit FastClient(int g) : gen(g) {}
    int gen;

    // ---- TunBuilder -> VpnService.Builder ----
    bool tun_builder_new() override { Env v; return v.e->CallStaticBooleanMethod(g_cls, m_tunNew); }
    bool tun_builder_set_layer(int layer) override { return layer == 3; }
    bool tun_builder_set_remote_address(const std::string&, bool) override { return true; }
    bool tun_builder_add_address(const std::string& a, int prefix, const std::string&, bool ipv6, bool) override {
        Env v; return v.e->CallStaticBooleanMethod(g_cls, m_addAddr, v.s(a), (jint)prefix, (jboolean)ipv6);
    }
    bool tun_builder_set_route_metric_default(int) override { return true; }
    bool tun_builder_reroute_gw(bool ipv4, bool ipv6, unsigned int) override {
        Env v; return v.e->CallStaticBooleanMethod(g_cls, m_reroute, (jboolean)ipv4, (jboolean)ipv6);
    }
    bool tun_builder_add_route(const std::string& a, int prefix, int, bool ipv6) override {
        Env v; return v.e->CallStaticBooleanMethod(g_cls, m_addRoute, v.s(a), (jint)prefix, (jboolean)ipv6);
    }
    bool tun_builder_exclude_route(const std::string&, int, int, bool) override { return true; } // server socket is protect()ed
    bool tun_builder_add_dns_server(const std::string& a, bool) override {
        Env v; return v.e->CallStaticBooleanMethod(g_cls, m_addDns, v.s(a));
    }
    bool tun_builder_add_search_domain(const std::string& d) override {
        Env v; return v.e->CallStaticBooleanMethod(g_cls, m_addSearch, v.s(d));
    }
    bool tun_builder_set_mtu(int mtu) override { Env v; return v.e->CallStaticBooleanMethod(g_cls, m_setMtu, (jint)mtu); }
    bool tun_builder_set_session_name(const std::string& n) override {
        Env v; return v.e->CallStaticBooleanMethod(g_cls, m_setSession, v.s(n));
    }
    int tun_builder_establish() override { Env v; return v.e->CallStaticIntMethod(g_cls, m_establish); }
    bool tun_builder_persist() override { return false; }
    void tun_builder_teardown(bool) override { Env v; v.e->CallStaticVoidMethod(g_cls, m_teardown); }

    // ---- client callbacks ----
    bool socket_protect(int socket, std::string, bool) override {
        Env v; return v.e->CallStaticBooleanMethod(g_cls, m_protect, (jint)socket);
    }
    bool pause_on_connection_timeout() override { return false; }
    void event(const ClientAPI::Event& ev) override {
        Env v;
        v.e->CallStaticVoidMethod(g_cls, m_event, (jint)gen, v.s(ev.name), v.s(ev.info), (jboolean)ev.error, (jboolean)ev.fatal);
        if (ev.name == "CONNECTED") {
            ClientAPI::ConnectionInfo ci = connection_info();
            v.e->CallStaticVoidMethod(g_cls, m_connected, (jint)gen, v.s(ci.vpnIp4), v.s(ci.serverProto));
        }
    }
    void log(const ClientAPI::LogInfo& li) override {
        Env v; v.e->CallStaticVoidMethod(g_cls, m_log, v.s(li.text.substr(0, 300)));
    }
};

static std::mutex g_mu;
static std::shared_ptr<FastClient> g_client;
static std::thread g_thread;

static void run(std::shared_ptr<FastClient> c, std::string content) {
    ClientAPI::Config cfg;
    cfg.content = content;
    cfg.guiVersion = "FastVPN 2.0";
    cfg.connTimeout = 30;
    cfg.tunPersist = false;
    cfg.googleDnsFallback = false;
    std::string err;
    ClientAPI::EvalConfig ev = c->eval_config(cfg);
    if (ev.error) err = ev.message.empty() ? "Invalid configuration" : ev.message;
    else {
        ClientAPI::Status st = c->connect();               // blocks until disconnected / failed
        if (st.error) err = st.status + (st.message.empty() ? "" : ": " + st.message);
    }
    Env v;
    v.e->CallStaticVoidMethod(g_cls, m_finished, (jint)c->gen, v.s(err));
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    JNIEnv* e; vm->GetEnv((void**)&e, JNI_VERSION_1_6);
    g_cls = (jclass)e->NewGlobalRef(e->FindClass("com/fastvpn/app7k2q9x/vpn/OvpnBridge"));
    auto S = [&](const char* n, const char* sig) { return e->GetStaticMethodID(g_cls, n, sig); };
    m_tunNew = S("tunNew", "()Z");
    m_addAddr = S("tunAddAddress", "(Ljava/lang/String;IZ)Z");
    m_reroute = S("tunReroute", "(ZZ)Z");
    m_addRoute = S("tunAddRoute", "(Ljava/lang/String;IZ)Z");
    m_addDns = S("tunAddDns", "(Ljava/lang/String;)Z");
    m_addSearch = S("tunAddSearch", "(Ljava/lang/String;)Z");
    m_setMtu = S("tunSetMtu", "(I)Z");
    m_setSession = S("tunSetSession", "(Ljava/lang/String;)Z");
    m_establish = S("tunEstablish", "()I");
    m_teardown = S("tunTeardown", "()V");
    m_protect = S("protect", "(I)Z");
    m_event = S("onEvent", "(ILjava/lang/String;Ljava/lang/String;ZZ)V");
    m_connected = S("onConnected", "(ILjava/lang/String;Ljava/lang/String;)V");
    m_finished = S("onFinished", "(ILjava/lang/String;)V");
    m_log = S("log", "(Ljava/lang/String;)V");
    ClientAPI::OpenVPNClient::init_process();
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL Java_com_fastvpn_app7k2q9x_vpn_NativeOvpn_start(JNIEnv* env, jclass, jstring cfg, jint gen) {
    const char* c = env->GetStringUTFChars(cfg, nullptr);
    std::string content(c); env->ReleaseStringUTFChars(cfg, c);
    std::lock_guard<std::mutex> lk(g_mu);
    auto oldClient = g_client;
    std::thread oldThread = std::move(g_thread);
    auto client = std::make_shared<FastClient>(gen);
    g_client = client;
    // The previous tunnel (if any) is fully stopped before the new one starts: never two tunnels.
    g_thread = std::thread([client, oldClient, content, t = std::move(oldThread)]() mutable {
        if (oldClient) oldClient->stop();
        if (t.joinable()) t.join();
        run(client, content);
    });
}

extern "C" JNIEXPORT void JNICALL Java_com_fastvpn_app7k2q9x_vpn_NativeOvpn_stop(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (g_client) g_client->stop();
}

extern "C" JNIEXPORT void JNICALL Java_com_fastvpn_app7k2q9x_vpn_NativeOvpn_reconnect(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lk(g_mu);
    if (g_client) g_client->reconnect(0);
}

extern "C" JNIEXPORT jlongArray JNICALL Java_com_fastvpn_app7k2q9x_vpn_NativeOvpn_bytes(JNIEnv* env, jclass) {
    jlong v[2] = {0, 0};
    { std::lock_guard<std::mutex> lk(g_mu);
      if (g_client) { ClientAPI::TransportStats ts = g_client->transport_stats(); v[0] = ts.bytesIn; v[1] = ts.bytesOut; } }
    jlongArray a = env->NewLongArray(2);
    env->SetLongArrayRegion(a, 0, 2, v);
    return a;
}
