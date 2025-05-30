/*
 * SpotBugs - Find bugs in Java programs
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package edu.umd.cs.findbugs.detect;

import java.util.*;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.ba.AnalysisContext;
import edu.umd.cs.findbugs.ba.XMethod;
import edu.umd.cs.findbugs.internalAnnotations.DottedClassName;
import edu.umd.cs.findbugs.util.ClassName;
import org.apache.bcel.Const;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugAccumulator;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

/**
 * This detector can find constructors that throw exception.
 */
public class ConstructorThrow extends OpcodeStackDetector {
    private final BugAccumulator bugAccumulator;

    /**
     * The containing methods (DottedClassName complete with signature) to the methods called directly from the containing one
     * to the caught Exceptions by the surrounding try-catches of the call sites.
     * If the call site is not inside a try-catch then an empty string.
     */
    private final Map<String, Map<String, Set<String>>> exHandlesToMethodCallsByMethodsMap = new HashMap<>();

    /**
     * The DottedClassName complete with signature of the method to the set of the Exceptions thrown directly from the method.
     */
    private final Map<String, Set<JavaClass>> thrownExsByMethodMap = new HashMap<>();

    private boolean isFinalClass = false;
    private boolean isFinalFinalizer = false;
    private boolean isFirstPass = true;
    private boolean hadObjectConstructor = false;

    public ConstructorThrow(BugReporter bugReporter) {
        this.bugAccumulator = new BugAccumulator(bugReporter);
    }

    /**
     * Visit a class to find the constructor, then collect all the methods that gets called in it.
     * Also, we are checking for final declaration on the class, or a final finalizer, as if present
     * no finalizer attack can happen.
     */
    @Override
    public void visit(JavaClass obj) {
        resetState();
        if (obj.isFinal()) {
            isFinalClass = true;
            return;
        }

        isFinalFinalizer = hasFinalFinalizer(obj);
        try {
            for (JavaClass cl : obj.getSuperClasses()) {
                isFinalFinalizer |= hasFinalFinalizer(cl);
            }
        } catch (ClassNotFoundException e) {
            AnalysisContext.reportMissingClass(e);
        }

        for (Method m : obj.getMethods()) {
            doVisitMethod(m);
        }
        isFirstPass = false;
    }

    private static boolean hasFinalFinalizer(JavaClass jc) {
        // Check for final finalizer.
        // Signature of the finalizer is also needed to be checked
        return Arrays.stream(jc.getMethods())
                .anyMatch(m -> "finalize".equals(m.getName()) && "()V".equals(m.getSignature()) && m.isFinal());
    }

    @Override
    public void visit(Method obj) {
        hadObjectConstructor = false;
        super.visit(obj);
    }

    @Override
    public void visitAfter(JavaClass obj) {
        super.visit(obj);
        bugAccumulator.reportAccumulatedBugs();
    }

    /**
     * 1. Check for any throw expression in the constructor.
     * 2. Check for any exception throw inside constructor, or any of the called methods.
     * If the class is final, we are fine, no finalizer attack can happen.
     * In the first pass the detector shouldn't report, because there could be
     * a final finalizer and a throwing constructor. Reporting in this case
     * would be a false positive as classes with a final finalizer are not
     * vulnerable to the finalizer attack.
     */
    @Override
    public void sawOpcode(int seen) {
        if (isFinalClass || isFinalFinalizer) {
            return;
        }
        if (isFirstPass) {
            collectExceptionsByMethods(seen);
        } else if (Const.CONSTRUCTOR_NAME.equals(getMethodName())) {
            reportConstructorThrow(seen);
        }
    }

    /**
     * Reports ConstructorThrow bug if there is an unhandled unchecked exception thrown directly or indirectly
     * from the currently visited method.
     * If the exception is thrown directly, the bug is reported at the throw.
     * If the exception is thrown indirectly (through a method call), the bug is reported at the call of the method
     * which throws the exception.
     */
    private void reportConstructorThrow(int seen) {
        // if there is a throw in the Constructor which is not handled in a try-catch block, it's a bug
        if (seen == Const.ATHROW) {
            OpcodeStack.Item item = stack.getStackItem(0);
            if (item != null) {
                try {
                    JavaClass thrownExClass = item.getJavaClass();
                    // in case of try-with-resources the compiler generates a nested try-catch with Throwable
                    // so filter out Throwable
                    // however this filters out Throwable explicitly thrown by the programmer, that is not so common
                    if (thrownExClass == null || "java.lang.Throwable".equals(thrownExClass.getClassName())) {
                        return;
                    }
                    Set<String> caughtExes = getSurroundingCaughtExes(getConstantPool());
                    if (isThrownExNotCaught(thrownExClass, caughtExes)) {
                        accumulateBug();
                    }
                } catch (ClassNotFoundException e) {
                    AnalysisContext.reportMissingClass(e);
                }
            }
        } else if (isMethodCall()) {
            if (Const.CONSTRUCTOR_NAME.equals(getNameConstantOperand())) {
                hadObjectConstructor = true;
            }

            String calledMethodFQN = getCalledMethodFQN();
            Set<JavaClass> unhandledExes = getUnhandledExThrowsInMethod(calledMethodFQN, new HashSet<>());
            if (hadObjectConstructor && !unhandledExes.isEmpty()) {
                Set<String> caughtExes = getSurroundingCaughtExes(getConstantPool());
                boolean hasNotCaughtExFromBody = unhandledExes.stream()
                        .anyMatch(ex -> isThrownExNotCaught(ex, caughtExes));

                if (hasNotCaughtExFromBody) {
                    accumulateBug();
                }
            }
        }
    }

