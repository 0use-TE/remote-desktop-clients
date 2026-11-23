/* Standalone Android smoke test. Does not connect to a server or modify an installed app. */
#include <stdio.h>
#include <sys/stat.h>
#include <freerdp/client/cmdline.h>
#include "ouse_rdpei.c"

#define CHECK(expr) do { if (!(expr)) { fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #expr); return 1; } } while (0)
static int layouts, caps, hidden, shown, refreshed;
static DISPLAY_CONTROL_MONITOR_LAYOUT sent;
static UINT prior_caps(DispClientContext* context, UINT32 n, UINT32 a, UINT32 b) { caps++; return 0; }
static UINT send_layout(DispClientContext* context, UINT32 n, DISPLAY_CONTROL_MONITOR_LAYOUT* monitors) {
    if (n != 1) return 1;
    layouts++; sent = monitors[0]; return 0;
}
static BOOL suppress(rdpContext* context, BYTE allow, const RECTANGLE_16* area) {
    if (allow) { if (!area || area->right != 1999 || area->bottom != 1199) return FALSE; shown++; }
    else { if (area) return FALSE; hidden++; }
    return TRUE;
}
static BOOL refresh(rdpContext* context, BYTE count, const RECTANGLE_16* area) { refreshed++; return count == 1 && area != NULL; }
static int imageLists, textRequests, serverLists, rejectList;
static UINT16 imageFlags;
static UINT32 imageLength;
static BYTE imageData[160];
static UINT previous_request(CliprdrClientContext* context, const CLIPRDR_FORMAT_DATA_REQUEST* request) { textRequests++; return 0; }
static UINT previous_list(CliprdrClientContext* context, const CLIPRDR_FORMAT_LIST* list) { serverLists++; return 0; }
static UINT send_image_list(CliprdrClientContext* context, const CLIPRDR_FORMAT_LIST* list) {
    if (list->numFormats != 3 || list->formats[0].formatId != OUSE_CF_PNG ||
        strcmp(list->formats[0].formatName, "PNG") != 0 || list->formats[1].formatId != OUSE_CF_DIBV5 ||
        list->formats[2].formatId != OUSE_CF_DIB) return 1;
    imageLists++; return rejectList ? 1 : 0;
}
static UINT send_image_data(CliprdrClientContext* context, const CLIPRDR_FORMAT_DATA_RESPONSE* response) {
    imageFlags = response->msgFlags; imageLength = response->dataLen;
    if (imageLength > sizeof(imageData) || (imageLength && !response->requestedFormatData)) return 1;
    if (imageLength) memcpy(imageData, response->requestedFormatData, imageLength);
    return 0;
}
static BYTE* sample_dib(void) {
    BYTE* dib = calloc(1, 148);
    if (!dib) return NULL;
    image_put32(dib, 124); image_put32(dib + 4, 3); image_put32(dib + 8, (UINT32)-2);
    image_put32(dib + 124, 0xffff0000); // Top red, green, blue.
    image_put32(dib + 128, 0xff00ff00); image_put32(dib + 132, 0xff0000ff);
    image_put32(dib + 136, 0x800000ff); // Bottom: half-alpha blue, transparent, white.
    image_put32(dib + 140, 0); image_put32(dib + 144, 0xffffffff);
    return dib;
}

