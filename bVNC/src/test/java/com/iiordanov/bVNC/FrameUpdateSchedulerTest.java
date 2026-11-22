package com.iiordanov.bVNC;

import android.graphics.Rect;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class FrameUpdateSchedulerTest {
    private static class Display implements FrameUpdateScheduler.Target {
        final List<Runnable> callbacks = new ArrayList<>();
        final List<Rect> draws = new ArrayList<>();
        public void postFrame(Runnable callback) { callbacks.add(callback); }
        public void invalidate(Rect bounds) { draws.add(bounds); }
        void frame() { callbacks.remove(0).run(); }
    }

    @Test public void burstIncludesEveryRegionInOneDisplayFrame() {
        Display display = new Display();
        FrameUpdateScheduler scheduler = new FrameUpdateScheduler(display);
        scheduler.request(new Rect(10, 20, 30, 40));
        scheduler.request(new Rect(80, 90, 110, 120));
        scheduler.request(new Rect(-5, -10, 2, 5));
        assertEquals(1, display.callbacks.size());
        display.frame();
        assertEquals(List.of(new Rect(-5, -10, 110, 120)), display.draws);
    }

    @Test public void finalUpdateIsScheduledOnTheNextFrame() {
        Display display = new Display();
        FrameUpdateScheduler scheduler = new FrameUpdateScheduler(display);
        scheduler.request(new Rect(1, 2, 3, 4)); display.frame();
        scheduler.request(new Rect(50, 60, 70, 80));
        assertEquals(1, display.callbacks.size());
        display.frame();
        assertEquals(new Rect(50, 60, 70, 80), display.draws.get(1));
    }

    @Test public void updatesDuringDrawingAreKeptForFollowingFrame() {
        final FrameUpdateScheduler[] scheduler = new FrameUpdateScheduler[1];
        Display display = new Display() {
            @Override public void invalidate(Rect bounds) {
                super.invalidate(bounds);
                if (draws.size() == 1) scheduler[0].request(new Rect(5, 5, 10, 10));
            }
        };
        scheduler[0] = new FrameUpdateScheduler(display);
        scheduler[0].request(new Rect(1, 1, 2, 2));
        display.frame(); display.frame();
        assertEquals(2, display.draws.size());
    }

    @Test public void clearingDiscardsStaleCallbackWithoutDroppingNewUpdates() {
        Display display = new Display();
        FrameUpdateScheduler scheduler = new FrameUpdateScheduler(display);
        scheduler.request(new Rect(1, 1, 2, 2)); scheduler.clear();
        scheduler.request(new Rect(8, 8, 12, 12));
        display.frame(); assertTrue(display.draws.isEmpty());
        display.frame(); assertEquals(List.of(new Rect(8, 8, 12, 12)), display.draws);
    }

    @Test public void fractionalZoomRoundsDamageOutwards() {
        assertEquals(new Rect(-1, -1, 2, 2),
                FrameUpdateScheduler.viewBounds(0, 0, 1, 1, 0, 0, 0.75f));
        assertEquals(new Rect(5, 5, 8, 8),
                FrameUpdateScheduler.viewBounds(10, 10, 1, 1, 2, 2, 0.75f));
    }

    @Test public void emptyAndNonFiniteRegionsNeverScheduleDrawing() {
        Display display = new Display();
        FrameUpdateScheduler scheduler = new FrameUpdateScheduler(display);
        scheduler.request(new Rect());
        scheduler.request(FrameUpdateScheduler.viewBounds(0, 0, -1, 1, 0, 0, 1));
        scheduler.request(FrameUpdateScheduler.viewBounds(Float.NaN, 0, 1, 1, 0, 0, 1));
        assertTrue(display.callbacks.isEmpty());
    }
}