    /**
     * Get the Exceptions thrown from the inside of the method, either directly or indirectly from called methods.
     * Uses inner collections which are needed to filled correctly.
     *
     * @param method the method to visit and get the exceptions thrown out of it
     * @param visitedMethods the names of the already visited methods, needed to prevent stackoverflow by recursively checking method call cycles
     * @return the JavaClasses of the Exceptions thrown from the method
     */
    private Set<JavaClass> getUnhandledExThrowsInMethod(String method, Set<String> visitedMethods) {
        Set<JavaClass> unhandledExesInMethod = new HashSet<>();
        if (visitedMethods.contains(method)) {
            return unhandledExesInMethod;
        } else {
            visitedMethods.add(method);
        }

        if (thrownExsByMethodMap.containsKey(method)) {
            unhandledExesInMethod.addAll(thrownExsByMethodMap.get(method));
        }

        if (exHandlesToMethodCallsByMethodsMap.containsKey(method)) {
            Map<String, Set<String>> exHandlesByMethodCalls = exHandlesToMethodCallsByMethodsMap.get(method);
            for (Map.Entry<String, Set<String>> entry : exHandlesByMethodCalls.entrySet()) {
                String calledMethod = entry.getKey();
                Set<JavaClass> unhandledExes = getUnhandledExThrowsInMethod(calledMethod, visitedMethods);
                Set<String> exHandles = entry.getValue();
                Set<JavaClass> remainingUnhandledExes = unhandledExes.stream()
                        .filter(ex -> !isHandled(ex, exHandles))
                        .collect(Collectors.toSet());
                unhandledExesInMethod.addAll(remainingUnhandledExes);
            }
        }
        return unhandledExesInMethod;
    }

    /**
     * Checks whether the Exception is handled in all call sites.
     * @param thrownEx the thrown Exception which needs to be handled
     * @param exHandles the set of the dotted class names of the caught Exceptions in the call sites.
     * @return true if the Exception handled in all call sites.
     */
    private boolean isHandled(JavaClass thrownEx, Set<String> exHandles) {
        return exHandles.stream().allMatch(handle -> isHandled(thrownEx, handle));
    }

    /**
     * Checks if the thrown Exception is handled by the caught Exception.
     * @param thrownEx the thrown Exception which needs to be handled
     * @param caughtEx the name of the caught Exception at the call site. If no Exception is caught,
     *                 then it's an empty string or other nonnull string which is not a name of any Exception.
     * @return true if the Exception is handled.
     */
    private static boolean isHandled(JavaClass thrownEx, @NonNull @DottedClassName String caughtEx) {
        try {
            return thrownEx.getClassName().equals(caughtEx)
                    || Arrays.stream(thrownEx.getSuperClasses()).anyMatch(e -> e.getClassName().equals(caughtEx));
        } catch (ClassNotFoundException e) {
            AnalysisContext.reportMissingClass(e);
        }
        return false;
    }

    /**
     * Gets the DottedClassNames of the Exceptions which are caught by a try-catch block at the current PC.
     * @param cp ConstantPool
     * @return Set of the DottedClassNames of the caught Exceptions.
     */
    private Set<String> getSurroundingCaughtExes(ConstantPool cp) {
        return getSurroundingCaughtExceptionTypes(getPC(), Integer.MAX_VALUE).stream()
                .filter(i -> i != 0)
                .map(caughtExType -> cp.constantToString(cp.getConstant(caughtExType)))
                .collect(Collectors.toSet());
    }

    /**
     * Checks if the thrown exception is not caught.
     * @param thrownEx the Exception to catch.
     * @param caughtExes the set of the DottedClassNames of the caught Exceptions at call site.
     * @return true if the exception is not caught.
     */
    private static boolean isThrownExNotCaught(JavaClass thrownEx, Set<String> caughtExes) {
        return caughtExes.stream().noneMatch(caughtEx -> isHandled(thrownEx, caughtEx));
    }

