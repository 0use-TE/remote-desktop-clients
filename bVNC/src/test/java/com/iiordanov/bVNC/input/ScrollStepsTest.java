package com.iiordanov.bVNC.input;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScrollStepsTest {
    @Test
    public void smallMovesAccumulateAndBurstDoesNotCarryOver() {
        ScrollSteps steps = new ScrollSteps();
        assertEquals(0, steps.take(.25f));
        assertEquals(0, steps.take(.25f));
        assertEquals(1, steps.take(.5f));
        assertEquals(2, steps.take(14.5f));
        assertEquals(0, steps.take(.25f));
        assertEquals(1, steps.take(.25f));
    }

    @Test
    public void reversalAndNewGestureDiscardOldMovement() {
        ScrollSteps steps = new ScrollSteps();
        assertEquals(0, steps.take(.75f));
        assertEquals(-1, steps.take(-1f));
        assertEquals(0, steps.take(.75f));
        steps.reset();
        assertEquals(0, steps.take(.5f));
    }
}
