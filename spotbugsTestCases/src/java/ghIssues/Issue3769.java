package ghIssues;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;

/**
 * @see <a href="https://github.com/spotbugs/spotbugs/issues/3769">GitHub issue 3769</a> and <a href="https://github.com/spotbugs/spotbugs/issues/3788">GitHub issue 3788</a>
 */
class Issue3769 {
    public void issue3769() {
        HashMap map = new HashMap();
        LinkedHashMap linkedHashMap = (LinkedHashMap) map.clone(); // BC_IMPOSSIBLE_DOWNCAST
    }

    public LinkedHashMap issue3788(HashMap map) {
        return (LinkedHashMap) map; // BC_IMPOSSIBLE_DOWNCAST
    }

    public void issue3752() {
        ArrayList<String> a = new ArrayList<String>();
        String s[] = (String[]) a.stream().toArray(); // BC_IMPOSSIBLE_DOWNCAST_OF_TOARRAY
        String s2[] = (String[]) a.toArray(); // BC_IMPOSSIBLE_DOWNCAST_OF_TOARRAY
    }
}
