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
#include <freerdp/client/disp.h>
#include <freerdp/update.h>
#include <freerdp/client/cliprdr.h>
#include "ouse_files.c"

typedef struct {
    BOOL active;
    INT32 x, y;
} Contact;
typedef struct TouchSession {
    freerdp* instance;
    RdpeiClientContext* channel;
    DispClientContext* display;
    pcDispCaps originalCaps;
    UINT32 maxMonitors;
    UINT64 maxArea;
    CliprdrClientContext* clipboard;
    pcCliprdrServerFormatDataRequest originalRequest;
    pcCliprdrServerFormatList originalList;
    BYTE* imagePng;
    UINT32 pngSize;
    BYTE* imageDib;
    UINT32 dibSize;
    Contact contacts[32];
    struct TouchSession* next;
} TouchSession;
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static TouchSession* sessions;
static TouchSession* find(freerdp* instance) {
    for (TouchSession* s = sessions; s; s = s->next) if (s->instance == instance) return s;
    return NULL;
}
#include "ouse_clipboard.c"
static UINT display_caps(DispClientContext* channel, UINT32 monitors, UINT32 factorA, UINT32 factorB) {
    pcDispCaps previous = NULL;
    pthread_mutex_lock(&lock);
    for (TouchSession* s = sessions; s; s = s->next) if (s->display == channel) {
        s->maxMonitors = monitors;
        s->maxArea = (UINT64)factorA * factorB;
        previous = s->originalCaps;
        break;
    }
    pthread_mutex_unlock(&lock);
    return previous ? previous(channel, monitors, factorA, factorB) : 0;
}
static void connected(void* context, ChannelConnectedEventArgs* event) {
    if (!context || !event || !event->name) return;
    pthread_mutex_lock(&lock);
    TouchSession* s = find(((rdpContext*)context)->instance);
    if (s && strcmp(event->name, RDPEI_DVC_CHANNEL_NAME) == 0) {
        s->channel = event->pInterface;
        memset(s->contacts, 0, sizeof(s->contacts));
    } else if (s && strcmp(event->name, CLIPRDR_SVC_CHANNEL_NAME) == 0 && event->pInterface) {
        if (s->clipboard != event->pInterface) { clipboard_detach(s); s->clipboard = event->pInterface; }
    } else if (s && strcmp(event->name, DISP_DVC_CHANNEL_NAME) == 0 && event->pInterface && s->display != event->pInterface) {
        if (s->display && s->display->DisplayControlCaps == display_caps)
            s->display->DisplayControlCaps = s->originalCaps;
        s->display = event->pInterface;
        s->originalCaps = s->display->DisplayControlCaps;
        s->display->DisplayControlCaps = display_caps;
        s->maxMonitors = 0;
    }
    pthread_mutex_unlock(&lock);
}
static void disconnected(void* context, ChannelDisconnectedEventArgs* event) {
    if (!context || !event || !event->name) return;
    pthread_mutex_lock(&lock);
    TouchSession* s = find(((rdpContext*)context)->instance);
    if (s && strcmp(event->name, RDPEI_DVC_CHANNEL_NAME) == 0) {
        s->channel = NULL;
        memset(s->contacts, 0, sizeof(s->contacts));
    } else if (s && strcmp(event->name, CLIPRDR_SVC_CHANNEL_NAME) == 0) {
        clipboard_detach(s);
    } else if (s && strcmp(event->name, DISP_DVC_CHANNEL_NAME) == 0) {
        if (s->display && s->display->DisplayControlCaps == display_caps)
            s->display->DisplayControlCaps = s->originalCaps;
        s->display = NULL;
        s->maxMonitors = 0;
    }
    pthread_mutex_unlock(&lock);
}
JNIEXPORT void JNICALL Java_com_undatech_opaque_NativeRdpTouch_nativeDetach(JNIEnv*, jclass, jlong);
JNIEXPORT jboolean JNICALL Java_com_undatech_opaque_NativeRdpTouch_nativeAttach(JNIEnv* env, jclass cls, jlong handle) {
    freerdp* instance = (freerdp*)(uintptr_t)handle;
    if (!instance || !instance->context || !instance->context->pubSub) return JNI_FALSE;
    if (!freerdp_settings_set_bool(instance->settings, FreeRDP_MultiTouchInput, TRUE) ||
        !freerdp_settings_set_bool(instance->settings, FreeRDP_SupportDynamicChannels, TRUE) ||
        !freerdp_settings_set_bool(instance->settings, FreeRDP_SupportDisplayControl, TRUE)) return JNI_FALSE;
    pthread_mutex_lock(&lock);
    if (find(instance)) { pthread_mutex_unlock(&lock); return JNI_TRUE; }
    TouchSession* s = calloc(1, sizeof(*s));
    if (!s) { pthread_mutex_unlock(&lock); return JNI_FALSE; }
    s->instance = instance; s->next = sessions; sessions = s;
    pthread_mutex_unlock(&lock);
    int first = PubSub_SubscribeChannelConnected(instance->context->pubSub, connected);
    int second = PubSub_SubscribeChannelDisconnected(instance->context->pubSub, disconnected);
    if (first != 0 || second != 0) {
        Java_com_undatech_opaque_NativeRdpTouch_nativeDetach(env, cls, handle);
        return JNI_FALSE;
    }
    return JNI_TRUE;
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
    if (s) {
        *link = s->next;
        clipboard_detach(s);
        if (s->display && s->display->DisplayControlCaps == display_caps)
            s->display->DisplayControlCaps = s->originalCaps;
    }
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

JNIEXPORT jboolean JNICALL Java_com_undatech_opaque_NativeRdpTouch_nativeDisplayReady(JNIEnv* env, jclass cls, jlong handle) {
    pthread_mutex_lock(&lock);
    TouchSession* s = find((freerdp*)(uintptr_t)handle);
    BOOL ready = s && s->display && s->maxMonitors > 0;
    pthread_mutex_unlock(&lock);
    return ready ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_com_undatech_opaque_NativeRdpTouch_nativeResize(JNIEnv* env, jclass cls, jlong handle, jint width, jint height, jint scale) {
    if (width < 200 || width > 8192 || height < 200 || height > 8192 || width % 2 || height % 2 || scale < 100 || scale > 500) return JNI_FALSE;
    pthread_mutex_lock(&lock);
    TouchSession* s = find((freerdp*)(uintptr_t)handle);
    UINT status = 1;
    if (s && s->display && s->display->SendMonitorLayout && s->maxMonitors > 0 &&
        (UINT64)width * height <= s->maxArea) {
        DISPLAY_CONTROL_MONITOR_LAYOUT monitor = {0};
        monitor.Flags = DISPLAY_CONTROL_MONITOR_PRIMARY;
        monitor.Width = (UINT32)width;
        monitor.Height = (UINT32)height;
        monitor.DesktopScaleFactor = (UINT32)scale;
        monitor.DeviceScaleFactor = 100;
        monitor.Orientation = ORIENTATION_LANDSCAPE;
        status = s->display->SendMonitorLayout(s->display, 1, &monitor);
    }
    pthread_mutex_unlock(&lock);
    return status == 0 ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jlong JNICALL Java_com_undatech_opaque_NativeRdpTouch_nativeError(JNIEnv* env, jclass cls, jlong handle) {
    freerdp* instance = (freerdp*)(uintptr_t)handle;
    if (!instance || !instance->context) return 0;
    return ((jlong)freerdp_get_last_error(instance->context) << 32) | freerdp_error_info(instance);
}
JNIEXPORT jboolean JNICALL Java_com_undatech_opaque_NativeRdpTouch_nativeOutput(JNIEnv* env, jclass cls, jlong handle, jboolean visible) {
    freerdp* instance = (freerdp*)(uintptr_t)handle;
    if (!instance || !instance->context || !instance->update || !instance->settings->SuppressOutput ||
        !instance->update->SuppressOutput) return JNI_FALSE;
    RECTANGLE_16 area = {0, 0, (UINT16)(instance->settings->DesktopWidth - 1), (UINT16)(instance->settings->DesktopHeight - 1)};
    if (!instance->update->SuppressOutput(instance->context, visible ? 1 : 0, visible ? &area : NULL)) return JNI_FALSE;
    if (visible && instance->update->RefreshRect) instance->update->RefreshRect(instance->context, 1, &area);
    return JNI_TRUE;
}
