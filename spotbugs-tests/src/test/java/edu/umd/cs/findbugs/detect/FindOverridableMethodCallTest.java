package edu.umd.cs.findbugs.detect;

import static edu.umd.cs.findbugs.test.CountMatcher.containsExactly;
import static org.hamcrest.Matchers.hasItem;

import edu.umd.cs.findbugs.BugCollection;
import edu.umd.cs.findbugs.test.SpotBugsExtension;
import edu.umd.cs.findbugs.test.SpotBugsRunner;
import org.apache.bcel.Const;

import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import edu.umd.cs.findbugs.AbstractIntegrationTest;
import edu.umd.cs.findbugs.annotations.Confidence;
import edu.umd.cs.findbugs.test.matcher.BugInstanceMatcher;
import edu.umd.cs.findbugs.test.matcher.BugInstanceMatcherBuilder;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Paths;

@ExtendWith(SpotBugsExtension.class)
class FindOverridableMethodCallTest extends AbstractIntegrationTest {
    @Test
    @DisabledOnJre({ JRE.JAVA_8 })
    void testIssue2414_java11(SpotBugsRunner spotbugs) {
        BugCollection bugCollection = spotbugs.performAnalysis(Paths.get(
                "../spotbugsTestCases/build/classes/java/java17/overridableMethodCall/Issue2414.class"));

        final BugInstanceMatcher bugInstanceMatcher = new BugInstanceMatcherBuilder()
                .bugType("MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR")
                //                .inClass("Issue2414")
                //                .inMethod(Const.CONSTRUCTOR_NAME)
                //                .withConfidence(Confidence.LOW)
                //                .atLine(line)
                .build();

        assertThat(bugCollection, containsExactly(1, bugInstanceMatcher));
    }

    @Test
    void testIssue2414_java8() {
        performAnalysis("overridableMethodCall/Issue2414.class");

        final BugInstanceMatcher bugInstanceMatcher = new BugInstanceMatcherBuilder()
                .bugType("MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR")
                .build();

        assertThat(getBugCollection(), containsExactly(0, bugInstanceMatcher));
    }

    @Test
    void testDirectCase() {
        testCase("DirectCase", 5, 18);
    }

    @Test
    void testDirectCaseObject() {
        testCase("DirectCaseObject", 5, 13);
    }

    @Test
    void testIndirectCase1() {
        testCase("IndirectCase1", 9, 22);
    }

    @Test
    void testIndirectCase2() {
        testCase("IndirectCase2", 5, 18);
    }

    @Test
    void testDoubleIndirectCase1() {
        testCase("DoubleIndirectCase1", 16, 29);
    }

    @Test
    void testDoubleIndirectCase2() {
        testCase("DoubleIndirectCase2", 16, 29);
    }

    @Test
    void testDoubleIndirectCase3() {
        testCase("DoubleIndirectCase3", 11, 24);
    }

    @Test
    void testDoubleIndirectCase4() {
        testCase("DoubleIndirectCase4", 10, 23);
    }

    @Test
    void testDoubleIndirectCase5() {
        testCase("DoubleIndirectCase5", 5, 18);
    }

    @Test
    void testDoubleIndirectCase6() {
        testCase("DoubleIndirectCase6", 5, 18);
    }

    @Test
    void testMethodReference() {
        testCase("MethodReference", 11, 20);
    }

    @Test
    void testMethodReferenceIndirect1() {
        testCase("MethodReferenceIndirect1", 15, 24);
    }

    @Test
    void testMethodReferenceIndirect2() {
        testCase("MethodReferenceIndirect2", 23, 32);
    }

    @Test
    void testMethodReferenceIndirect3() {
        testCase("MethodReferenceIndirect3", 23, 32);
    }

    @Test
    void testFinalClassDirect() {
        testPass("FinalClassDirect");
    }

    @Test
    void testFinalClassIndirect() {
        testPass("FinalClassIndirect");
    }

    @Test
    void testFinalClassDoubleIndirect() {
        testPass("FinalClassDoubleIndirect");
    }

    @Test
    void testFinalClassMethodReference() {
        testPass("FinalClassMethodReference");
    }

    @Test
    void testFinalClassInheritedDirect() {
        testPass("FinalClassInheritedDirect");
    }

    @Test
    void testFinalClassInheritedIndirect() {
        testPass("FinalClassInheritedIndirect");
    }

    @Test
    void testFinalClassInheritedDoubleIndirect() {
        testPass("FinalClassInheritedDoubleIndirect");
    }

    @Test
    void testFinalClassInheritedMethodReference() {
        testPass("FinalClassInheritedMethodReference");
    }

    void testCase(String className, int constructorLine, int cloneLine) {
        performAnalysis("overridableMethodCall/" + className + ".class");

        checkOneBug();
        checkOverridableMethodCallInConstructor(className, constructorLine);
        checkOverridableMethodCallInClone(className, cloneLine);
    }

    void testPass(String className) {
        performAnalysis("overridableMethodCall/" + className + ".class");

        checkNoBug();
    }

    void checkOneBug() {
        BugInstanceMatcher bugTypeMatcher = new BugInstanceMatcherBuilder()
                .bugType("MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR").build();
        assertThat(getBugCollection(), containsExactly(1, bugTypeMatcher));

        bugTypeMatcher = new BugInstanceMatcherBuilder()
                .bugType("MC_OVERRIDABLE_METHOD_CALL_IN_CLONE").build();
        assertThat(getBugCollection(), containsExactly(1, bugTypeMatcher));
    }

    void checkNoBug() {
        BugInstanceMatcher bugTypeMatcher = new BugInstanceMatcherBuilder()
                .bugType("MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR").build();
        assertThat(getBugCollection(), containsExactly(0, bugTypeMatcher));

        bugTypeMatcher = new BugInstanceMatcherBuilder()
                .bugType("MC_OVERRIDABLE_METHOD_CALL_IN_CLONE").build();
        assertThat(getBugCollection(), containsExactly(0, bugTypeMatcher));
    }

    void checkOverridableMethodCallInConstructor(String className, int line) {
        final BugInstanceMatcher bugInstanceMatcher = new BugInstanceMatcherBuilder()
                .bugType("MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR")
                .inClass(className)
                .inMethod(Const.CONSTRUCTOR_NAME)
                .withConfidence(Confidence.LOW)
                .atLine(line)
                .build();
        assertThat(getBugCollection(), hasItem(bugInstanceMatcher));
    }

    void checkOverridableMethodCallInClone(String className, int line) {
        final BugInstanceMatcher bugInstanceMatcher = new BugInstanceMatcherBuilder()
                .bugType("MC_OVERRIDABLE_METHOD_CALL_IN_CLONE")
                .inClass(className)
                .inMethod("clone")
                .atLine(line)
                .build();
        assertThat(getBugCollection(), hasItem(bugInstanceMatcher));
    }
}
