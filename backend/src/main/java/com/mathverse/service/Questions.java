package com.mathverse.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Deterministic question generator: six topics, three difficulty tiers each
 * (Python: services/questions.py).
 *
 * Every question is built from random numbers and its answer is computed, so the
 * answer is always right (QuestionsTest re-solves thousands of them from the
 * text alone). The server keeps correctIndex; the client only ever receives the
 * text and the four options.
 *
 * About half the questions are "story" questions told through the villain's own
 * world ({@link StoryQuestions}). They are built the same way: random numbers,
 * computed answer. If this generator ever fails, the game serves a fixed backup
 * question instead ({@link QuestionBank}).
 *
 * Each question also carries a plain hint written from the same numbers. That
 * hint is what Gwen says when no AI service is configured, and it is the only
 * maths the AI is allowed to rephrase when one is (see {@link Hints}).
 */
public final class Questions {

    public static final List<String> DIFFICULTIES = List.of("easy", "medium", "hard");

    /** The share of questions told as a villain story rather than as a plain sum. */
    public static final double STORY_SHARE = 0.5;

    private Questions() {
    }

    /** A new question for this boss and tier. Unknown boss or tier: IllegalArgumentException. */
    public static GeneratedQuestion generate(String bossId, String difficulty, Rng rng) {
        if (!GENERATORS.containsKey(bossId)) {
            throw new IllegalArgumentException("Unknown boss: " + bossId);
        }
        if (!DIFFICULTIES.contains(difficulty)) {
            throw new IllegalArgumentException("Unknown difficulty: " + difficulty);
        }
        GeneratedQuestion question;
        if (rng.random() < STORY_SHARE) {
            question = rng.choice(StoryQuestions.STORIES.get(bossId).get(difficulty)).make().apply(rng);
        } else {
            question = GENERATORS.get(bossId).get(difficulty).apply(rng);
        }
        return question.withDifficulty(difficulty);
    }

    public static GeneratedQuestion generate(String bossId, String difficulty) {
        return generate(bossId, difficulty, new Rng());
    }

    // ── shared helpers (also used by StoryQuestions) ─────────────────────────

    /** Four distinct options containing the answer exactly once, in random order. */
    static GeneratedQuestion build(String text, int answer, List<Integer> distractors, String hint, Rng rng) {
        List<Integer> options = new ArrayList<>();
        options.add(answer);
        for (int d : distractors) {
            if (!options.contains(d) && options.size() < 4) {
                options.add(d);
            }
        }
        int step = 1;
        while (options.size() < 4) { // distractors collided; pad with nearby numbers
            for (int candidate : new int[] {answer + step, answer - step}) {
                if (!options.contains(candidate) && options.size() < 4) {
                    options.add(candidate);
                }
            }
            step++;
        }
        rng.shuffle(options);
        List<String> shown = options.stream().map(String::valueOf).toList();
        return new GeneratedQuestion(text, shown, options.indexOf(answer), hint, "medium");
    }

