package overridableMethodCall;

import java.util.List;
import java.util.stream.Collectors;

public class Issue2414 {
    private final static List<String> aList = List.of("de", "us");

    public Issue2414() {
        getList();
    }

    private void getList() {
        aList.stream()
                .forEach(test -> test.length());
    }
}