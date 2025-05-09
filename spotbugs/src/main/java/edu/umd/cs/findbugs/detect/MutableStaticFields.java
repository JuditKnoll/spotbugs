/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2003,2004 University of Maryland
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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.AnalysisContext;
import edu.umd.cs.findbugs.ba.XClass;
import edu.umd.cs.findbugs.ba.XField;
import edu.umd.cs.findbugs.ba.XMethod;
import edu.umd.cs.findbugs.classfile.CheckedAnalysisException;
import edu.umd.cs.findbugs.classfile.ClassDescriptor;
import edu.umd.cs.findbugs.classfile.Global;
import edu.umd.cs.findbugs.util.MutableClasses;

public class MutableStaticFields extends BytecodeScanningDetector {
    private static final Set<String> COLLECTION_SUPERCLASSES = Set.of("java/util/Collection",
            "java/util/List", "java/util/Set", "java/util/Map", "java/util/AbstractList", "java/util/SortedSet",
            "java/util/SortedMap", "java/util/NavigableMap", "java/util/Dictionary");

    private static final Set<String> MUTABLE_COLLECTION_CLASSES = Set.of("java/util/ArrayList",
            "java/util/HashSet", "java/util/HashMap", "java/util/Hashtable", "java/util/IdentityHashMap",
            "java/util/LinkedHashSet", "java/util/LinkedList", "java/util/LinkedHashMap", "java/util/TreeSet",
            "java/util/TreeMap", "java/util/Properties");

    private static enum AllowedParameter {
        NONE, EMPTY_ARRAY
    }

    private static final Map<String, Map<String, AllowedParameter>> MUTABLE_COLLECTION_METHODS = Map.of(
            "java/util/Arrays", Collections.singletonMap("asList", AllowedParameter.EMPTY_ARRAY),
            "com/google/common/collect/Lists", Map.of("newArrayList", AllowedParameter.NONE, "newLinkedList", AllowedParameter.NONE),
            "com/google/common/collect/Sets", Map.of("newHashSet", AllowedParameter.NONE, "newTreeSet", AllowedParameter.NONE));

    static String extractPackage(String c) {
        int i = c.lastIndexOf('/');
        if (i < 0) {
            return "";
        }
        return c.substring(0, i);
    }

    LinkedList<XField> seen = new LinkedList<>();

    boolean publicClass;

    boolean mutableCollectionJustCreated = false;

    boolean zeroOnTOS;

    boolean emptyArrayOnTOS;

    boolean inStaticInitializer;

    String packageName;

    Set<XField> readAnywhere = new HashSet<>();

    Set<XField> unsafeValue = new HashSet<>();

    Set<XField> mutableCollection = new HashSet<>();

    Set<XField> notFinal = new HashSet<>();

    Set<XField> outsidePackage = new HashSet<>();

    Set<XField> needsRefactoringToBeFinal = new HashSet<>();

    Set<XField> writtenInMethod = new HashSet<>();

    Set<XField> writtenTwiceInMethod = new HashSet<>();

    Map<XField, SourceLineAnnotation> firstFieldUse = new HashMap<>();

    private final BugReporter bugReporter;

    /**
     * Eclipse uses reflection to initialize NLS message bundles. Classes which
     * using this mechanism are usually extending org.eclipse.osgi.util.NLS class
     * and contains lots of public static String fields which are used as
     * message Constants. Unfortunately these fields cannot be final, so FB
     * reports tons of warnings for such Eclipse classes.
     */
    private boolean isEclipseNLS;

