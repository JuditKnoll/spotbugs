package ghIssues;

import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * @see <a href="https://github.com/spotbugs/spotbugs/issues/3769">GitHub issue 3769</a> and <a href="https://github.com/spotbugs/spotbugs/issues/3788">GitHub issue 3788</a>
 */
class Issue3769 {
    public void issue3769() {
        HashMap map = new HashMap();
        LinkedHashMap linkedHashMap = (LinkedHashMap) map.clone(); // BC_IMPOSSIBLE_DOWNCAST
    }

    public LinkedHashMap issue3788(HashMap map) {
        return (LinkedHashMap) map; // should report a BC_IMPOSSIBLE_DOWNCAST warning, but no warnings
    }
}
