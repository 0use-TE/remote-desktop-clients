/* Included by ouse_rdpei.c. UTF-8 paths and atomic, non-replacing directory publication. */
#include <errno.h>
#include <fcntl.h>
#include <sys/syscall.h>
#include <unistd.h>

static int publish_path(const char* source, const char* target) {
#ifdef __NR_renameat2
    if (syscall(__NR_renameat2, AT_FDCWD, source, AT_FDCWD, target, 1 /* RENAME_NOREPLACE */) == 0) return 0;
    if (errno == EEXIST || errno == ENOTEMPTY) return 1;
    if (errno == ENOSYS || errno == EINVAL || errno == EOPNOTSUPP) return 2;
    return 3;
#else
    return 2;
#endif
}
static char* copy_utf8_path(JNIEnv* env, jbyteArray bytes) {
    if (!bytes) return NULL;
    jsize size = (*env)->GetArrayLength(env, bytes);
    if (size < 1 || size > 4095) return NULL;
    char* path = calloc(1, (size_t)size + 1);
    if (!path) return NULL;
    (*env)->GetByteArrayRegion(env, bytes, 0, size, (jbyte*)path);
    if ((*env)->ExceptionCheck(env) || memchr(path, 0, size)) { free(path); return NULL; }
    return path;
}
static BOOL configure_shared(freerdp* instance, const char* path) {
    if (!instance || !instance->settings || !path || path[0] != '/') return FALSE;
    RDPDR_DRIVE* existing = (RDPDR_DRIVE*)freerdp_device_collection_find(instance->settings, "OuseRDP");
    if (existing) return existing->Type == RDPDR_DTYP_FILESYSTEM && existing->Path && strcmp(existing->Path, path) == 0;
    RDPDR_DRIVE* drive = calloc(1, sizeof(*drive));
    if (!drive) return FALSE;
    drive->Type = RDPDR_DTYP_FILESYSTEM; drive->Name = strdup("OuseRDP"); drive->Path = strdup(path);
    if (!drive->Name || !drive->Path ||
        !freerdp_settings_set_bool(instance->settings, FreeRDP_DeviceRedirection, TRUE) ||
        !freerdp_device_collection_add(instance->settings, (RDPDR_DEVICE*)drive)) {
        free(drive->Name); free(drive->Path); free(drive); return FALSE;
    }
    return TRUE;
}
JNIEXPORT jboolean JNICALL Java_com_undatech_opaque_NativeSharedFolder_nativeConfigure(JNIEnv* env, jclass cls, jlong handle, jbyteArray path) {
    char* directory = copy_utf8_path(env, path);
    BOOL result = directory && configure_shared((freerdp*)(uintptr_t)handle, directory);
    free(directory); return result ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jint JNICALL Java_com_undatech_opaque_NativeSharedFolder_nativePublish(JNIEnv* env, jclass cls, jbyteArray source, jbyteArray target) {
    char* from = copy_utf8_path(env, source); char* to = copy_utf8_path(env, target);
    int result = from && to ? publish_path(from, to) : 3;
    free(from); free(to);
    return result;
}