    public MutableStaticFields(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visit(JavaClass obj) {
        super.visit(obj);
        int flags = obj.getAccessFlags();
        publicClass = (flags & Const.ACC_PUBLIC) != 0 && !getDottedClassName().startsWith("sun.");

        packageName = extractPackage(getClassName());
        isEclipseNLS = "org.eclipse.osgi.util.NLS".equals(obj.getSuperclassName());
    }

    @Override
    public void visit(Method obj) {
        zeroOnTOS = false;
        // System.out.println(methodName);
        inStaticInitializer = getMethodName().equals(Const.STATIC_INITIALIZER_NAME);
    }

    @Override
    public void visit(Code obj) {
        writtenInMethod.clear();
        writtenTwiceInMethod.clear();
        super.visit(obj);
        if (inStaticInitializer) {
            needsRefactoringToBeFinal.addAll(writtenTwiceInMethod);
        }
        writtenInMethod.clear();
        writtenTwiceInMethod.clear();
    }

    @Override
    public void sawOpcode(int seen) {
        // System.out.println("saw\t" + Const.getOpcodeName(seen) + "\t" + zeroOnTOS);
        switch (seen) {
        case Const.GETSTATIC:
        case Const.PUTSTATIC:

            XField xField = getXFieldOperand();
            if (xField == null) {
                break;
            }
            if (!interesting(xField)) {
                break;
            }

            boolean samePackage = packageName.equals(extractPackage(xField.getFieldDescriptor().getSlashedClassName()));
            boolean initOnly = seen == Const.GETSTATIC || getClassName().equals(getClassConstantOperand()) && inStaticInitializer;
            boolean safeValue = seen == Const.GETSTATIC || emptyArrayOnTOS
                    || AnalysisContext.currentXFactory().isEmptyArrayField(xField) || !MutableClasses.mutableSignature(getSigConstantOperand());

            if (seen == Const.GETSTATIC) {
                readAnywhere.add(xField);
            }
            if (seen == Const.PUTSTATIC) {
                if (xField.isFinal() && mutableCollectionJustCreated) {
                    mutableCollection.add(xField);
                }
                if (!writtenInMethod.add(xField)) {
                    writtenTwiceInMethod.add(xField);
                }
            }

            if (!samePackage) {
                outsidePackage.add(xField);
            }

            if (!initOnly) {
                notFinal.add(xField);
            }

            if (!safeValue) {
                unsafeValue.add(xField);
            }

            // Remove inStaticInitializer check to report all source lines of
            // first use
            // doing so, however adds quite a bit of memory bloat.
            if (inStaticInitializer && !firstFieldUse.containsKey(xField)) {
                SourceLineAnnotation sla = SourceLineAnnotation.fromVisitedInstruction(this);
                firstFieldUse.put(xField, sla);
            }
            break;
        case Const.ANEWARRAY:
        case Const.NEWARRAY:
            if (zeroOnTOS) {
                emptyArrayOnTOS = true;
            }
            zeroOnTOS = false;
            return;
        case Const.ICONST_0:
            zeroOnTOS = true;
            emptyArrayOnTOS = false;
            return;
        case Const.INVOKESPECIAL:
            if (inStaticInitializer && Const.CONSTRUCTOR_NAME.equals(getMethodDescriptorOperand().getName())) {
                ClassDescriptor classDescriptor = getClassDescriptorOperand();
                if (MUTABLE_COLLECTION_CLASSES.contains(classDescriptor.getClassName())) {
                    mutableCollectionJustCreated = true;
                    return;
                }
                try {
                    /* Check whether it's statically initialized anonymous class like this:
                     * public static final Map map = new HashMap() {{put("a", "b");}}
                     * We do not check whether all modification methods are overridden or not for simplicity:
                     * Skip if there's at least one method is present
                     */
                    XClass xClass = classDescriptor.getXClass();
                    ClassDescriptor superclassDescriptor = xClass.getSuperclassDescriptor();
                    if (superclassDescriptor != null
                            && MUTABLE_COLLECTION_CLASSES.contains(superclassDescriptor.getClassName())) {
                        mutableCollectionJustCreated = true;
                        for (XMethod xMethod : xClass.getXMethods()) {
                            if (xMethod != null && !Const.CONSTRUCTOR_NAME.equals(xMethod.getName()) && !Const.STATIC_INITIALIZER_NAME.equals(xMethod
                                    .getName())) {
                                mutableCollectionJustCreated = false;
                                break;
                            }
                        }
                        return;
                    }
                } catch (CheckedAnalysisException e) {
                    // ignore
                }
            }
            break;
        case Const.INVOKESTATIC:
            if (inStaticInitializer) {
                Map<String, AllowedParameter> methods = MUTABLE_COLLECTION_METHODS.get(getMethodDescriptorOperand()
                        .getSlashedClassName());
                if (methods != null) {
                    String name = getMethodDescriptorOperand().getName();
                    AllowedParameter allowedParameter = methods.get(name);
                    if (allowedParameter == AllowedParameter.NONE
                            || (allowedParameter == AllowedParameter.EMPTY_ARRAY && !emptyArrayOnTOS)) {
                        mutableCollectionJustCreated = true;
                        return;
                    }
                }
            }
            break;
        }
        zeroOnTOS = false;
        emptyArrayOnTOS = false;
        mutableCollectionJustCreated = false;
    }

    private boolean isCollection(String signature) {
        if (signature.startsWith("L") && signature.endsWith(";")) {
            String fieldClass = signature.substring(1, signature.length() - 1);
            return COLLECTION_SUPERCLASSES.contains(fieldClass) || MUTABLE_COLLECTION_CLASSES.contains(fieldClass);
        }
        return false;
    }

    private boolean interesting(XField f) {
        if (!f.isPublic() && !f.isProtected()) {
            return false;
        }
        if (!f.isStatic() || f.isSynthetic() || f.isVolatile()) {
            return false;
        }
        if (!f.isFinal()) {
            return true;
        }
        String fieldSig = f.getSignature();
        return fieldSig.charAt(0) == '[' || isCollection(fieldSig);
    }

    @Override
    public void visit(Field obj) {
        super.visit(obj);
        int flags = obj.getAccessFlags();
        boolean isStatic = (flags & Const.ACC_STATIC) != 0;
        if (!isStatic) {
            return;
        }
        boolean isVolatile = (flags & Const.ACC_VOLATILE) != 0;
        if (isVolatile) {
            return;
        }
        boolean isFinal = (flags & Const.ACC_FINAL) != 0;
        boolean isPublic = publicClass && (flags & Const.ACC_PUBLIC) != 0;
        boolean isProtected = publicClass && (flags & Const.ACC_PROTECTED) != 0;
        if (!isPublic && !isProtected) {
            return;
        }

        boolean isArray = getFieldSig().charAt(0) == '[';

        if (isFinal && !(isArray || isCollection(getFieldSig()))) {
            return;
        }
        if (isEclipseNLS && getFieldSig().equals("Ljava/lang/String;")) {
            return;
        }

        seen.add(getXField());
    }

    @Override
    public void report() {
        /*
         * for(Iterator i = unsafeValue.iterator(); i.hasNext(); ) {
         * System.out.println("Unsafe: " + i.next()); }
         */
        for (XField f : seen) {
            boolean isFinal = f.isFinal();
            String className = f.getClassName();
            String fieldSig = f.getSignature();
            String fieldName = f.getName();
            boolean couldBeFinal = !isFinal && !notFinal.contains(f);
            //            boolean isPublic = f.isPublic();
            boolean couldBePackage = !outsidePackage.contains(f);
            boolean isMutableCollection = mutableCollection.contains(f);
            boolean movedOutofInterface = false;

            try {
                XClass xClass = Global.getAnalysisCache().getClassAnalysis(XClass.class, f.getClassDescriptor());
                movedOutofInterface = couldBePackage && xClass.isInterface();
            } catch (CheckedAnalysisException e) {
                assert true;
            }
            boolean isHashtable = fieldSig.equals("Ljava/util/Hashtable;");
            boolean isArray = fieldSig.charAt(0) == '[' && unsafeValue.contains(f);
            boolean isReadAnywhere = readAnywhere.contains(f);
            //            if (false) {
            //                System.out.println(className + "." + fieldName + " : " + fieldSig + "\t" + isHashtable + "\t" + isArray);
            //            }

            String bugType;
            int priority = NORMAL_PRIORITY;
            if (isFinal && !isHashtable && !isArray && !isMutableCollection) {
                continue;
            } else if (movedOutofInterface) {
                bugType = "MS_OOI_PKGPROTECT";
            } else if (couldBePackage && couldBeFinal && (isHashtable || isArray)) {
                bugType = "MS_FINAL_PKGPROTECT";
            } else if (couldBeFinal && !isHashtable && !isArray) {
                bugType = "MS_SHOULD_BE_FINAL";
                if (needsRefactoringToBeFinal.contains(f)) {
                    bugType = "MS_SHOULD_BE_REFACTORED_TO_BE_FINAL";
                }
                if (fieldName.equals(fieldName.toUpperCase()) || fieldSig.charAt(0) == 'L') {
                    priority = HIGH_PRIORITY;
                }
            } else if (couldBePackage) {
                bugType = isMutableCollection ? "MS_MUTABLE_COLLECTION_PKGPROTECT" : "MS_PKGPROTECT";
            } else if (isHashtable) {
                bugType = "MS_MUTABLE_HASHTABLE";
                if (!isFinal) {
                    priority = HIGH_PRIORITY;
                }
            } else if (isArray) {
                bugType = "MS_MUTABLE_ARRAY";
                if (fieldSig.indexOf('L') >= 0 || !isFinal) {
                    priority = HIGH_PRIORITY;
                }
            } else if (isMutableCollection) {
                bugType = "MS_MUTABLE_COLLECTION";
                priority = HIGH_PRIORITY;
            } else if (!isFinal) {
                bugType = "MS_CANNOT_BE_FINAL";
            } else {
                throw new IllegalStateException("impossible");
            }
            if (!isReadAnywhere) {
                priority = LOW_PRIORITY;
            }

            BugInstance bug = new BugInstance(this, bugType, priority).addClass(className).addField(f);
            SourceLineAnnotation firstPC = firstFieldUse.get(f);
            if (firstPC != null) {
                bug.addSourceLine(firstPC);
            }
            bugReporter.reportBug(bug);

        }
    }
}
