package overridableMethodCall;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Issue2414 {
    private final static List<String> aList = Collections.unmodifiableList(Arrays.asList("de", "us"));

    public Issue2414() {
        getList();
    }

    private void getList() {
        aList.stream()
                .forEach(test -> test.length());
    }
}