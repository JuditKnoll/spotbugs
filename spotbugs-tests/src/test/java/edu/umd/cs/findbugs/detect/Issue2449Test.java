package edu.umd.cs.findbugs.detect;

import edu.umd.cs.findbugs.AbstractIntegrationTest;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.test.matcher.BugInstanceMatcher;
import edu.umd.cs.findbugs.test.matcher.BugInstanceMatcherBuilder;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @see <a href="https://github.com/spotbugs/spotbugs/issues/2449">GitHub issue #2449</a>
 */
public class Issue2449Test extends AbstractIntegrationTest {
    @Test
    public void test() {
        performAnalysis("npe/DetectThrowers.class",
                "npe/DetectThrowersHelper.class");

        // Ideally there wouldn't be any hits, but there are 4 FPs:
        // DetectThrowers.runtimeExInnerIndirectlyThrown(Object)
        // DetectThrowers.exOuterIndirectlyThrown(Object),
        // DetectThrowers.runtimeExInnerIndirectlyThrown(Object),
        // DetectThrowers.runtimeExOuterIndirectlyThrown(Object).
        BugInstanceMatcher bugTypeMatcher = new BugInstanceMatcherBuilder()
                .bugType("NP_NULL_ON_SOME_PATH")
                .build();

        List<BugInstance> matchedBugs = getBugCollection().getCollection().stream()
                .filter(bugTypeMatcher::matches)
                .toList();

        assertThat(String.format("4 or less NP_NULL_ON_SOME_PATH bug hit expected, but got %d:%s",
                matchedBugs.size(),
                matchedBugs.stream()
                        .map(BugInstance::getMessage)
                        .collect(Collectors.joining(",\n\t", "\n\t", "."))),
                4 >= matchedBugs.size());
    }
}
