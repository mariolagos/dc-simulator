package org.dcsim.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Canon helpers for our OWN generated strings:
 * - slug(...) for paths/URLs/files
 * - fileName(...), fileId(...)
 * - title(...) for report/chart headings
 * - key(...) for internal map keys (stable, ASCII, uppercase)
 * - snake(...), kebab(...), upperSnake(...)
 *
 * Examples
 * Rapport-/diagramrubriker
 * String heading = Canon.title(projectName + " – " + scenarioName); // "Project X – Scenario A"
 *
 * Säkra filnamn
 * String outName = Canon.fileName(projectName + "_" + scenarioName + "_pivots", ".xlsx", 80);
 * // t.ex. "project-x_scenario-a_pivots.xlsx"
 *
 ** Stabila interna nycklar (mappar/datasets)
 * String datasetKey = Canon.key(projectName + ":" + scenarioName); // "PROJECTXSCENARIOA"
 *
 * Katalog-/URL-segment
 * Path dir = base.resolve(Canon.slug(projectName)).resolve(Canon.slug(scenarioName));
 * // .../project-x/scenario-a
 *
 * Konstanter/enum-lika etiketter
 * String label = Canon.upperSnake("Node Voltage"); // "NODE_VOLTAGE"
 */
public final class Canon {
    private Canon() {}

    private static final Pattern NON_ASCII     = Pattern.compile("[^\\p{ASCII}]");
    private static final Pattern NON_ALNUM     = Pattern.compile("[^A-Za-z0-9]+");
    private static final Pattern MULTI_DASH    = Pattern.compile("-{2,}");
    private static final Pattern MULTI_USCORE  = Pattern.compile("_{2,}");
    private static final Pattern WS            = Pattern.compile("\\s+");
    private static final Pattern BAD_FILENAME  = Pattern.compile("[\\\\/:*?\"<>|]+");

    /** Basic ASCII fold (å→a, ö→o, etc.), then returns ASCII only. */
    public static String ascii(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        return NON_ASCII.matcher(n).replaceAll("");
    }

    /** Slug for file/URL segments: lowercase, ASCII, '-' separators. */
    public static String slug(String s) {
        String a = ascii(s).toLowerCase(Locale.ROOT);
        String r = NON_ALNUM.matcher(a).replaceAll("-");
        r = MULTI_DASH.matcher(r).replaceAll("-");
        r = trimSeparators(r, '-');
        return r;
    }

    /** Kebab case (preserve ASCII letters/digits, '-') */
    public static String kebab(String s) { return slug(s); }

    /** snake_case: lowercase, ASCII, '_' separators. */
    public static String snake(String s) {
        String a = ascii(s).toLowerCase(Locale.ROOT);
        String r = NON_ALNUM.matcher(a).replaceAll("_");
        r = MULTI_USCORE.matcher(r).replaceAll("_");
        r = trimSeparators(r, '_');
        return r;
    }

    /** UPPER_SNAKE for constants/keys. */
    public static String upperSnake(String s) {
        return snake(s).toUpperCase(Locale.ROOT);
    }

    /** Internal stable key: ASCII, uppercase, alnum only (drops separators). */
    public static String key(String s) {
        String a = ascii(s).toUpperCase(Locale.ROOT);
        String r = NON_ALNUM.matcher(a).replaceAll("");
        return r.trim();
    }

    /** Human heading (Title Case), collapses whitespace, keeps acronyms. */
    public static String title(String s) {
        if (s == null || s.isBlank()) return "";
        String base = ascii(s.trim());
        base = WS.matcher(base).replaceAll(" ");
        String[] parts = base.split(" ");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String w = parts[i];
            if (w.length() <= 2 || w.equals(w.toUpperCase(Locale.ROOT))) {
                // small words/acronyms → keep upper
                b.append(w.toUpperCase(Locale.ROOT));
            } else {
                b.append(Character.toUpperCase(w.charAt(0)))
                        .append(w.substring(1).toLowerCase(Locale.ROOT));
            }
            if (i + 1 < parts.length) b.append(' ');
        }
        return b.toString();
    }

    /** Safe filename stem (no extension): ASCII, removes illegal chars, compact. */
    public static String fileId(String s, int maxLen) {
        String slug = slug(s);
        String safe = BAD_FILENAME.matcher(slug).replaceAll("");
        if (safe.length() > maxLen) safe = safe.substring(0, maxLen);
        if (safe.isEmpty()) safe = "untitled";
        return safe;
    }

    /** Safe filename with extension ('.csv', '.xlsx', ...). */
    public static String fileName(String base, String ext, int maxStemLen) {
        String stem = fileId(base, Math.max(1, maxStemLen));
        String e = (ext == null) ? "" : ext.trim();
        if (!e.isEmpty() && !e.startsWith(".")) e = "." + e;
        return stem + e;
    }

    private static String trimSeparators(String s, char sep) {
        int i = 0, j = s.length();
        while (i < j && s.charAt(i) == sep) i++;
        while (j > i && s.charAt(j - 1) == sep) j--;
        return (i == 0 && j == s.length()) ? s : s.substring(i, j);
    }
}
