// Copyright (c) 2026 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package tests.junittests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.cef.misc.EventFlags;
import org.junit.jupiter.api.Test;

// EventFlags is a collection of plain int bit-flag constants -- no CEF native
// lifecycle involved.
class EventFlagsTest {
    @Test
    void noneIsZero() {
        assertEquals(0, EventFlags.EVENTFLAG_NONE);
    }

    @Test
    void flagsAreDistinctSingleBits() {
        int[] flags = {EventFlags.EVENTFLAG_CAPS_LOCK_ON, EventFlags.EVENTFLAG_SHIFT_DOWN,
                EventFlags.EVENTFLAG_CONTROL_DOWN, EventFlags.EVENTFLAG_ALT_DOWN,
                EventFlags.EVENTFLAG_LEFT_MOUSE_BUTTON, EventFlags.EVENTFLAG_MIDDLE_MOUSE_BUTTON,
                EventFlags.EVENTFLAG_RIGHT_MOUSE_BUTTON, EventFlags.EVENTFLAG_COMMAND_DOWN,
                EventFlags.EVENTFLAG_NUM_LOCK_ON, EventFlags.EVENTFLAG_IS_KEY_PAD,
                EventFlags.EVENTFLAG_IS_LEFT, EventFlags.EVENTFLAG_IS_RIGHT};

        int union = 0;
        for (int flag : flags) {
            // Each flag must be a single bit that hasn't already been used --
            // otherwise combining flags with bitwise-OR would be lossy.
            assertEquals(1, Integer.bitCount(flag));
            assertEquals(0, union & flag);
            union |= flag;
        }
    }

    @Test
    void flagsCombineWithBitwiseOr() {
        int combined = EventFlags.EVENTFLAG_SHIFT_DOWN | EventFlags.EVENTFLAG_CONTROL_DOWN;
        assertEquals(true, (combined & EventFlags.EVENTFLAG_SHIFT_DOWN) != 0);
        assertEquals(true, (combined & EventFlags.EVENTFLAG_CONTROL_DOWN) != 0);
        assertEquals(false, (combined & EventFlags.EVENTFLAG_ALT_DOWN) != 0);
    }
}
