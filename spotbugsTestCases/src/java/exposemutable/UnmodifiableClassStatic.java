package exposemutable;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UnmodifiableClassStatic {

    private static final List<String> emptyList;
    private static final Set<String> emptySet;
    private static final Map<String, String> emptyMap;

    private static final List<String> copyOfList;
    private static final Set<String> copyOfSet;
    private static final Map<String, String> copyOfMap;

    private static final List<String> listWithTernary;
    private static final Set<String> setWithTernary;
    private static final Map<String, String> mapWithTernary;

    private static final List<String> listWithIf;
    private static final Set<String> setWithIf;
    private static final Map<String, String> mapWithIf;

    private static final List<String> listWithTernary2;
    private static final Set<String> setWithTernary2;
    private static final Map<String, String> mapWithTernary2;

    private static final List<String> listWithIf2;
    private static final Set<String> setWithIf2;
    private static final Map<String, String> mapWithIf2;

    private static final List<String> listWithTernary3;
    private static final Set<String> setWithTernary3;
    private static final Map<String, String> mapWithTernary3;

    private static final List<String> listWithIf3;
    private static final Set<String> setWithIf3;
    private static final Map<String, String> mapWithIf3;

    static {
        emptyList = Collections.emptyList();
        emptySet = Collections.emptySet();
        emptyMap = Collections.emptyMap();

        List<String> list = List.of("a", "b", "c");
        Set<String> set = Set.of("a", "b", "c");
        Map<String, String> map = Map.of("a", "aa", "b", "bb", "c", "cc");

        copyOfList = List.copyOf(list);
        copyOfSet = Set.copyOf(set);
        copyOfMap = Map.copyOf(map);

        listWithTernary = list != null ? List.copyOf(list) : Collections.emptyList();
        setWithTernary = set != null ? Set.copyOf(set) : Collections.emptySet();
        mapWithTernary = map != null ? Map.copyOf(map) : Collections.emptyMap();

        listWithTernary2 = list != null ? List.copyOf(list) : new ArrayList<>();
        setWithTernary2 = set != null ? Set.copyOf(set) : new HashSet<>();
        mapWithTernary2 = map != null ? Map.copyOf(map) : new HashMap<>();

        listWithTernary3 = list != null ? new ArrayList<>(list) : Collections.emptyList();
        setWithTernary3 = set != null ? new HashSet<>(set) : Collections.emptySet();
        mapWithTernary3 = map != null ? new HashMap<>(map) : Collections.emptyMap();

        if (list != null) {
            listWithIf = List.copyOf(list);
        } else {
            listWithIf = Collections.emptyList();
        }

        if (set != null) {
            setWithIf = Set.copyOf(set);
        } else {
            setWithIf = Collections.emptySet();
        }

        if (map != null) {
            mapWithIf = Map.copyOf(map);
        } else {
            mapWithIf = Collections.emptyMap();
        }

        if (list != null) {
            listWithIf2 = List.copyOf(list);
        } else {
            listWithIf2 = new ArrayList<>();
        }

        if (set != null) {
            setWithIf2 = Set.copyOf(set);
        } else {
            setWithIf2 = new HashSet<>();
        }

        if (map != null) {
            mapWithIf2 = Map.copyOf(map);
        } else {
            mapWithIf2 = new HashMap<>();
        }

        if (list != null) {
            listWithIf3 = new ArrayList<>(list);
        } else {
            listWithIf3 = Collections.emptyList();
        }

        if (set != null) {
            setWithIf3 = new HashSet<>(set);
        } else {
            setWithIf3 = Collections.emptySet();
        }

        if (map != null) {
            mapWithIf3 = new HashMap<>(map);
        } else {
            mapWithIf3 = Collections.emptyMap();
        }
    }

    public List<String> getEmptyList() {
        return emptyList;
    }

    public Set<String> getEmptySet() {
        return emptySet;
    }

    public Map<String, String> getEmptyMap() {
        return emptyMap;
    }

    public List<String> getCopyOfList() {
        return copyOfList;
    }

    public Set<String> getCopyOfSet() {
        return copyOfSet;
    }

    public Map<String, String> getCopyOfMap() {
        return copyOfMap;
    }

    public List<String> getListWithTernary() {
        return listWithTernary;
    }

    public Set<String> getSetWithTernary() {
        return setWithTernary;
    }

    public Map<String, String> getMapWithTernary() {
        return mapWithTernary;
    }

    public List<String> getListWithTernary2() {
        return listWithTernary2;
    }

    public Set<String> getSetWithTernary2() {
        return setWithTernary2;
    }

    public Map<String, String> getMapWithTernary2() {
        return mapWithTernary2;
    }

    public List<String> getListWithTernary3() {
        return listWithTernary3;
    }

    public Set<String> getSetWithTernary3() {
        return setWithTernary3;
    }

    public Map<String, String> getMapWithTernary3() {
        return mapWithTernary3;
    }

    public List<String> getListWithIf() {
        return listWithIf;
    }

    public Set<String> getSetWithIf() {
        return setWithIf;
    }

    public Map<String, String> getMapWithIf() {
        return mapWithIf;
    }

    public List<String> getListWithIf2() {
        return listWithIf2;
    }

    public Set<String> getSetWithIf2() {
        return setWithIf2;
    }

    public Map<String, String> getMapWithIf2() {
        return mapWithIf2;
    }

    public List<String> getListWithIf3() {
        return listWithIf3;
    }

    public Set<String> getSetWithIf3() {
        return setWithIf3;
    }

    public Map<String, String> getMapWithIf3() {
        return mapWithIf3;
    }
}