int main(int argc, char** argv) {
    CHECK(!Java_com_undatech_opaque_NativeRdpTouch_nativeAttach(NULL, NULL, 0));
    CHECK(!Java_com_undatech_opaque_NativeRdpTouch_nativeOutput(NULL, NULL, 0, JNI_TRUE));
    freerdp* instance = freerdp_new(); CHECK(instance);
    instance->ContextSize = sizeof(rdpContext);
    CHECK(freerdp_context_new(instance));
    jlong handle = (jlong)(uintptr_t)instance;
    CHECK(Java_com_undatech_opaque_NativeRdpTouch_nativeAttach(NULL, NULL, handle));
    CHECK(Java_com_undatech_opaque_NativeRdpTouch_nativeAttach(NULL, NULL, handle));
    CHECK(sessions && !sessions->next);
    CHECK(instance->settings->SupportDisplayControl);
    char* driveArgs[] = {"ouse-test", "/v:127.0.0.1"};
    CHECK(freerdp_client_settings_parse_command_line_arguments(instance->settings, 2, driveArgs, FALSE) == 0);
    CHECK(configure_shared(instance, "/sdcard/Android/data/io.github.ouse.ouserdp/files/OuseRDP-Shared"));
    CHECK(configure_shared(instance, "/sdcard/Android/data/io.github.ouse.ouserdp/files/OuseRDP-Shared"));
    CHECK(instance->settings->DeviceRedirection && !instance->settings->RedirectDrives);
    CHECK(instance->settings->DeviceCount == 1);
    RDPDR_DRIVE* drive = (RDPDR_DRIVE*)freerdp_device_collection_find(instance->settings, "OuseRDP");
    CHECK(drive && drive->Type == RDPDR_DTYP_FILESYSTEM);
    CHECK(strcmp(drive->Path, "/sdcard/Android/data/io.github.ouse.ouserdp/files/OuseRDP-Shared") == 0);
    CHECK(!Java_com_undatech_opaque_NativeRdpTouch_nativeDisplayReady(NULL, NULL, handle));
    DispClientContext display = {0};
    display.DisplayControlCaps = prior_caps;
    display.SendMonitorLayout = send_layout;
    ChannelConnectedEventArgs event = {0};
    event.name = DISP_DVC_CHANNEL_NAME; event.pInterface = &display;
    connected(instance->context, &event);
    CHECK(display.DisplayControlCaps == display_caps);
    connected(instance->context, &event); // Duplicate channel notifications must remain idempotent.
    CHECK(display.DisplayControlCaps(&display, 1, 8192, 8192) == 0 && caps == 1);
    CHECK(Java_com_undatech_opaque_NativeRdpTouch_nativeDisplayReady(NULL, NULL, handle));
    CHECK(Java_com_undatech_opaque_NativeRdpTouch_nativeResize(NULL, NULL, handle, 2000, 1200, 150));
    CHECK(layouts == 1 && sent.Width == 2000 && sent.Height == 1200 && sent.DesktopScaleFactor == 150 && sent.DeviceScaleFactor == 100);
    CHECK(!Java_com_undatech_opaque_NativeRdpTouch_nativeResize(NULL, NULL, handle, 2001, 1200, 150));
    CHECK(!Java_com_undatech_opaque_NativeRdpTouch_nativeResize(NULL, NULL, handle, 2000, 1200, 50));
    display.DisplayControlCaps(&display, 1, 100, 100);
    CHECK(!Java_com_undatech_opaque_NativeRdpTouch_nativeResize(NULL, NULL, handle, 2000, 1200, 150));
    CHECK(layouts == 1);
    instance->settings->SuppressOutput = TRUE;
    instance->settings->DesktopWidth = 2000; instance->settings->DesktopHeight = 1200;
    instance->update->SuppressOutput = suppress; instance->update->RefreshRect = refresh;
    CHECK(Java_com_undatech_opaque_NativeRdpTouch_nativeOutput(NULL, NULL, handle, JNI_FALSE));
    CHECK(Java_com_undatech_opaque_NativeRdpTouch_nativeOutput(NULL, NULL, handle, JNI_TRUE));
    CHECK(hidden == 1 && shown == 1 && refreshed == 1);
    CliprdrClientContext clipboard = {0};
    clipboard.ServerFormatDataRequest = previous_request; clipboard.ServerFormatList = previous_list;
    clipboard.ClientFormatList = send_image_list; clipboard.ClientFormatDataResponse = send_image_data;
    event.name = CLIPRDR_SVC_CHANNEL_NAME; event.pInterface = &clipboard;
    connected(instance->context, &event);
    CHECK(Java_com_undatech_opaque_NativeRdpClipboard_nativeReady(NULL, NULL, handle));
    CHECK(clipboard.ServerFormatDataRequest == previous_request); // Hook lazily after Android initialization.
    BYTE* png = calloc(1, 8); BYTE* dib = sample_dib(); CHECK(png && dib);
    CHECK(image_offer(sessions, png, 8, dib, 148)); CHECK(imageLists == 1);
    connected(instance->context, &event); // Duplicate notifications retain the image.
    CHECK(sessions->imageDib == dib);
    CLIPRDR_FORMAT_DATA_REQUEST request = {0}; request.requestedFormatId = OUSE_CF_DIBV5;
    CHECK(clipboard.ServerFormatDataRequest(&clipboard, &request) == 0);
    CHECK(imageFlags == CB_RESPONSE_OK && imageLength == 148 && image_u32(imageData + 136) == 0x800000ff);
    request.requestedFormatId = OUSE_CF_DIB;
    CHECK(clipboard.ServerFormatDataRequest(&clipboard, &request) == 0);
    CHECK(imageFlags == CB_RESPONSE_OK && imageLength == 64 && image_u32(imageData) == 40);
    CHECK(imageData[40] == 255 && imageData[41] == 127 && imageData[42] == 127); // Half-blue on white.
    CHECK(imageData[43] == 255 && imageData[44] == 255 && imageData[45] == 255); // Transparent on white.
    CHECK(imageData[49] == 0 && imageData[50] == 0 && imageData[51] == 0); // Padded row.
    CHECK(imageData[52] == 0 && imageData[53] == 0 && imageData[54] == 255); // Top red stored last.
    request.requestedFormatId = OUSE_CF_PNG;
    CHECK(clipboard.ServerFormatDataRequest(&clipboard, &request) == 0 && imageLength == 8);
    request.requestedFormatId = 999;
    CHECK(clipboard.ServerFormatDataRequest(&clipboard, &request) == 0);
    CHECK(imageFlags == CB_RESPONSE_FAIL && imageLength == 0 && textRequests == 0);
    rejectList = 1;
    BYTE* nextPng = calloc(1, 8); BYTE* nextDib = sample_dib(); CHECK(nextPng && nextDib);
    CHECK(!image_offer(sessions, nextPng, 8, nextDib, 148)); free(nextPng); free(nextDib);
    CHECK(sessions->imageDib == dib); // A failed announcement retains the previous offer.
    rejectList = 0;
    CLIPRDR_FORMAT_LIST remoteList = {0};
    CHECK(clipboard.ServerFormatList(&clipboard, &remoteList) == 0 && serverLists == 1);
    CHECK(!sessions->imageDib && !sessions->imagePng);
    request.requestedFormatId = 13;
    CHECK(clipboard.ServerFormatDataRequest(&clipboard, &request) == 0 && textRequests == 1);
    png = calloc(1, 8); dib = sample_dib(); CHECK(png && dib);
    CHECK(image_offer(sessions, png, 8, dib, 148));
    Java_com_undatech_opaque_NativeRdpClipboard_nativeClear(NULL, NULL, handle);
    CHECK(!sessions->imageDib && !sessions->imagePng);
    freerdp_set_last_error(instance->context, 0x20009);
    CHECK((Java_com_undatech_opaque_NativeRdpTouch_nativeError(NULL, NULL, handle) >> 32) == 0x20009);
    Java_com_undatech_opaque_NativeRdpTouch_nativeDetach(NULL, NULL, handle);
    CHECK(!sessions && display.DisplayControlCaps == prior_caps);
    CHECK(clipboard.ServerFormatDataRequest == previous_request && clipboard.ServerFormatList == previous_list);
    for (int i = 0; i < 100; i++) {
        CHECK(Java_com_undatech_opaque_NativeRdpTouch_nativeAttach(NULL, NULL, handle));
        Java_com_undatech_opaque_NativeRdpTouch_nativeDetach(NULL, NULL, handle);
        CHECK(!sessions);
    }
    freerdp_context_free(instance); freerdp_free(instance);
    char publishRoot[512];
    CHECK(snprintf(publishRoot, sizeof(publishRoot), "%s/ouserdp-publish-XXXXXX", argc > 1 ? argv[1] : "/data/local/tmp") > 0);
    CHECK(mkdtemp(publishRoot));
    char from[1024], existing[1024], target[1024], folder[1024], moved[1024];
    snprintf(from, sizeof(from), "%s/from", publishRoot); snprintf(existing, sizeof(existing), "%s/existing", publishRoot);
    snprintf(target, sizeof(target), "%s/相片🌟", publishRoot); snprintf(folder, sizeof(folder), "%s/folder", publishRoot);
    snprintf(moved, sizeof(moved), "%s/文件夹🌟", publishRoot);
    FILE* source = fopen(from, "wb"); CHECK(source); CHECK(fwrite("NEW", 1, 3, source) == 3); CHECK(fclose(source) == 0);
    FILE* old = fopen(existing, "wb"); CHECK(old); CHECK(fwrite("OLD", 1, 3, old) == 3); CHECK(fclose(old) == 0);
    int collision = publish_path(from, existing);
    CHECK(collision == 1 || collision == 2);
    char preserved[4] = {0}; old = fopen(existing, "rb"); CHECK(old); CHECK(fread(preserved, 1, 3, old) == 3); fclose(old);
    CHECK(strcmp(preserved, "OLD") == 0 && access(from, F_OK) == 0);
    int published = publish_path(from, target);
    CHECK(published == 0 || published == 2);
    if (published == 0) CHECK(access(from, F_OK) != 0 && access(target, F_OK) == 0);
    CHECK(mkdir(folder, 0700) == 0);
    int directoryPublished = publish_path(folder, moved);
    CHECK(directoryPublished == 0 || directoryPublished == 2);
    if (directoryPublished == 0) CHECK(access(folder, F_OK) != 0 && access(moved, F_OK) == 0);
    unlink(from); unlink(existing); unlink(target); rmdir(folder); rmdir(moved); CHECK(rmdir(publishRoot) == 0);
    printf("PASS: RDPDR single OuseRDP drive parsed; Unicode no-replace publication file=%d directory=%d (0=atomic, 2=streaming fallback required)\n", published, directoryPublished);
    puts("PASS: display/output; PNG, DIBV5 alpha, DIB padding/orientation/compositing; image ownership, text fallback, failed offers, callback restoration; 100 attach/detach cycles");
    return 0;
}