    /** Whole-number power, e.g. pow(2, 3) = 8. */
    static int pow(int base, int exponent) {
        int result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= base;
        }
        return result;
    }

    private static String sign(boolean add) {
        return add ? "+" : "-";
    }

    // ── Green Goblin: linear equations ───────────────────────────────────────
    private static final String GOBLIN_INTRO =
            "SPIDER SENSE ALERT\n\nGreen Goblin has encrypted Oscorp's security system.\n\nSolve:\n";
    private static final String GOBLIN_OUTRO = "\n\nFind Y to disable the attack.";

    static GeneratedQuestion goblinEasy(Rng rng) {
        int y = rng.randint(2, 12);
        String equation;
        String hint;
        List<Integer> wrong;
        if (rng.coin()) { // one step: add or subtract
            int b = rng.randint(2, 15);
            boolean add = rng.coin();
            int c;
            if (add) {
                c = y + b;
            } else { // keep the right-hand side positive: choose it, then work out Y
                c = rng.randint(1, 10);
                y = c + b;
            }
            equation = "Y " + sign(add) + " " + b + " = " + c;
            hint = "Undo the " + sign(add) + " " + b + ": " + (add ? "subtract" : "add") + " " + b + " on both sides.";
            wrong = List.of(add ? c + b : c - b, y + 1, y - 1, y + 2);
        } else { // one step: divide
            int a = rng.randint(2, 9);
            equation = a + "Y = " + (a * y);
            hint = a + "Y means " + a + " times Y. Divide both sides by " + a + ".";
            wrong = List.of(a * y - a, a * y + a, y + 1, y - 1);
        }
        return build(GOBLIN_INTRO + equation + GOBLIN_OUTRO, y, wrong, hint, rng);
    }

    static GeneratedQuestion goblinMedium(Rng rng) {
        int a = rng.randint(2, 10);
        int y = rng.randint(2, 11);
        boolean add = rng.coin();
        int b = add ? rng.randint(1, 20) : rng.randint(1, Math.min(20, a * y - 1)); // right-hand side stays positive
        int c = add ? a * y + b : a * y - b;
        String equation = a + "Y " + sign(add) + " " + b + " = " + c;
        String hint = "Move the constant to the other side first: " + (add ? "subtract" : "add") + " " + b
                + " on both sides, then divide by " + a + ".";
        return build(GOBLIN_INTRO + equation + GOBLIN_OUTRO, y, List.of(y + 1, y - 1, y + 2, y * 2), hint, rng);
    }

    static GeneratedQuestion goblinHard(Rng rng) {
        int y = rng.randint(2, 12);
        String equation;
        String hint;
        List<Integer> wrong;
        if (rng.coin()) { // brackets
            int a = rng.randint(2, 6);
            int b = rng.randint(1, 9);
            boolean add = rng.coin();
            if (!add) {
                y = b + rng.randint(1, 9); // keep the bracket positive
            }
            int c = add ? a * (y + b) : a * (y - b);
            equation = a + "(Y " + sign(add) + " " + b + ") = " + c;
            hint = "Divide both sides by " + a + " first, then " + (add ? "subtract" : "add") + " " + b + ".";
            wrong = List.of(Math.floorDiv(c, a), y + b, Math.abs(y - b) + 1, y + 1);
        } else { // Y on both sides
            int cCoef = rng.randint(1, 5);
            int a = cCoef + rng.randint(1, 5);
            int b = rng.randint(1, 12);
            int d = (a - cCoef) * y + b;
            equation = a + "Y + " + b + " = " + cCoef + "Y + " + d;
            hint = "Collect the Y terms on one side: subtract " + cCoef + "Y from both sides, then subtract "
                    + b + ", then divide.";
            wrong = List.of(d - b, y + 1, y - 1, y * 2);
        }
        return build(GOBLIN_INTRO + equation + GOBLIN_OUTRO, y, wrong, hint, rng);
    }

    // ── Doctor Octopus: area and perimeter ───────────────────────────────────
    private static GeneratedQuestion rectangle(Rng rng, int w, int length) {
        if (rng.coin()) {
            int area = w * length;
            String text = "DOC OCK THREAT\n\nDoc Ock is building a mechanical lab.\n\n"
                    + "The rectangular lab is " + w + "m wide and " + length + "m long.\n\nWhat is the total Area?";
            String hint = "Area of a rectangle is width x length. Multiply " + w + " by " + length + ".";
            return build(text, area, List.of(w + length, 2 * (w + length), area + 10, area - 5), hint, rng);
        }
        int perimeter = 2 * (w + length);
        String text = "DOC OCK THREAT\n\nDoc Ock's tentacles need a wire around the lab.\n\n"
                + "The lab is " + w + "m wide and " + length + "m long.\n\nWhat is the Perimeter?";
        String hint = "Perimeter of a rectangle is 2 x (width + length). Add " + w + " and " + length
                + ", then double it.";
        return build(text, perimeter, List.of(w * length, w + length, perimeter + 2, perimeter - 4), hint, rng);
    }

    static GeneratedQuestion docOckEasy(Rng rng) {
        int w = rng.randint(2, 6);
        return rectangle(rng, w, w + rng.randint(1, 4));
    }

    static GeneratedQuestion docOckMedium(Rng rng) {
        int w = rng.randint(3, 12);
        return rectangle(rng, w, w + rng.randint(1, 10));
    }

    static GeneratedQuestion docOckHard(Rng rng) {
        if (rng.coin()) { // triangle
            int base = 2 * rng.randint(2, 8);
            int height = rng.randint(3, 12);
            int area = base * height / 2;
            String text = "DOC OCK THREAT\n\nDoc Ock is welding a triangular panel with a base of "
                    + base + "m and a height of " + height + "m.\n\nWhat is the Area of the panel?";
            String hint = "Area of a triangle is half of base x height. Multiply " + base + " by " + height
                    + ", then halve it.";
            return build(text, area, List.of(base * height, base + height, area + base, area - 2), hint, rng);
        }
        int w = rng.randint(3, 9); // work backwards from the perimeter
        int length = w + rng.randint(1, 8);
        int perimeter = 2 * (w + length);
        int area = w * length;
        String text = "DOC OCK THREAT\n\nDoc Ock's rectangular lab has a perimeter of "
                + perimeter + "m and a width of " + w + "m.\n\nWhat is the Area of the lab?";
        String hint = "Half the perimeter is width + length, so the length is " + perimeter + " / 2 - " + w
                + ". Then multiply width by length.";
        return build(text, area, List.of(perimeter * w, (perimeter - w) * w, length, perimeter / 2), hint, rng);
    }

    // ── Mysterio: sequences and patterns ─────────────────────────────────────
    private static GeneratedQuestion arithmetic(Rng rng, int start, int diff) {
        int[] seq = {start, start + diff, start + 2 * diff, start + 3 * diff};
        int next = seq[3] + diff;
        String text = "MYSTERIO ILLUSION\n\nThe drones are spawning in a sequence.\n\n"
                + "Observe the sequence: " + seq[0] + ", " + seq[1] + ", " + seq[2] + ", " + seq[3] + "...\n\n"
                + "What is the next number to stop the drones?";
        String hint = "Look for an addition pattern. What do you add to each number to get the next?";
        return build(text, next, List.of(next + 1, next - 2, next + diff, next * 2), hint, rng);
    }

    static GeneratedQuestion mysterioEasy(Rng rng) {
        int start = rng.randint(1, 5);
        return arithmetic(rng, start, rng.randint(2, 5));
    }

    static GeneratedQuestion mysterioMedium(Rng rng) {
        int start = rng.randint(2, 6);
        if (rng.coin()) {
            return arithmetic(rng, start, rng.randint(3, 12));
        }
        int ratio = rng.randint(2, 3);
        int[] seq = {start, start * ratio, start * pow(ratio, 2), start * pow(ratio, 3)};
        int next = seq[3] * ratio;
        String text = "MYSTERIO ILLUSION\n\nMysterio's mirror maze shifts in a pattern.\n\n"
                + "Observe the sequence: " + seq[0] + ", " + seq[1] + ", " + seq[2] + ", " + seq[3] + "...\n\n"
                + "What is the next number to break the illusion?";
        String hint = "Look for a multiplication pattern. What do you multiply each number by to get the next?";
        return build(text, next, List.of(next + ratio, next - ratio, next * 2, next + start), hint, rng);
    }

    static GeneratedQuestion mysterioHard(Rng rng) {
        int start = rng.randint(2, 9);
        int diff = rng.randint(3, 9);
        int n = rng.choice(8, 9, 10, 12);
        int answer = start + (n - 1) * diff;
        String text = "MYSTERIO ILLUSION\n\nMysterio hides his real position far along a pattern.\n\n"
                + "Observe the sequence: " + start + ", " + (start + diff) + ", " + (start + 2 * diff) + ", "
                + (start + 3 * diff) + "...\n\n"
                + "What is the " + n + "th number in the sequence?";
        String hint = "Each step adds " + diff + ". To reach the " + n + "th number from the 1st you take "
                + (n - 1) + " steps, not " + n + ".";
        return build(text, answer, List.of(start + n * diff, answer - diff, n * diff, answer + start), hint, rng);
    }

    // ── Venom: percentages ───────────────────────────────────────────────────
    private static GeneratedQuestion percentOf(Rng rng, int percent, int total) {
        int covered = percent * total / 100;
        String text = "SYMBIOTE OUTBREAK\n\nVenom's symbiote is spreading rapidly!\n\n"
                + "It has covered " + percent + "% of a " + total + " sq ft laboratory.\n\n"
                + "How many square feet are covered?";
        String hint = "To find " + percent + "% of " + total + ", divide " + percent + " by 100 and multiply by "
                + total + ".";
        return build(text, covered, List.of(covered + 10, covered - 20, total - covered, covered * 2), hint, rng);
    }

    static GeneratedQuestion venomEasy(Rng rng) {
        int percent = rng.choice(10, 25, 50);
        return percentOf(rng, percent, rng.randint(1, 10) * 20);
    }

    static GeneratedQuestion venomMedium(Rng rng) {
        int percent = rng.randint(1, 4) * 15;
        return percentOf(rng, percent, rng.randint(2, 6) * 100);
    }

    static GeneratedQuestion venomHard(Rng rng) {
        if (rng.coin()) { // percentage increase
            int base = rng.randint(2, 6) * 100;
            int percent = rng.choice(10, 20, 25, 40);
            int answer = base * (100 + percent) / 100;
            String text = "SYMBIOTE OUTBREAK\n\n"
                    + "The symbiote covered " + base + " sq ft, then grew by " + percent + "%.\n\n"
                    + "How many square feet does it cover now?";
            String hint = "Find " + percent + "% of " + base + " first, then add it on to the " + base + ".";
            List<Integer> wrong = List.of(base * percent / 100, base + percent, base * (100 - percent) / 100,
                    answer + 10);
            return build(text, answer, wrong, hint, rng);
        }
        int total = rng.choice(20, 40, 50, 200);
        int percent = rng.choice(20, 30, 40, 60, 70, 80);
        int part = total * percent / 100; // one number as a percentage of another
        String text = "SYMBIOTE OUTBREAK\n\n"
                + "Venom has infected " + part + " of the " + total + " lab computers.\n\n"
                + "What percentage of the computers is infected?";
        String hint = "Divide the part by the whole, " + part + " / " + total + ", then multiply by 100.";
        return build(text, percent, List.of(part, 100 - percent, percent + 10, percent - 10), hint, rng);
    }

    // ── The Lizard: exponents and radicals ───────────────────────────────────
    private static final String LIZARD_INTRO =
            "MUTATION THREAT\n\nThe Lizard is mixing a new serum formula.\n\nCalculate the dosage:\n";
    private static final String LIZARD_OUTRO = "\n\nFind the value to neutralize the serum!";

    static GeneratedQuestion lizardEasy(Rng rng) {
        int answer;
        String expression;
        String hint;
        List<Integer> wrong;
        if (rng.coin()) {
            int base = rng.randint(2, 9);
            answer = base * base;
            expression = base + "^2 = ?";
            hint = base + "^2 means " + base + " multiplied by itself, not " + base + " times 2.";
            wrong = List.of(base * 2, answer + base, answer - 1, base + 2);
        } else {
            int root = rng.randint(2, 10);
            answer = root;
            expression = "√" + (root * root) + " = ?";
            hint = "Which number multiplied by itself gives " + (root * root) + "?";
            wrong = List.of(root * root / 2, root + 1, root - 1, root * 2);
        }
        return build(LIZARD_INTRO + expression + LIZARD_OUTRO, answer, wrong, hint, rng);
    }

    static GeneratedQuestion lizardMedium(Rng rng) {
        int base = rng.randint(2, 5);
        int exp = rng.randint(2, 3);
        int root = rng.randint(2, 9);
        boolean add = rng.coin();
        int power = pow(base, exp);
        int answer = add ? power + root : power - root;
        String expression = base + "^" + exp + " " + sign(add) + " √" + (root * root) + " = ?";
        String hint = "Work out " + base + "^" + exp + " first (" + base + " multiplied by itself " + exp
                + " times), then the square root of " + (root * root) + ", then " + (add ? "add" : "subtract") + ".";
        return build(LIZARD_INTRO + expression + LIZARD_OUTRO, answer,
                List.of(answer + 2, answer - 2, answer + base, answer * 2), hint, rng);
    }

    static GeneratedQuestion lizardHard(Rng rng) {
        int base = rng.randint(2, 3);
        int answer;
        String expression;
        String hint;
        List<Integer> wrong;
        if (rng.coin()) { // product rule
            int m = rng.randint(1, 3);
            int n = rng.randint(2, 3);
            answer = pow(base, m + n);
            expression = base + "^" + m + " × " + base + "^" + n + " = ?";
            hint = "Same base, so add the powers: " + base + "^" + m + " × " + base + "^" + n + " = "
                    + base + "^(" + m + "+" + n + ").";
            wrong = List.of(pow(base, m * n), pow(base * base, m + n), base * (m + n), answer + base);
        } else { // power of a power
            int m = rng.randint(2, 3);
            answer = pow(base, 2 * m);
            expression = "(" + base + "^" + m + ")^2 = ?";
            hint = "A power of a power multiplies the powers: (" + base + "^" + m + ")^2 = " + base + "^(" + m
                    + "×2).";
            wrong = List.of(pow(base, m + 2), pow(base, m) * 2, base * m * 2, answer - base);
        }
        return build(LIZARD_INTRO + expression + LIZARD_OUTRO, answer, wrong, hint, rng);
    }

    // ── Kingpin: ratios and proportions ──────────────────────────────────────
    private static GeneratedQuestion proportion(Rng rng, int x1, int y1, int k) {
        int x2 = x1 * k;
        int y2 = y1 * k;
        String text = "UNDERWORLD TAKEOVER\n\nKingpin is transferring funds proportionally.\n\n"
                + "The ratio is " + x1 + " : " + y1 + " = " + x2 + " : X.\n\n"
                + "What is the value of X to intercept the transfer?";
        String hint = x1 + " multiplied by what gives " + x2 + "? Multiply " + y1 + " by that same number.";
        return build(text, y2, List.of(y2 + y1, y2 - x1, y2 + k, y2 * 2), hint, rng);
    }

    static GeneratedQuestion kingpinEasy(Rng rng) {
        int y1 = rng.randint(2, 6);
        return proportion(rng, 1, y1, rng.randint(2, 9));
    }

    static GeneratedQuestion kingpinMedium(Rng rng) {
        int x1 = rng.randint(2, 6);
        int y1 = rng.randint(3, 7);
        return proportion(rng, x1, y1, rng.randint(2, 5));
    }

    static GeneratedQuestion kingpinHard(Rng rng) {
        int[] parts = ratioParts(rng);
        int a = parts[0];
        int b = parts[1];
        int k = parts[2];
        int total = (a + b) * k;
        int answer = a * k;
        String text = "UNDERWORLD TAKEOVER\n\n"
                + "Kingpin splits $" + total + " between two gangs in the ratio " + a + " : " + b + ".\n\n"
                + "How many dollars does the first gang get?";
        String hint = "The ratio has " + a + " + " + b + " = " + (a + b) + " equal parts. Find the size of one part, "
                + "then take " + a + " of them.";
        return build(text, answer, List.of(b * k, total / a, total / (a + b), total - a), hint, rng);
    }

    /** Two different ratio numbers a and b from 2 to 7, and a scale k from 5 to 20. */
    static int[] ratioParts(Rng rng) {
        int a = rng.randint(2, 7);
        List<Integer> others = new ArrayList<>();
        for (int n = 2; n < 8; n++) {
            if (n != a) {
                others.add(n);
            }
        }
        int b = rng.choice(others);
        return new int[] {a, b, rng.randint(5, 20)};
    }

    private static final Map<String, Map<String, Function<Rng, GeneratedQuestion>>> GENERATORS = Map.of(
            "goblin", Map.of("easy", Questions::goblinEasy, "medium", Questions::goblinMedium,
                    "hard", Questions::goblinHard),
            "doc-ock", Map.of("easy", Questions::docOckEasy, "medium", Questions::docOckMedium,
                    "hard", Questions::docOckHard),
            "mysterio", Map.of("easy", Questions::mysterioEasy, "medium", Questions::mysterioMedium,
                    "hard", Questions::mysterioHard),
            "venom", Map.of("easy", Questions::venomEasy, "medium", Questions::venomMedium,
                    "hard", Questions::venomHard),
            "lizard", Map.of("easy", Questions::lizardEasy, "medium", Questions::lizardMedium,
                    "hard", Questions::lizardHard),
            "kingpin", Map.of("easy", Questions::kingpinEasy, "medium", Questions::kingpinMedium,
                    "hard", Questions::kingpinHard));
}
