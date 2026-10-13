package dev.omalauncher.companion;

import java.text.Normalizer;
import java.util.Locale;

/** Profile and search rules shared by catalog reads and actions. No Android stubs in tests. */
final class AppPolicy {
    enum Kind { PERSONAL, WORK, CLONE, PRIVATE, UNKNOWN }
    static Kind kind(int sdk, boolean owner, String type) {
        if (owner) return Kind.PERSONAL;
        if (sdk < 35) return Kind.WORK;
        if ("android.os.usertype.profile.MANAGED".equals(type)) return Kind.WORK;
        if ("android.os.usertype.profile.CLONE".equals(type)) return Kind.CLONE;
        if ("android.os.usertype.profile.PRIVATE".equals(type)) return Kind.PRIVATE;
        return Kind.UNKNOWN;
    }
    static boolean visible(Kind kind, boolean quiet, boolean homeRole, boolean privateShown, boolean pendingLock) {
        return kind != Kind.UNKNOWN && (kind != Kind.PRIVATE || (homeRole && privateShown && !quiet && !pendingLock));
    }
    static boolean sameProfile(long expected, long actual) { return expected >= 0 && expected == actual; }
    static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
    static boolean matches(String label, String packageName, String query) {
        String term = fold(query.trim());
        return fold(label).contains(term) || fold(packageName).contains(term);
    }
}
