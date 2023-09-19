package edu.umd.cs.findbugs.detect;

import edu.umd.cs.findbugs.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * @see <a href="https://github.com/spotbugs/spotbugs/issues/2449">GitHub issue #2449</a>
 */
class Issue2449Test extends AbstractIntegrationTest {
    @Test
    void test() {
        performAnalysis("npe/DetectThrowers.class",
                "npe/DetectThrowersHelper.class");

        // Ideally there wouldn't be any hits, but there are 4 FPs:
        // In DetectThrowers.exInnerIndirectlyThrown(Object) - line 42
        // In DetectThrowers.exOuterIndirectlyThrown(Object) - line 56
        // In DetectThrowers.runtimeExInnerIndirectlyThrown(Object) - line 30
        // In DetectThrowers.runtimeExOuterIndirectlyThrown(Object) - line 70
        assertNoBugType("NP_NULL_ON_SOME_PATH");
    }
}
