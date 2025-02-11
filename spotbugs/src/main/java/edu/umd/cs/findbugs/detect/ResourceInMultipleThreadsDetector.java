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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import edu.umd.cs.findbugs.Lookup;
import edu.umd.cs.findbugs.ba.CFG;
import edu.umd.cs.findbugs.ba.CFGBuilderException;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.Location;
import edu.umd.cs.findbugs.ba.LockDataflow;
import edu.umd.cs.findbugs.ba.OpcodeStackScanner;
import edu.umd.cs.findbugs.ba.XFactory;
import edu.umd.cs.findbugs.ba.Hierarchy;
import edu.umd.cs.findbugs.ba.JavaClassAndMethod;
import edu.umd.cs.findbugs.bcel.BCELUtil;
import edu.umd.cs.findbugs.classfile.MethodDescriptor;
import edu.umd.cs.findbugs.util.MultiThreadedCodeIdentifierUtils;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.BootstrapMethods;
import org.apache.bcel.classfile.ConstantInvokeDynamic;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.XField;
import edu.umd.cs.findbugs.ba.XMethod;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;
import edu.umd.cs.findbugs.classfile.CheckedAnalysisException;
import edu.umd.cs.findbugs.util.BootstrapMethodsUtil;
import edu.umd.cs.findbugs.util.CollectionAnalysis;
import edu.umd.cs.findbugs.util.MethodAnalysis;
import edu.umd.cs.findbugs.util.MutableClasses;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldInstruction;
import org.apache.bcel.generic.INVOKEDYNAMIC;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.PUTFIELD;
import org.apache.bcel.generic.PUTSTATIC;

public class ResourceInMultipleThreadsDetector extends OpcodeStackDetector {

    private static final class FieldData {
        private boolean modified = false;
        private boolean onlySynchronized = true;
        private boolean onlyPutField = true;
        private final Map<Method, Set<BugInstance>> methodBugs = new HashMap<>();
    }

    private final BugReporter bugReporter;

    private final Set<XField> synchronizedCollectionTypedFields = new HashSet<>();
    private final Map<XMethod, Set<XMethod>> calledMethodsByMethods = new HashMap<>();
    private final Set<XMethod> methodsUsedInThreads = new HashSet<>();
    private final Map<XField, FieldData> fieldsUsedInThreads = new HashMap<>();

