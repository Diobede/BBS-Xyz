package mchorse.bbs_mod.utils.interps;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SplineTest
{
    @Test
    public void testBSplineValues()
    {
        // Test B-Spline with known behavior
        // Uniform cubic B-spline basis functions sum to 1
        double y0 = 0.0;
        double y1 = 1.0;
        double y2 = 1.0;
        double y3 = 0.0;
        
        // At t=0.5, it should be symmetric if inputs are symmetric
        double val = Lerps.bSpline(y0, y1, y2, y3, 0.5);
        
        // For B-spline with 0, 1, 1, 0
        // It smooths out the "pulse"
        assertTrue(val > 0.5, "Center should be high");
        assertTrue(val < 1.0, "But not reach 1.0 (approximating)");
        
        // Test symmetry
        assertEquals(val, Lerps.bSpline(y3, y2, y1, y0, 0.5), 0.0001);
    }

    @Test
    public void testBSplineContinuity()
    {
        // Check smoothness (derivative continuity approximation)
        double y0 = 0, y1 = 10, y2 = 5, y3 = 20;
        double step = 0.01;
        
        double prev = Lerps.bSpline(y0, y1, y2, y3, 0);
        for (double t = step; t <= 1.0; t += step)
        {
            double curr = Lerps.bSpline(y0, y1, y2, y3, t);
            // Just ensuring no NaNs or Infinities and relatively small jumps
            assertFalse(Double.isNaN(curr));
            assertTrue(Math.abs(curr - prev) < 5.0); 
            prev = curr;
        }
    }

    @Test
    public void benchmarkInterpolations()
    {
        int iterations = 10_000_000;
        double y0 = 0, y1 = 10, y2 = 5, y3 = 20;
        
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++)
        {
            Lerps.cubicHermite(y0, y1, y2, y3, 0.5);
        }
        long hermiteTime = System.nanoTime() - start;
        
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++)
        {
            Lerps.bSpline(y0, y1, y2, y3, 0.5);
        }
        long bSplineTime = System.nanoTime() - start;
        
        System.out.println("Benchmark Results (10M iterations):");
        System.out.println("Hermite: " + (hermiteTime / 1_000_000.0) + " ms");
        System.out.println("B-Spline: " + (bSplineTime / 1_000_000.0) + " ms");
        
        // B-Spline might be slightly slower due to more ops or similar
        // Just ensure it's not orders of magnitude slower
        // Using a generous factor of 5x to account for JIT variances etc.
        assertTrue(bSplineTime < hermiteTime * 5, "B-Spline should be reasonably performant");
    }

    @Test
    public void testEndpointInterpolation()
    {
        // User reported "bugging", likely due to B-Spline not passing through control points
        double y0 = 0, y1 = 10, y2 = 20, y3 = 30;
        
        // At t=0, it should be y1 (10) for an interpolating spline
        double val0 = Lerps.bSpline(y0, y1, y2, y3, 0);
        
        // B-Spline formula at t=0 is (y0 + 4*y1 + y2)/6 = (0 + 40 + 20)/6 = 10
        // Wait, for this specific case it IS 10?
        // (0 + 40 + 20)/6 = 60/6 = 10.
        // Let's try different values.
        
        double z0 = 0, z1 = 0, z2 = 10, z3 = 10;
        // t=0: (0 + 0 + 10)/6 = 1.666... != 0 (z1)
        
        double valZ0 = Lerps.bSpline(z0, z1, z2, z3, 0);
        
        // B-Spline is an approximating spline, so it generally does NOT pass through control points
        // (Except for linear arrangements).
        // The value 1.666... confirms standard B-Spline behavior.
        assertEquals(10.0 / 6.0, valZ0, 0.001, "B-Spline should value at t=0 is (y0+4y1+y2)/6");
        assertNotEquals(z1, valZ0, 0.001, "B-Spline does NOT interpolate (hit) the keyframe");
    }

    @Test
    public void testSegmentContinuity()
    {
        // Test continuity between two segments
        // Path: 0, 0, 10, 20, 0
        // Segment 1: inputs 0, 0, 10, 20. Interpolates between 0 and 10. Ends at t=1.
        // Segment 2: inputs 0, 10, 20, 0. Interpolates between 10 and 20. Starts at t=0.
        
        // At t=1 of Segment 1
        double val1 = Lerps.bSpline(0, 0, 10, 20, 1.0);
        
        // At t=0 of Segment 2
        double val2 = Lerps.bSpline(0, 10, 20, 0, 0.0);
        
        assertEquals(val1, val2, 0.0001, "B-Spline should be continuous at keyframe boundary");
        
        // With correct formula, val should be (0 + 4*10 + 20) / 6 = 10.0
        assertEquals(10.0, val1, 0.0001, "B-Spline value at join should be correct");
    }
}
