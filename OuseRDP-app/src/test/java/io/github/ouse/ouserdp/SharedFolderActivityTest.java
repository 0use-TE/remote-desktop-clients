package io.github.ouse.ouserdp;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import com.undatech.opaque.RdpSharedFolder;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 28, qualifiers = "zh-rCN-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SharedFolderActivityTest {
    @Test public void tabletLayoutOffersSelectionExportAndFolderNavigationWithLargeText() throws Exception { checkLayout(1600, 1000); }
    @Test public void phoneLayoutKeepsImportAndExportVisibleWithLargeText() throws Exception { checkLayout(480, 1100); }
    private void checkLayout(int width, int height) throws Exception {
        File root = RdpSharedFolder.directory(RuntimeEnvironment.getApplication());
        File folder = new File(root, "项目文件"); assertTrue(folder.mkdir());
        File video = new File(root, "平板录像.mp4"); assertTrue(video.createNewFile());
        var controller = Robolectric.buildActivity(SharedFolderActivity.class).setup();
        try {
            SharedFolderActivity activity = controller.get(); View content = activity.findViewById(android.R.id.content);
            settle(content, width, height);
            assertNotNull(findText(content, "添加文件")); assertNotNull(findText(content, "添加文件夹"));
            TextView save = findText(content, "保存到平板"); assertNotNull(save); assertFalse(save.isEnabled());
            assertLargeText(content);
            TextView filename = findText(content, "平板录像.mp4"); assertNotNull(filename);
            ViewGroup item = (ViewGroup) filename.getParent().getParent(); ((CheckBox) item.getChildAt(0)).setChecked(true);
            assertTrue(save.isEnabled());
            String artifacts = System.getenv("OUSE_UI_ARTIFACTS");
            if (artifacts != null) {
                Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                content.draw(new Canvas(image));
                try (FileOutputStream output = new FileOutputStream(new File(artifacts, "ouserdp-shared-" + width + ".png"))) {
                    assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
                } finally { image.recycle(); }
            }
            TextView name = findText(content, "项目文件"); assertNotNull(name);
            ((View) name.getParent().getParent()).performClick(); settle(content, width, height);
            assertNotNull(findText(content, "返回上一级"));
            activity.getOnBackPressedDispatcher().onBackPressed(); settle(content, width, height);
            assertNotNull(findText(content, "平板录像.mp4"));
        } finally { controller.pause().stop().destroy(); assertTrue(video.delete()); assertTrue(folder.delete()); }
    }
    private void settle(View view, int width, int height) throws InterruptedException {
        for (int i = 0; i < 15; i++) {
            shadowOf(android.os.Looper.getMainLooper()).idle();
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, width, height); Thread.sleep(15);
        }
    }
    private TextView findText(View view, String label) {
        if (view instanceof TextView text && label.contentEquals(text.getText())) return text;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            TextView result = findText(group.getChildAt(i), label); if (result != null) return result;
        }
        return null;
    }
    private void assertLargeText(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView text && text.length() > 0) assertTrue(text.getText().toString(), text.getTextSize() >= 20);
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) assertLargeText(group.getChildAt(i));
    }
}
