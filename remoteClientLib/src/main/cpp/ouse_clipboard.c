/* Included by ouse_rdpei.c. Own only image offers; retain FreeRDP's text callbacks. */
#define OUSE_CF_DIB 8
#define OUSE_CF_DIBV5 17
#define OUSE_CF_PNG 0xC001
#define OUSE_IMAGE_MAX_BYTES (64U * 1024U * 1024U + 124U)

static UINT image_request(CliprdrClientContext*, const CLIPRDR_FORMAT_DATA_REQUEST*);
static UINT image_server_list(CliprdrClientContext*, const CLIPRDR_FORMAT_LIST*);
static void image_clear(TouchSession* s) {
    free(s->imagePng); free(s->imageDib);
    s->imagePng = s->imageDib = NULL;
    s->pngSize = s->dibSize = 0;
}
static void clipboard_detach(TouchSession* s) {
    image_clear(s);
    if (s->clipboard) {
        if (s->clipboard->ServerFormatDataRequest == image_request)
            s->clipboard->ServerFormatDataRequest = s->originalRequest;
        if (s->clipboard->ServerFormatList == image_server_list)
            s->clipboard->ServerFormatList = s->originalList;
    }
    s->clipboard = NULL; s->originalRequest = NULL; s->originalList = NULL;
}
static TouchSession* clipboard_find(CliprdrClientContext* channel) {
    for (TouchSession* s = sessions; s; s = s->next) if (s->clipboard == channel) return s;
    return NULL;
}
static UINT image_server_list(CliprdrClientContext* channel, const CLIPRDR_FORMAT_LIST* list) {
    pthread_mutex_lock(&lock);
    TouchSession* s = clipboard_find(channel);
    pcCliprdrServerFormatList previous = s ? s->originalList : NULL;
    if (s) image_clear(s); /* A remote copy takes ownership, including remote text. */
    pthread_mutex_unlock(&lock);
    return previous ? previous(channel, list) : 0;
}
static UINT32 image_u32(const BYTE* p) {
    return (UINT32)p[0] | ((UINT32)p[1] << 8) | ((UINT32)p[2] << 16) | ((UINT32)p[3] << 24);
}
static void image_put32(BYTE* p, UINT32 value) {
    for (int i = 0; i < 4; i++) p[i] = (BYTE)(value >> (8 * i));
}
/* CF_DIB compatibility: 24-bit, bottom-up, padded rows, alpha composited on white. */
static BYTE* image_legacy_dib(const BYTE* v5, UINT32 size, UINT32* length) {
    if (size < 124 || image_u32(v5) != 124) return NULL;
    UINT32 width = image_u32(v5 + 4), height = 0U - image_u32(v5 + 8);
    if (!width || !height || (UINT64)width * height * 4 + 124 != size) return NULL;
    UINT32 stride = (width * 3U + 3U) & ~3U;
    *length = 40U + stride * height;
    BYTE* result = calloc(1, *length);
    if (!result) return NULL;
    image_put32(result, 40); image_put32(result + 4, width); image_put32(result + 8, height);
    result[12] = 1; result[14] = 24; image_put32(result + 20, stride * height);
    for (UINT32 y = 0; y < height; y++) {
        const BYTE* src = v5 + 124 + (size_t)y * width * 4;
        BYTE* dst = result + 40 + (size_t)(height - 1 - y) * stride;
        for (UINT32 x = 0; x < width; x++) {
            UINT32 alpha = src[x * 4 + 3];
            for (int c = 0; c < 3; c++) dst[x * 3 + c] =
                (BYTE)((src[x * 4 + c] * alpha + 255U * (255U - alpha) + 127U) / 255U);
        }
    }
    return result;
}
static UINT image_request(CliprdrClientContext* channel, const CLIPRDR_FORMAT_DATA_REQUEST* request) {
    if (!channel || !request) return 0;
    pthread_mutex_lock(&lock);
    TouchSession* s = clipboard_find(channel);
    if (!s || !s->imageDib) {
        pcCliprdrServerFormatDataRequest previous = s ? s->originalRequest : NULL;
        pthread_mutex_unlock(&lock);
        return previous ? previous(channel, request) : 0;
    }
    CLIPRDR_FORMAT_DATA_RESPONSE response = {0};
    response.msgType = CB_FORMAT_DATA_RESPONSE;
    response.msgFlags = CB_RESPONSE_FAIL;
    BYTE* temporary = NULL;
    if (request->requestedFormatId == OUSE_CF_PNG) {
        response.requestedFormatData = s->imagePng; response.dataLen = s->pngSize;
    } else if (request->requestedFormatId == OUSE_CF_DIBV5) {
        response.requestedFormatData = s->imageDib; response.dataLen = s->dibSize;
    } else if (request->requestedFormatId == OUSE_CF_DIB) {
        temporary = image_legacy_dib(s->imageDib, s->dibSize, &response.dataLen);
        response.requestedFormatData = temporary;
    }
    if (response.requestedFormatData) response.msgFlags = CB_RESPONSE_OK;
    else response.dataLen = 0;
    UINT result = channel->ClientFormatDataResponse ? channel->ClientFormatDataResponse(channel, &response) : 0;
    free(temporary);
    pthread_mutex_unlock(&lock);
    return result;
}
/* Install after Android's channel initializer has supplied the text handlers. */
static BOOL image_offer(TouchSession* s, BYTE* png, UINT32 pngSize, BYTE* dib, UINT32 dibSize) {
    if (!s || !s->clipboard || !s->clipboard->ClientFormatList ||
        !s->clipboard->ClientFormatDataResponse || !s->clipboard->ServerFormatDataRequest) return FALSE;
    CliprdrClientContext* channel = s->clipboard;
    if (channel->ServerFormatDataRequest != image_request) {
        s->originalRequest = channel->ServerFormatDataRequest;
        channel->ServerFormatDataRequest = image_request;
    }
    if (channel->ServerFormatList != image_server_list) {
        s->originalList = channel->ServerFormatList;
        channel->ServerFormatList = image_server_list;
    }
    CLIPRDR_FORMAT formats[] = {{OUSE_CF_PNG, "PNG"}, {OUSE_CF_DIBV5, NULL}, {OUSE_CF_DIB, NULL}};
    CLIPRDR_FORMAT_LIST list = {0};
    list.msgType = CB_FORMAT_LIST; list.numFormats = 3; list.formats = formats;
    /* ClientFormatList serializes synchronously; callbacks run on the network thread. */
    if (channel->ClientFormatList(channel, &list) != 0) return FALSE;
    image_clear(s);
    s->imagePng = png; s->pngSize = pngSize; s->imageDib = dib; s->dibSize = dibSize;
    return TRUE;
}
JNIEXPORT jboolean JNICALL Java_com_undatech_opaque_NativeRdpClipboard_nativeReady(JNIEnv* env, jclass cls, jlong handle) {
    pthread_mutex_lock(&lock);
    TouchSession* s = find((freerdp*)(uintptr_t)handle);
    BOOL ready = s && s->clipboard && s->clipboard->ClientFormatList && s->clipboard->ServerFormatDataRequest;
    pthread_mutex_unlock(&lock);
    return ready ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL Java_com_undatech_opaque_NativeRdpClipboard_nativeClear(JNIEnv* env, jclass cls, jlong handle) {
    pthread_mutex_lock(&lock);
    TouchSession* s = find((freerdp*)(uintptr_t)handle);
    if (s) image_clear(s);
    pthread_mutex_unlock(&lock);
}
JNIEXPORT jboolean JNICALL Java_com_undatech_opaque_NativeRdpClipboard_nativeSend(JNIEnv* env, jclass cls, jlong handle, jbyteArray png, jbyteArray dib) {
    if (!png || !dib) return JNI_FALSE;
    jsize pngSize = (*env)->GetArrayLength(env, png), dibSize = (*env)->GetArrayLength(env, dib);
    if (pngSize < 8 || (UINT32)pngSize > OUSE_IMAGE_MAX_BYTES || dibSize < 128 || (UINT32)dibSize > OUSE_IMAGE_MAX_BYTES) return JNI_FALSE;
    BYTE* pngCopy = malloc(pngSize); BYTE* dibCopy = malloc(dibSize);
    if (!pngCopy || !dibCopy) { free(pngCopy); free(dibCopy); return JNI_FALSE; }
    (*env)->GetByteArrayRegion(env, png, 0, pngSize, (jbyte*)pngCopy);
    (*env)->GetByteArrayRegion(env, dib, 0, dibSize, (jbyte*)dibCopy);
    if ((*env)->ExceptionCheck(env)) { free(pngCopy); free(dibCopy); return JNI_FALSE; }
    UINT32 width = image_u32(dibCopy + 4), height = 0U - image_u32(dibCopy + 8);
    if (image_u32(dibCopy) != 124 || !width || !height || width > 8192 || height > 8192 ||
        (UINT64)width * height * 4 + 124 != (UINT32)dibSize) { free(pngCopy); free(dibCopy); return JNI_FALSE; }
    pthread_mutex_lock(&lock);
    BOOL result = image_offer(find((freerdp*)(uintptr_t)handle), pngCopy, pngSize, dibCopy, dibSize);
    pthread_mutex_unlock(&lock);
    if (!result) { free(pngCopy); free(dibCopy); }
    return result ? JNI_TRUE : JNI_FALSE;
}