    public ResourceInMultipleThreadsDetector(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    public void visit(ClassContext ctx) {
        resetState();
        JavaClass javaClass = ctx.getJavaClass();
        for (Method m : javaClass.getMethods()) {
            doVisitMethod(m);
        }

        for (XMethod xm : methodsUsedInThreads) {
            // it's a nest member, an anonymous class implementing Runnable
            if (!xm.getClassName().equals(javaClass.getClassName())) {
                collectMethods(ctx, javaClass, xm);
            }
        }

        for (XMethod xm : methodsUsedInThreads) {
            collectFieldsUsedInThreads(ctx, javaClass, xm);
        }
    }

    private void collectMethods(ClassContext classContext, JavaClass javaClass, XMethod xMethod) {
        try {
            Method method = Lookup.findImplementation(javaClass, xMethod.getName(), xMethod.getSignature());
            if (method != null) {
                CFG cfg = classContext.getCFG(method);
                ConstantPoolGen cpg = classContext.getConstantPoolGen();

                for (Location location : cfg.orderedLocations()) {
                    InstructionHandle handle = location.getHandle();
                    Instruction instruction = handle.getInstruction();

                    if (instruction instanceof InvokeInstruction && !(instruction instanceof INVOKEDYNAMIC)) {
                        XMethod calledMethod = XFactory.createXMethod((InvokeInstruction) instruction, cpg);
                        calledMethodsByMethods.computeIfAbsent(getXMethod(), v -> new HashSet<>()).add(calledMethod);
                        addToMethodsUsedInThreads(calledMethod);
                    }
                }
            }
        } catch (CFGBuilderException e) {
            bugReporter.logError("Detector ResourceInMultipleThreadsDetector caught exception while analyzing an anonymous class", e);
        }
    }

    @Override
    public void sawOpcode(int seen) {
        collectMethodsUsedInThreads(seen);
    }

    private void collectMethodsUsedInThreads(int seen) {
        if (seen == Const.INVOKEDYNAMIC) {
            JavaClass javaClass = getThisClass();
            Optional<Method> lambdaMethod = getMethodFromBootstrap(javaClass, (ConstantInvokeDynamic) getConstantRefOperand());
            if (lambdaMethod.isPresent()) {
                XMethod lambdaXMethod = XFactory.createXMethod(javaClass, lambdaMethod.get());
                calledMethodsByMethods.computeIfAbsent(getXMethod(), v -> new HashSet<>()).add(lambdaXMethod);

                if (getStack().getStackDepth() > 1
                        && "Ljava/lang/Thread;".equals(getStack().getStackItem(1).getSignature())
                        && !isJavaRuntimeMethod()) {
                    addToMethodsUsedInThreads(lambdaXMethod);
                }
            }

        } else if ((seen == Const.INVOKEVIRTUAL || seen == Const.INVOKEINTERFACE || seen == Const.INVOKESPECIAL || seen == Const.INVOKESTATIC)) {
            XMethod calledMethod = getXMethodOperand();
            if (calledMethod != null) {
                if ("java.lang.Thread".equals(calledMethod.getClassName()) && Const.CONSTRUCTOR_NAME.equals(calledMethod.getName())) {
                    int stackIdx = getStackIndexOfRunnable(calledMethod.getSignature());
                    if (stackIdx >= 0) {
                        OpcodeStack.Item stackItem = getStack().getStackItem(stackIdx);
                        try {
                            JavaClass runnableClass = stackItem.getJavaClass();
                            if (runnableClass != null && runnableClass.isAnonymous()) {
                                // java.lang.Runnable.run() method is the relevant one
                                JavaClassAndMethod runMethod = Hierarchy.findMethod(runnableClass, "run", "()V");
                                if (runMethod != null) {
                                    methodsUsedInThreads.add(runMethod.toXMethod());
                                }
                            }
                        } catch (ClassNotFoundException e) {
                            bugReporter.logError(String.format("Could not find class during analyzing %s with ResourceInMultipleThreadsDetector",
                                    getClassName()), e);
                        }
                    }
                }
                calledMethodsByMethods.computeIfAbsent(getXMethod(), v -> new HashSet<>()).add(calledMethod);

                if (methodsUsedInThreads.contains(getXMethod())
                        && getClassDescriptor().equals(calledMethod.getClassDescriptor())) {
                    addToMethodsUsedInThreads(calledMethod);
                }
            }
        }
    }

    private void addToMethodsUsedInThreads(XMethod methodToAdd) {
        methodsUsedInThreads.add(methodToAdd);
        if (calledMethodsByMethods.containsKey(methodToAdd)) {
            methodsUsedInThreads.addAll(calledMethodsByMethods.get(methodToAdd));
        }
    }

    /**
     * Get the stack index of java.lang.Runnable type if the provided signature is a valid signature of java.lang.Thread's constructors.
     * @param signature the provided method signature
     * @return the stack index of the java.lang.Runnable type
     */
    private static int getStackIndexOfRunnable(String signature) {
        switch (signature) {
        case "(Ljava/lang/Runnable;)V":
        case "(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;)V":
            return 0;
        case "(Ljava/lang/Runnable;Ljava/lang/String;)V":
        case "(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;Ljava/lang/String;)V":
            return 1;
        case "(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;Ljava/lang/String;J)V":
            return 2;
        default:
            return -1;
        }
    }

    /**
     * Ignore a special case where a Thread is passed to the {@code java.lang.Runtime} class,
     * so it is used as a shutdown hook.
     *
     * @return {@code true} if the Thread is passed to the {@code java.lang.Runtime} class, {@code false} otherwise
     */
    private boolean isJavaRuntimeMethod() {
        return IntStream.range(0, getStack().getStackDepth())
                .mapToObj(getStack()::getStackItem)
                .map(OpcodeStack.Item::getReturnValueOf)
                .filter(Objects::nonNull)
                .anyMatch(method -> "java.lang.Runtime".equals(method.getClassName()));
    }

    private Optional<Method> getMethodFromBootstrap(JavaClass javaClass, ConstantInvokeDynamic constDyn) {
        for (Attribute attr : javaClass.getAttributes()) {
            if (attr instanceof BootstrapMethods) {
                return BootstrapMethodsUtil.getMethodFromBootstrap((BootstrapMethods) attr,
                        constDyn.getBootstrapMethodAttrIndex(), getConstantPool(), javaClass);
            }
        }
        return Optional.empty();
    }

    private void collectFieldsUsedInThreads(ClassContext classContext, JavaClass javaClass, XMethod xMethod) {
        try {
            Method method = Lookup.findImplementation(javaClass, xMethod.getName(), xMethod.getSignature());
            if (method != null) {
                CFG cfg = classContext.getCFG(method);
                ConstantPoolGen cpg = classContext.getConstantPoolGen();
                LockDataflow lockDataflow = classContext.getLockDataflow(method);

                for (Location loc : cfg.orderedLocations()) {
                    InstructionHandle ih = loc.getHandle();
                    MethodDescriptor mdesc = BCELUtil.getMethodDescriptor(javaClass, method);
                    collectFieldsUsedInThreads(ih, javaClass, method, mdesc, cfg, lockDataflow, cpg);
                }
            }
        } catch (CheckedAnalysisException e) {
            bugReporter.logError(String.format("Detector ResourceInMultipleThreadsDetector caught exception while analyzing class %s", javaClass
                    .getClassName()), e);
        }
    }

    private void collectFieldsUsedInThreads(InstructionHandle ih, JavaClass javaClass, Method method, MethodDescriptor mdesc, CFG cfg,
            LockDataflow lockDataflow, ConstantPoolGen cpg) throws CheckedAnalysisException {
        Instruction instruction = ih.getInstruction();
        int pos = ih.getPosition();
        OpcodeStack currentStack = OpcodeStackScanner.getStackAt(javaClass, method, pos);
        if ((instruction instanceof PUTFIELD || instruction instanceof PUTSTATIC) && currentStack.getStackDepth() > 0
                && !MethodAnalysis.isDuplicatedLocation(mdesc, pos)) {
            FieldInstruction inf = (FieldInstruction) instruction;
            XField field = XFactory.createXField(inf, cpg);
            OpcodeStack.Item stackItem = currentStack.getStackItem(0);
            if (stackItem.getReturnValueOf() != null && CollectionAnalysis.isSynchronizedCollection(stackItem.getReturnValueOf())) {
                synchronizedCollectionTypedFields.add(field);
            } else if (!isAtomicTypedField(field)
                    && !(Const.CONSTRUCTOR_NAME.equals(method.getName()) || Const.STATIC_INITIALIZER_NAME.equals(method.getName()))) {
                boolean isLocked = MultiThreadedCodeIdentifierUtils.isLocked(method, cfg, lockDataflow, pos);
                createOrUpdateFieldData(field, true, method, null, isLocked);
            }
            //        } else if ((seen == Const.GETFIELD || seen == Const.GETSTATIC)
            //                && !MethodAnalysis.isDuplicatedLocation(getMethodDescriptor(), getPC())
            //                && methodsUsedInThreads.contains(getMethodDescriptor())
            //        ) {
            //            OpcodeStack.Item stackItem = getStack().getStackItem(0);

        } else if ((instruction instanceof InvokeInstruction && !(instruction instanceof INVOKEDYNAMIC))
                && currentStack.getStackDepth() > 0
                && !MethodAnalysis.isDuplicatedLocation(mdesc, pos)) {
            InvokeInstruction invIns = (InvokeInstruction) instruction;
            XMethod calledMethod = XFactory.createXMethod(invIns, cpg);
            // The field is accessed always be the last item in the stack, because the earlier elements are the arguments
            XField xField = currentStack.getStackItem(currentStack.getStackDepth() - 1).getXField();
            if (xField != null && !isAtomicTypedField(xField)) {
                boolean isLocked = MultiThreadedCodeIdentifierUtils.isLocked(method, cfg, lockDataflow, pos);
                createOrUpdateFieldData(xField, false, method, calledMethod, isLocked);
            }
        }
    }

    private void collectFieldsUsedInThreads(int seen) throws CheckedAnalysisException {
        if ((seen == Const.PUTFIELD || seen == Const.PUTSTATIC) && getStack().getStackDepth() > 0
                && !MethodAnalysis.isDuplicatedLocation(getMethodDescriptor(), getPC())
                && methodsUsedInThreads.contains(getXMethod())) {
            OpcodeStack.Item stackItem = getStack().getStackItem(0);
            XField field = getXFieldOperand();
            if (field != null) {
                if (stackItem.getReturnValueOf() != null && CollectionAnalysis.isSynchronizedCollection(stackItem.getReturnValueOf())) {
                    synchronizedCollectionTypedFields.add(field);
                } else if (!isAtomicTypedField(field)
                        && !(Const.CONSTRUCTOR_NAME.equals(getMethodName()) || Const.STATIC_INITIALIZER_NAME.equals(getMethodName()))) {
                    createOrUpdateFieldData(field, true, getMethod(), getXMethodOperand(), false);
                }
            }
        } else if ((seen == Const.GETFIELD || seen == Const.GETSTATIC)
        //                && !MethodAnalysis.isDuplicatedLocation(getMethodDescriptor(), getPC())
        //                && methodsUsedInThreads.contains(getMethodDescriptor())
        ) {
            OpcodeStack.Item stackItem = getStack().getStackItem(0);

        } else if ((seen == Const.INVOKEVIRTUAL || seen == Const.INVOKEINTERFACE || seen == Const.INVOKESPECIAL || seen == Const.INVOKESTATIC)
                && getXMethodOperand() != null && getStack().getStackDepth() > 0
                && !MethodAnalysis.isDuplicatedLocation(getMethodDescriptor(), getPC())
                && methodsUsedInThreads.contains(getXMethod())) {
            // The field is accessed always be the last item in the stack, because the earlier elements are the arguments
            XField xField = getStack().getStackItem(getStack().getStackDepth() - 1).getXField();
            if (xField != null && !isAtomicTypedField(xField)) {
                createOrUpdateFieldData(xField, false, getMethod(), getXMethodOperand(), false);
            }
        }
    }

    private boolean isAtomicTypedField(XField xField) {
        return xField.getSignature().contains("java/util/concurrent/atomic") || synchronizedCollectionTypedFields.contains(xField);
    }

    private void createOrUpdateFieldData(XField xField, boolean putfield, Method containerMethod, XMethod xMethod, boolean synchronizedBlock) {
        BugInstance bug = new BugInstance(this, "AT_UNSAFE_RESOURCE_ACCESS_IN_THREAD", LOW_PRIORITY)
                .addClassAndMethod(this)
                .addSourceLine(this)
                .addField(xField);
        if (!putfield) {
            bug.addCalledMethod(this);
        }

        FieldData data = fieldsUsedInThreads.computeIfAbsent(xField, value -> new FieldData());
        data.methodBugs.computeIfAbsent(containerMethod, value -> new HashSet<>()).add(bug);
        data.onlySynchronized &= synchronizedBlock;
        data.onlyPutField &= putfield;
        data.modified |= putfield || (xMethod != null && MutableClasses.looksLikeASetter(xMethod.getName()));
    }

    @Override
    public void visitAfter(JavaClass javaClass) {
        super.visit(javaClass);
        fieldsUsedInThreads.entrySet().stream()
                .filter(entry -> isBug(entry.getValue()))
                .flatMap(entry -> entry.getValue().methodBugs.values().stream().flatMap(Set::stream))
                .collect(Collectors.toSet())
                .forEach(bugReporter::reportBug);
    }

    /**
     * A bug is reported if the field is modified in multiple methods, it is not only accessed in synchronized blocks,
     * and it is not a synchronized collection or an atomic typed field.
     *
     * @param data the field data
     * @return {@code true} if the field is a bug, {@code false} otherwise
     */
    private static boolean isBug(FieldData data) {
        return data.modified && !data.onlySynchronized && data.methodBugs.size() > 1 && !data.onlyPutField;
    }

    private void resetState() {
        synchronizedCollectionTypedFields.clear();
        methodsUsedInThreads.clear();
        fieldsUsedInThreads.clear();
        calledMethodsByMethods.clear();
    }
}
