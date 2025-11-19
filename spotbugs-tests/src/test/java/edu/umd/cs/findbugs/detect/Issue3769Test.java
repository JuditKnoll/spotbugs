package edu.umd.cs.findbugs.detect;

import edu.umd.cs.findbugs.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class Issue3769Test extends AbstractIntegrationTest {

    @Test
    void testIssue() {
        performAnalysis("ghIssues/Issue3769.class");
        assertBugInMethodAtLine("BC_IMPOSSIBLE_DOWNCAST","Issue3769", "issue3769", 12);
        assertBugInMethodAtLine("BC_IMPOSSIBLE_DOWNCAST","Issue3769", "issue3788", 16);
        assertBugInMethodAtLine("BC_IMPOSSIBLE_DOWNCAST_OF_TOARRAY", "Issue3769", "issue3752", 23);
        assertBugInMethodAtLine("BC_IMPOSSIBLE_DOWNCAST_OF_TOARRAY", "Issue3769", "issue3752", 24);
        assertBugTypeCount("BC_IMPOSSIBLE_DOWNCAST", 2);
        assertBugTypeCount("BC_IMPOSSIBLE_DOWNCAST_OF_TOARRAY", 2);
    }
}
