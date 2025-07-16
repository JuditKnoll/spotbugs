package edu.umd.cs.findbugs.detect;

import edu.umd.cs.findbugs.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class Issue3529Test extends AbstractIntegrationTest {

    @Test
    void testIssue() {
        performAnalysis("ghIssues/Issue3529.class");

        assertBugInMethod("DMI_EMPTY_DB_PASSWORD", "Issue3529", "getEmptyPassConn");
        assertBugInMethod("DMI_CONSTANT_DB_PASSWORD", "Issue3529", "getConstantPassConn");
        assertBugInMethod("DMI_CONSTANT_DB_PASSWORD", "Issue3529", "getConstantVarConn");

        assertBugInMethod("DMI_EMPTY_DB_PASSWORD", "Issue3529", "getTernaryConn");
        assertBugInMethod("DMI_CONSTANT_DB_PASSWORD", "Issue3529", "getTernaryConn");

        assertBugInMethod("DMI_EMPTY_DB_PASSWORD", "Issue3529", "getTernaryVarConn");
        assertBugInMethod("DMI_CONSTANT_DB_PASSWORD", "Issue3529", "getTernaryVarConn");

        assertBugTypeCount("DMI_EMPTY_DB_PASSWORD", 3);
        assertBugTypeCount("DMI_CONSTANT_DB_PASSWORD", 4);
    }
}
