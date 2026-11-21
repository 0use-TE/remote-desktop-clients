package com.iiordanov.bVNC.input;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScrollStepsTest {
    @Test
    public void smallMovesAccumulateAndBurstDoesNotCarryOver() {
        ScrollSteps steps = new ScrollSteps();
        assertEquals(30, steps.take(.25f));
        assertEquals(30, steps.take(.25f));
        assertEquals(60, steps.take(.5f));
        assertEquals(240, steps.take(14.5f));
        assertEquals(30, steps.take(.25f));
    }

    @Test
    public void reversalAndNewGestureDiscardOldMovement() {
        ScrollSteps steps = new ScrollSteps();
        assertEquals(0, steps.take(.004f));
        assertEquals(-120, steps.take(-1f));
        assertEquals(0, steps.take(.004f));
        steps.reset();
        assertEquals(0, steps.take(.004f));
    }

    @Test
    public void subUnitMovementIsPreservedAndInvalidInputIgnored() {
        ScrollSteps steps = new ScrollSteps();
        assertEquals(0, steps.take(.005f));
        assertEquals(0, steps.take(Float.NaN));
        assertEquals(0, steps.take(Float.POSITIVE_INFINITY));
        assertEquals(1, steps.take(.005f));
        assertEquals(120, steps.take(1f));
    }
}
