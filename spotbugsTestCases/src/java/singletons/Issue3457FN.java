package singletons;

public class Issue3457FN {
    private static Issue3457FN instance;

    private Issue3457FN() {}

    public static Issue3457FN getInstance() {
        if (instance == null) {
            getUnreachableValue();
            instance = new Issue3457FN();
        }
        return instance;
    }

    private static int getUnreachableValue() {
        return 0;
    }
}