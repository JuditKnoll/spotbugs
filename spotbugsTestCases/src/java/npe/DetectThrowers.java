package npe;

public class DetectThrowers {

    private void oops(String s) {
        throw new RuntimeException(s);
    }

    private void oopsCaller(String s) {
        oops(s);
    }

    private void ooops(String s) throws Exception {
        throw new Exception(s);
    }

    private void ooopsCaller(String s) throws Exception {
        ooops(s);
    }

    public int runtimeExInnerThrown(Object x) {
        if (x == null)
            oops("x is null");
        return x.hashCode();
    }

    public int runtimeExInnerIndirectlyThrown(Object x) {
        if (x == null)
            oopsCaller("x is null");
        return x.hashCode();
    }

    public int exInnerThrown(Object x) throws Exception {
        if (x == null)
            ooops("x is null");
        return x.hashCode();
    }

    public int exInnerIndirectlyThrown(Object x) throws Exception {
        if (x == null)
            ooopsCaller("x is null");
        return x.hashCode();
    }

    public int exOuterThrown(Object x) throws Exception {
        if (x == null) {
            DetectThrowersHelper.ooops("x is null");
        }
        return x.hashCode();
    }

    public int exOuterIndirectlyThrown(Object x) throws Exception {
        if (x == null) {
            DetectThrowersHelper.ooopsCaller("x is null");
        }
        return x.hashCode();
    }

    public int runtimeExOuterThrown(Object x) throws Exception {
        if (x == null) {
            DetectThrowersHelper.oops("x is null");
        }
        return x.hashCode();
    }

    public int runtimeExOuterIndirectlyThrown(Object x) throws Exception {
        if (x == null) {
            DetectThrowersHelper.oopsCaller("x is null");
        }
        return x.hashCode();
    }
}
