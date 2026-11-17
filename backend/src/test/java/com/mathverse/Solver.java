package com.mathverse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works the answer out again from the question text alone, with its own
 * arithmetic (Python: tests/test_questions.py::solve). The tests compare this
 * with the answer the generator stored. Every question type, stories included,
 * must have a case here.
 */
public final class Solver {

    private Solver() {
    }

    /** The groups of the first match of regex in text, or null if it does not match. */
    private static int[] find(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) {
            return null;
        }
        int[] groups = new int[m.groupCount()];
        for (int i = 0; i < groups.length; i++) {
            String g = m.group(i + 1);
            groups[i] = g.matches("\\d+") ? Integer.parseInt(g) : (g.equals("+") || g.equals("plus") ? 1 : -1);
        }
        return groups;
    }

    private static int divide(int top, int bottom) {
        assertThat(top % bottom).as("%d does not divide %d exactly", bottom, top).isZero();
        return top / bottom;
    }

    private static int isqrt(int square) {
        int root = (int) Math.round(Math.sqrt(square));
        assertThat(root * root).isEqualTo(square);
        return root;
    }

    private static int pow(int base, int exponent) {
        int result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= base;
        }
        return result;
    }

    // Signs are read as +1 (for "+" or "plus") and -1 (for "-" or "minus").
    public static int solve(String bossId, String text) {
        int[] g;
        switch (bossId) {
            case "goblin" -> {
                if ((g = find(text, "grabbed (\\d+) more\\.\\nNow he carries (\\d+)")) != null) { // Y + b = c
                    return g[1] - g[0];
                }
                if ((g = find(text, "In (\\d+) seconds it covers (\\d+) metres")) != null) { // aY = c
                    return divide(g[1], g[0]);
                }
                if ((g = find(text, "(\\d+) times Y, (plus|minus) (\\d+), equals (\\d+)")) != null) { // aY ± b = c
                    return divide(g[1] > 0 ? g[3] - g[2] : g[3] + g[2], g[0]);
                }
                if ((g = find(text, "(\\d+) vials were filled and (\\d+) drops spilled, leaving (\\d+) drops")) != null) {
                    return divide(g[2] + g[1], g[0]); // aY - b = c
                }
                if ((g = find(text, "packs (\\d+) crates\\. Each crate holds Y pumpkin bombs and (\\d+) smoke bombs\\.\\n"
                        + "There are (\\d+) bombs")) != null) { // a(Y + b) = c
                    return divide(g[2], g[0]) - g[1];
                }
                if ((g = find(text, "swings (\\d+)Y \\+ (\\d+) metres\\. The Goblin Glider flies (\\d+)Y \\+ (\\d+) metres"))
                        != null) { // aY + b = cY + d, told as a race
                    return divide(g[3] - g[1], g[0] - g[2]);
                }
                if ((g = find(text, "(\\d+)Y \\+ (\\d+) = (\\d+)Y \\+ (\\d+)")) != null) { // aY + b = cY + d
                    return divide(g[3] - g[1], g[0] - g[2]);
                }
                if ((g = find(text, "(\\d+)\\(Y ([+-]) (\\d+)\\) = (\\d+)")) != null) { // a(Y ± b) = c
                    int inner = divide(g[3], g[0]);
                    return g[1] > 0 ? inner - g[2] : inner + g[2];
                }
                if ((g = find(text, "(\\d+)Y ([+-]) (\\d+) = (\\d+)")) != null) { // aY ± b = c
                    return divide(g[1] > 0 ? g[3] - g[2] : g[3] + g[2], g[0]);
                }
                if ((g = find(text, "(\\d+)Y = (\\d+)")) != null) { // aY = c
                    return divide(g[1], g[0]);
                }
                g = find(text, "\\nY ([+-]) (\\d+) = (\\d+)"); // Y ± b = c
                assertThat(g).as(text).isNotNull();
                return g[0] > 0 ? g[2] - g[1] : g[2] + g[1];
            }
            case "doc-ock" -> {
                if ((g = find(text, "base of (\\d+)m and a height of (\\d+)m")) != null) {
                    return divide(g[0] * g[1], 2);
                }
                if ((g = find(text, "Each side is (\\d+)m")) != null) { // square, perimeter
                    return 4 * g[0];
                }
                if ((g = find(text, "with sides of (\\d+)m")) != null) { // square, area
                    return g[0] * g[0];
                }
                if ((g = find(text, "Area of (\\d+) sq m and a width of (\\d+)m")) != null) { // perimeter from area
                    return 2 * (g[1] + divide(g[0], g[1]));
                }
                if ((g = find(text, "perimeter of (\\d+)m and a width of (\\d+)m")) != null) {
                    return g[1] * (divide(g[0], 2) - g[1]);
                }
                g = find(text, "(\\d+)m wide and (\\d+)m long");
                assertThat(g).as(text).isNotNull();
                return text.contains("Area") ? g[0] * g[1] : 2 * (g[0] + g[1]);
            }
            case "mysterio" -> {
                if ((g = find(text, "pattern: (\\d+), (\\d+), \\?, (\\d+), (\\d+)")) != null) { // hidden middle number
                    int a = g[0];
                    int b = g[1];
                    int d = g[2];
                    int e = g[3];
                    assertThat(e - d == b - a && d - b == 2 * (b - a)).as(text).isTrue();
                    return b + (b - a);
                }
                int[] s = find(text, "sequence: (\\d+), (\\d+), (\\d+), (\\d+)");
                assertThat(s).as(text).isNotNull();
                boolean arithmetic = s[1] - s[0] == s[2] - s[1] && s[2] - s[1] == s[3] - s[2];
                if ((g = find(text, "What is the (\\d+)th number")) != null) {
                    assertThat(arithmetic).isTrue();
                    return s[0] + (g[0] - 1) * (s[1] - s[0]);
                }
                if (arithmetic) {
                    return s[3] + (s[1] - s[0]);
                }
                assertThat(s[1] * s[1] == s[0] * s[2] && s[2] * s[2] == s[1] * s[3]).as(text).isTrue();
                return s[3] * s[1] / s[0];
            }
            case "venom" -> {
                if ((g = find(text, "covered (\\d+) sq ft, then grew by (\\d+)%")) != null) {
                    return g[0] + divide(g[0] * g[1], 100);
                }
                if ((g = find(text, "infected (\\d+) of the (\\d+) lab computers")) != null) {
                    return divide(g[0] * 100, g[1]);
                }
                if ((g = find(text, "weighed (\\d+) kg\\. After absorbing energy he grew by (\\d+)%")) != null) {
                    return g[0] + divide(g[0] * g[1], 100);
                }
                if ((g = find(text, "checked (\\d+) photos and (\\d+) were")) != null) {
                    return divide(g[1] * 100, g[0]);
                }
                if ((g = find(text, "has (\\d+) ml of web fluid\\. Venom's attack burns (\\d+)%")) != null) {
                    return divide(g[1] * g[0], 100);
                }
                if ((g = find(text, "(\\d+)% of (?:Eddie Brock's|his|the symbiote's) (\\d+) ")) != null) {
                    return divide(g[0] * g[1], 100);
                }
                g = find(text, "(\\d+)% of a (\\d+) sq ft");
                assertThat(g).as(text).isNotNull();
                return divide(g[0] * g[1], 100);
            }
            case "lizard" -> {
                if ((g = find(text, "\\((\\d+)\\^(\\d+)\\)\\^(\\d+) = \\?")) != null) {
                    return pow(pow(g[0], g[1]), g[2]);
                }
                if ((g = find(text, "(\\d+)\\^(\\d+) × (\\d+)\\^(\\d+) = \\?")) != null) {
                    return pow(g[0], g[1]) * pow(g[2], g[3]);
                }
                if ((g = find(text, "(\\d+)\\^(\\d+) ([+-]) √(\\d+) = \\?")) != null) {
                    int root = isqrt(g[3]);
                    int power = pow(g[0], g[1]);
                    return g[2] > 0 ? power + root : power - root;
                }
                if ((g = find(text, "(\\d+)\\^(\\d+) = \\?")) != null) {
                    return pow(g[0], g[1]);
                }
                g = find(text, "√(\\d+) = \\?");
                assertThat(g).as(text).isNotNull();
                return isqrt(g[0]);
            }
            case "kingpin" -> {
                if ((g = find(text, "Kingpin posts (\\d+) outside\\.\\nThere are (\\d+) guards inside")) != null) {
                    return g[0] * g[1];
                }
                if ((g = find(text, "1 gold chip is worth (\\d+) silver chips\\.\\n\\nHow many silver chips are (\\d+) gold"))
                        != null) {
                    return g[0] * g[1];
                }
                if ((g = find(text, "(\\d+) (?:crates|bodyguards) for every (\\d+) (?:barrels|lawyers)\\.\\n"
                        + "(?:A convoy carries|He has) (\\d+) ")) != null) {
                    return divide(g[1] * g[2], g[0]);
                }
                if ((g = find(text, "controls (\\d+) city blocks\\. He shares them with Hammerhead in the ratio (\\d+) : (\\d+)"))
                        != null) {
                    return divide(g[0], g[1] + g[2]) * g[1]; // Kingpin's share is the first number
                }
                if ((g = find(text, "share \\$(\\d+) in the ratio (\\d+) : (\\d+)\\.\\n\\nHow many dollars does Vanessa"))
                        != null) {
                    return divide(g[0], g[1] + g[2]) * g[2]; // Vanessa's share is the second number
                }
                if ((g = find(text, "splits \\$(\\d+) between two gangs in the ratio (\\d+) : (\\d+)")) != null) {
                    return divide(g[0], g[1] + g[2]) * g[1];
                }
                g = find(text, "ratio is (\\d+) : (\\d+) = (\\d+) : X");
                assertThat(g).as(text).isNotNull();
                return divide(g[1] * g[2], g[0]);
            }
            default -> throw new AssertionError(bossId);
        }
    }
}
