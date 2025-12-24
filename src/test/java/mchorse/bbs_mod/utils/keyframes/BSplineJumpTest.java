package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BSplineJumpTest
{
    @Test
    public void testContinuity()
    {
        KeyframeChannel<Double> channel = new KeyframeChannel<>("test", KeyframeFactories.DOUBLE);
        channel.insert(0, 0.0);
        channel.insert(10, 10.0);
        channel.insert(20, 10.0);
        channel.insert(30, 0.0);

        for (Keyframe<Double> kf : channel.getKeyframes())
        {
            kf.getInterpolation().setInterp(Interpolations.BSPLINE);
        }

        // Check around tick 10
        // Segment 1 (0-10) ends at 10.
        // Segment 2 (10-20) starts at 10.
        
        // We manually force segment creation to verify continuity at the exact boundary
        // channel.interpolate(10) uses findSegment(10).
        
        double at10 = channel.interpolate(10.0f);
        double justAfter10 = channel.interpolate(10.0001f);
        
        System.out.println("At 10.0: " + at10);
        System.out.println("At 10.0001: " + justAfter10);
        
        assertEquals(at10, justAfter10, 0.01, "Jump detected at knot 10");
        
        // Check around tick 20
        double at20 = channel.interpolate(20.0f);
        double justAfter20 = channel.interpolate(20.0001f);
        
        System.out.println("At 20.0: " + at20);
        System.out.println("At 20.0001: " + justAfter20);
        
        assertEquals(at20, justAfter20, 0.01, "Jump detected at knot 20");
    }
}
