package npe;

public class DetectThrowersHelper {
    public static void ooops(String s) throws Exception {
        throw new Exception(s);
    }

    public static void ooopsCaller(String s) throws Exception {
        ooops(s);
    }

    public static void oops(String s) {
        throw new RuntimeException(s);
    }

    public static void oopsCaller(String s) {
        oops(s);
    }
}
