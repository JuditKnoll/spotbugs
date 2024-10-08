package edu.umd.cs.findbugs.detect;

import edu.umd.cs.findbugs.AbstractIntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class Issue2019Test extends AbstractIntegrationTest {

    @Test
    void testIssue() {
        Assertions.assertDoesNotThrow(() -> performAnalysis("ghIssues/Issue2019$ATNSimulator.class",
                "ghIssues/Issue2019$BaseParser.class",
                "ghIssues/Issue2019$Parser.class",
                "ghIssues/Issue2019$ParserATNSimulator.class",
                "ghIssues/Issue2019$Recognizer.class",
                "ghIssues/Issue2019.class"));
    }
}