    private static String toDotted(String signature) {
        if (signature.startsWith("L") && signature.endsWith(";")) {
            return ClassName.toDottedClassName(signature.substring(1, signature.length() - 1));
        }
        return ClassName.toDottedClassName(signature);
    }

    /**
     * Fills the inner collections while visiting the method.
     * @param seen the opcode @see #sawOpcode(int)
     */
    private void collectExceptionsByMethods(int seen) {
        String containingMethod = getFullyQualifiedMethodName();
        if (seen == Const.ATHROW) {
            OpcodeStack.Item item = stack.getStackItem(0);
            if (item != null) {
                try {
                    JavaClass thrownExClass = item.getJavaClass();
                    // in case of try-with-resources the compiler generates a nested try-catch with Throwable
                    // so filter out Throwable
                    // however this filters out throwable explicitly thrown by the programmer, that is not so common
                    if (thrownExClass == null || "java.lang.Throwable".equals(thrownExClass.getClassName())) {
                        return;
                    }
                    Set<String> caughtExes = getSurroundingCaughtExes(getConstantPool());
                    if (isThrownExNotCaught(thrownExClass, caughtExes)) {
                        addToThrownExsByMethodMap(containingMethod, thrownExClass);
                    }
                } catch (ClassNotFoundException e) {
                    AnalysisContext.reportMissingClass(e);
                }
            }
        } else if (isMethodCall()) {
            String calledMethodName = getNameConstantOperand();
            String calledMethodFullName = getCalledMethodFQN();
            // not interested in call of the constructor or recursion
            if (!Const.CONSTRUCTOR_NAME.equals(calledMethodName) && !containingMethod.equals(calledMethodFullName)) {
                Set<String> caughtExes = getSurroundingCaughtExes(getConstantPool());
                if (caughtExes.isEmpty()) {
                    // No Exception is handled, then add an empty string to represent this
                    addToExHandlesToMethodCallsByMethodsMap(containingMethod, calledMethodFullName, Collections.singletonList(""));
                } else {
                    addToExHandlesToMethodCallsByMethodsMap(containingMethod, calledMethodFullName, caughtExes);
                }

                XMethod calledXMethod = getXMethodOperand();
                if (calledXMethod != null) {
                    String[] thrownCheckedExes = calledXMethod.getThrownExceptions();
                    if (thrownCheckedExes != null) {
                        for (String thrownCheckedEx : thrownCheckedExes) {
                            try {
                                JavaClass exClass = AnalysisContext.currentAnalysisContext().lookupClass(thrownCheckedEx);
                                addToThrownExsByMethodMap(calledMethodFullName, exClass);
                            } catch (ClassNotFoundException e) {
                                AnalysisContext.reportMissingClass(e);
                            }
                        }
                    }
                }
            }
        }
    }

    private void addToExHandlesToMethodCallsByMethodsMap(String containerMethod, String calledMethod, Collection<String> caughtExes) {
        exHandlesToMethodCallsByMethodsMap
                .computeIfAbsent(containerMethod, k -> new HashMap<>())
                .computeIfAbsent(calledMethod, k -> new HashSet<>())
                .addAll(caughtExes);
    }

    private void addToThrownExsByMethodMap(String containingMethod, JavaClass thrownExClass) {
        thrownExsByMethodMap.computeIfAbsent(containingMethod, k -> new HashSet<>()).add(thrownExClass);
    }

    /**
     * Gives back the fully qualified name (DottedClassName) of the called method complete with the signature.
     * Needs to be called from method call opcode.
     * This is in sync with {@link edu.umd.cs.findbugs.visitclass.PreorderVisitor#getFullyQualifiedMethodName} function.
     *
     * @return the fully qualified name of the method (dotted) with the signature.
     */
    private String getCalledMethodFQN() {
        return String.format("%s.%s : %s", getDottedClassConstantOperand(), getNameConstantOperand(),
                toDotted(getSigConstantOperand()));
    }

    private void resetState() {
        isFinalClass = false;
        isFinalFinalizer = false;
        isFirstPass = true;
        exHandlesToMethodCallsByMethodsMap.clear();
        thrownExsByMethodMap.clear();
    }

    private void accumulateBug() {
        BugInstance bug = new BugInstance(this, "CT_CONSTRUCTOR_THROW", NORMAL_PRIORITY)
                .addClassAndMethod(this)
                .addSourceLine(this, getPC());
        bugAccumulator.accumulateBug(bug, this);
    }
}
