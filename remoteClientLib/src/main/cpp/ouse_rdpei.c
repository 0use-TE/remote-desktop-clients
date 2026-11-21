/* OuseRDP RDPEI JNI bridge. Built against bundled FreeRDP 2.11.7 headers. */
#include <jni.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <freerdp/freerdp.h>
#include <freerdp/event.h>
#include <freerdp/settings.h>
#include <freerdp/client/rdpei.h>

typedef struct {
    BOOL active;
    INT32 x, y;
} Contact;
typedef struct TouchSession {
    freerdp* instance;
    RdpeiClientContext* channel;
    Contact contacts[32];
    struct TouchSession* next;
} TouchSession;
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static TouchSession* sessions;
static TouchSession* find(freerdp* instance) {
    for (TouchSession* s = sessions; s; s = s->next) if (s->instance == instance) return s;
    return NULL;
}
static void connected(void* context, ChannelConnectedEventArgs* event) {
    if (!context || !event || strcmp(event->name, RDPEI_DVC_CHANNEL_NAME) != 0) return;
    pthread_mutex_lock(&lock);
    TouchSession* s = find(((rdpContext*)context)->instance);
    if (s) { s->channel = event->pInterface; memset(s->contacts, 0, sizeof(s->contacts)); }
    pthread_mutex_unlock(&lock);
    __android_log_print(ANDROID_LOG_INFO, "OuseTouch", "RDPEI touch channel connected");
}
static void disconnected(void* context, ChannelDisconnectedEventArgs* event) {
    if (!context || !event || strcmp(event->name, RDPEI_DVC_CHANNEL_NAME) != 0) return;
    pthread_mutex_lock(&lock);
    TouchSession* s = find(((rdpContext*)context)->instance);
    if (s) { s->channel = NULL; memset(s->contacts, 0, sizeof(s->contacts)); }
    pthread_mutex_unlock(&lock);
}
JNIEXPORT jboolean JNICALL Java_com_undatech_opaque_NativeRdpTouch_nativeAttach(JNIEnv* env, jclass cls, jlong handle) {
    freerdp* instance = (freerdp*)(uintptr_t)handle;
    if (!instance || !instance->context || !instance->context->pubSub) return JNI_FALSE;
    if (!freerdp_settings_set_bool(instance->settings, FreeRDP_MultiTouchInput, TRUE) ||
        !freerdp_settings_set_bool(instance->settings, FreeRDP_SupportDynamicChannels, TRUE)) return JNI_FALSE;
    pthread_mutex_lock(&lock);
    if (find(instance)) { pthread_mutex_unlock(&lock); return JNI_TRUE; }
    TouchSession* s = calloc(1, sizeof(*s));
    if (!s) { pthread_mutex_unlock(&lock); return JNI_FALSE; }
    s->instance = instance; s->next = sessions; sessions = s;
    pthread_mutex_unlock(&lock);
    int first = PubSub_SubscribeChannelConnected(instance->context->pubSub, connected);
    int second = PubSub_SubscribeChannelDisconnected(instance->context->pubSub, disconnected);
    return first == 0 && second == 0;
}
JNIEXPORT jboolean JNICALL Java_com_undatech_opaque_NativeRdpTouch_nativeReady(JNIEnv* env, jclass cls, jlong handle) {
    pthread_mutex_lock(&lock);
    TouchSession* s = find((freerdp*)(uintptr_t)handle);
    BOOL ready = s && s->channel;
    pthread_mutex_unlock(&lock);
    return ready ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL Java_com_undatech_opaque_NativeRdpTouch_nativeDetach(JNIEnv* env, jclass cls, jlong handle) {
    freerdp* instance = (freerdp*)(uintptr_t)handle;
    pthread_mutex_lock(&lock);
    TouchSession** link = &sessions;
    while (*link && (*link)->instance != instance) link = &(*link)->next;
    TouchSession* s = *link;
    if (s) *link = s->next;
    pthread_mutex_unlock(&lock);
    if (s) {
        PubSub_UnsubscribeChannelConnected(instance->context->pubSub, connected);
        PubSub_UnsubscribeChannelDisconnected(instance->context->pubSub, disconnected);
        free(s);
    }
}
JNIEXPORT jboolean JNICALL Java_com_undatech_opaque_NativeRdpTouch_nativeSend(JNIEnv* env, jclass cls, jlong handle, jint action, jint id, jint x, jint y) {
    if (id < 0 || id >= 32) return JNI_FALSE;
    pthread_mutex_lock(&lock);
    TouchSession* s = find((freerdp*)(uintptr_t)handle);
    UINT status = 1;
    INT32 contactId = -1;
    if (s && s->channel) {
        Contact* contact = &s->contacts[id];
        if (action == 0 && !contact->active)
            status = s->channel->TouchBegin(s->channel, id, x, y, &contactId);
        else if (action == 1 && contact->active)
            status = s->channel->TouchUpdate(s->channel, id, x, y, &contactId);
        else if (action == 2 && contact->active)
            status = s->channel->TouchEnd(s->channel, id, x, y, &contactId);
        if (status == 0 && contactId >= 0) {
            contact->active = action != 2; contact->x = x; contact->y = y;
        } else if (status == 0) status = 1;
    }
    pthread_mutex_unlock(&lock);
    if (status != 0) __android_log_print(ANDROID_LOG_WARN, "OuseTouch", "Touch action=%d id=%d failed (%u)", action, id, status);
    return status == 0 ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL Java_com_undatech_opaque_NativeRdpTouch_nativeCancel(JNIEnv* env, jclass cls, jlong handle) {
    pthread_mutex_lock(&lock);
    TouchSession* s = find((freerdp*)(uintptr_t)handle);
    if (s && s->channel) {
        for (INT32 id = 0; id < 32; id++) if (s->contacts[id].active) {
            INT32 contactId;
            s->channel->TouchEnd(s->channel, id, s->contacts[id].x, s->contacts[id].y, &contactId);
            s->contacts[id].active = FALSE;
        }
    }
    pthread_mutex_unlock(&lock);
}
