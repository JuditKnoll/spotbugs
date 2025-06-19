package singletons;

public class Issue3457TP {
    private static Issue3457TP instance;

    private Issue3457TP() {}

    public static Issue3457TP getInstance() {
        if (instance == null) {
            int value = 0;
            switch (value) {
                case 1:
                    break;
            }
            instance = new Issue3457TP();
        }
        return instance;
    }

    private static int getUnreachableValue() {
        return 0;
    }
}
