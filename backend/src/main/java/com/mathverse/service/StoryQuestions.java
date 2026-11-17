package com.mathverse.service;

import static com.mathverse.service.Questions.build;
import static com.mathverse.service.Questions.pow;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Story questions: the same maths as {@link Questions}, told through each
 * villain's own world (Python: services/story_questions.py).
 *
 * They follow the same rules as every other question. The numbers are random,
 * the answer is computed from them, and the hint is written from the same
 * numbers. No AI service writes or checks any of this. Each villain has six
 * stories, two for each difficulty tier, and each tier practises the same skill
 * as the plain questions of that tier.
 */
public final class StoryQuestions {

    /** One story: its name (for test messages) and how to make it. */
    public record Story(String name, Function<Rng, GeneratedQuestion> make) {
    }

    private StoryQuestions() {
    }

    // ── Green Goblin: linear equations ───────────────────────────────────────
    static GeneratedQuestion goblinBombs(Rng rng) { // Y + b = c
        int y = rng.randint(2, 12);
        int b = rng.randint(2, 15);
        int c = y + b;
        String text = "PUMPKIN BOMB ALERT\n\n"
                + "Green Goblin loaded Y pumpkin bombs onto his glider, then grabbed " + b + " more.\nNow he carries "
                + c + ".\n\nHow many did he load first?";
        String hint = "Y + " + b + " = " + c + ". Undo the + " + b + ": subtract " + b + " from both sides.";
        return build(text, y, List.of(c + b, y + 1, y - 1, y + 2), hint, rng);
    }

    static GeneratedQuestion goblinGlider(Rng rng) { // aY = c
        int a = rng.randint(2, 9);
        int y = rng.randint(2, 12);
        String text = "GLIDER CHASE\n\n"
                + "The Goblin Glider flies Y metres every second.\nIn " + a + " seconds it covers " + (a * y)
                + " metres.\n\nHow many metres does it fly each second?";
        String hint = a + "Y = " + (a * y) + ". Divide both sides by " + a + ".";
        return build(text, y, List.of(a * y - a, a * y + a, y + 1, y - 1), hint, rng);
    }

    static GeneratedQuestion goblinVault(Rng rng) { // aY ± b = c
        int a = rng.randint(2, 10);
        int y = rng.randint(2, 11);
        boolean add = rng.coin();
        int b = add ? rng.randint(1, 20) : rng.randint(1, Math.min(20, a * y - 1));
        int c = add ? a * y + b : a * y - b;
        String text = "OSCORP VAULT\n\n"
                + "Norman Osborn locked the Oscorp vault with a secret number Y.\n"
                + "The clue says: " + a + " times Y, " + (add ? "plus" : "minus") + " " + b + ", equals " + c + ".\n\n"
                + "Find Y to open the vault.";
        String hint = a + "Y " + (add ? "+" : "-") + " " + b + " = " + c + ". " + (add ? "Subtract" : "Add") + " " + b
                + " on both sides first, then divide by " + a + ".";
        return build(text, y, List.of(y + 1, y - 1, y + 2, y * 2), hint, rng);
    }

    static GeneratedQuestion goblinSerum(Rng rng) { // aY - b = c
        int a = rng.randint(2, 9);
        int y = rng.randint(3, 12);
        int b = rng.randint(1, Math.min(20, a * y - 1));
        int c = a * y - b;
        String text = "GOBLIN SERUM\n\n"
                + "Each vial holds Y drops of Goblin serum.\n" + a + " vials were filled and " + b
                + " drops spilled, leaving " + c + " drops.\n\n"
                + "How many drops does one vial hold?";
        String hint = a + "Y - " + b + " = " + c + ". Add " + b + " to both sides first, then divide by " + a + ".";
        return build(text, y, List.of(y + 1, y - 1, y + 2, c / a), hint, rng);
    }

    static GeneratedQuestion goblinRace(Rng rng) { // aY + b = cY + d
        int y = rng.randint(2, 12);
        int c = rng.randint(1, 5);
        int a = c + rng.randint(1, 5);
        int b = rng.randint(1, 12);
        int d = (a - c) * y + b;
        String text = "GLIDER RACE\n\n"
                + "Spider-Man swings " + a + "Y + " + b + " metres. The Goblin Glider flies " + c + "Y + " + d
                + " metres.\n"
                + "They travel the same distance.\n\nFind Y.";
        String hint = a + "Y + " + b + " = " + c + "Y + " + d + ". Subtract " + c + "Y from both sides, then subtract "
                + b + ", then divide.";
        return build(text, y, List.of(d - b, y + 1, y - 1, y * 2), hint, rng);
    }

    static GeneratedQuestion goblinCrates(Rng rng) { // a(Y + b) = c
        int a = rng.randint(2, 6);
        int b = rng.randint(1, 9);
        int y = rng.randint(2, 12);
        int c = a * (y + b);
        String text = "PUMPKIN CRATES\n\n"
                + "Goblin packs " + a + " crates. Each crate holds Y pumpkin bombs and " + b + " smoke bombs.\n"
                + "There are " + c + " bombs in total.\n\nFind Y.";
        String hint = a + "(Y + " + b + ") = " + c + ". Divide both sides by " + a + " first, then subtract " + b + ".";
        return build(text, y, List.of(c / a, y + b + 1, y + 1, y - 1), hint, rng);
    }

    // ── Doctor Octopus: area and perimeter ───────────────────────────────────
    static GeneratedQuestion ockPlate(Rng rng) {
        int w = rng.randint(2, 6);
        int length = w + rng.randint(1, 4);
        int area = w * length;
        String text = "TENTACLE GRIP\n\n"
                + "Doc Ock's four metal arms hold a steel plate " + w + "m wide and " + length + "m long.\n\n"
                + "What is the Area of the plate?";
        String hint = "Area of a rectangle is width x length. Multiply " + w + " by " + length + ".";
        return build(text, area, List.of(w + length, 2 * (w + length), area + 10, area - 5), hint, rng);
    }

    static GeneratedQuestion ockFence(Rng rng) {
        int w = rng.randint(2, 6);
        int length = w + rng.randint(1, 4);
        int perimeter = 2 * (w + length);
        String text = "LASER FENCE\n\n"
                + "Doctor Octopus runs a laser fence around a floor " + w + "m wide and " + length + "m long.\n\n"
                + "What is the Perimeter of the floor?";
        String hint = "Perimeter of a rectangle is 2 x (width + length). Add " + w + " and " + length
                + ", then double it.";
        return build(text, perimeter, List.of(w * length, w + length, perimeter + 2, perimeter - 4), hint, rng);
    }

    static GeneratedQuestion ockShield(Rng rng) {
        int side = rng.randint(3, 15);
        String text = "FUSION REACTOR\n\n"
                + "Otto Octavius builds a square shield around his reactor. Each side is " + side + "m.\n\n"
                + "What is the Perimeter of the shield?";
        String hint = "A square has four equal sides. Add " + side + " four times.";
        return build(text, 4 * side, List.of(side * side, 2 * side, 4 * side + 4, 3 * side), hint, rng);
    }

    static GeneratedQuestion ockHatch(Rng rng) {
        int side = rng.randint(3, 12);
        String text = "ESCAPE HATCH\n\n"
                + "Doc Ock's arms cut a square hatch with sides of " + side + "m.\n\n"
                + "What is the Area of the hatch?";
        String hint = "Area of a square is side x side. Multiply " + side + " by " + side + ", not by 4.";
        return build(text, side * side, List.of(4 * side, 2 * side, side * side + side, side * side - side), hint, rng);
    }

    static GeneratedQuestion ockReach(Rng rng) {
        int base = 2 * rng.randint(2, 8);
        int height = rng.randint(3, 12);
        int area = base * height / 2;
        String text = "TENTACLE REACH\n\n"
                + "Doc Ock's arms sweep a triangular zone with a base of " + base + "m and a height of " + height
                + "m.\n\n"
                + "What is the Area of the zone?";
        String hint = "Area of a triangle is half of base x height. Multiply " + base + " by " + height
                + ", then halve it.";
        return build(text, area, List.of(base * height, base + height, area + base, area - 2), hint, rng);
    }

    static GeneratedQuestion ockReactorRoom(Rng rng) {
        int w = rng.randint(3, 9);
        int length = w + rng.randint(1, 8);
        int area = w * length;
        int perimeter = 2 * (w + length);
        String text = "REACTOR ROOM\n\n"
                + "The reactor room is a rectangle with an Area of " + area + " sq m and a width of " + w + "m.\n\n"
                + "What is its Perimeter?";
        String hint = "Find the length first: " + area + " divided by " + w + ". "
                + "Then add the width and the length, and double it.";
        return build(text, perimeter, List.of(length, w + length, 2 * area, perimeter + 2 * w), hint, rng);
    }

    // ── Mysterio: sequences and patterns ─────────────────────────────────────
    private static GeneratedQuestion nextIn(String title, String story, String ask, int[] seq, int next,
                                            List<Integer> wrong, String hint, Rng rng) {
        String text = title + "\n\n" + story + "\n"
                + "Observe the sequence: " + seq[0] + ", " + seq[1] + ", " + seq[2] + ", " + seq[3] + "...\n\n" + ask;
        return build(text, next, wrong, hint, rng);
    }

    private static final String ADD_HINT =
            "Look for an addition pattern. What do you add to each number to get the next?";

    private static int[] addSequence(int start, int diff, int length) {
        int[] seq = new int[length];
        for (int i = 0; i < length; i++) {
            seq[i] = start + diff * i;
        }
        return seq;
    }

    static GeneratedQuestion mysterioHelmet(Rng rng) {
        int start = rng.randint(1, 5);
        int diff = rng.randint(2, 5);
        int[] seq = addSequence(start, diff, 4);
        int next = seq[3] + diff;
        return nextIn("FISHBOWL TRICK", "Mysterio's fishbowl helmet flashes in a pattern.",
                "How many flashes come next?", seq, next, List.of(next + 1, next - 2, next + diff, next * 2),
                ADD_HINT, rng);
    }

    static GeneratedQuestion mysterioSmoke(Rng rng) {
        int start = rng.randint(1, 5);
        int diff = rng.randint(2, 5);
        int[] seq = addSequence(start, diff, 4);
        int next = seq[3] + diff;
        return nextIn("SMOKE SCREEN", "Mysterio releases more smoke clouds every round.",
                "How many clouds come next?", seq, next, List.of(next + 1, next - 2, next + diff, next * 2),
                ADD_HINT, rng);
    }

    static GeneratedQuestion mysterioArmy(Rng rng) {
        int start = rng.randint(2, 6);
        int ratio = rng.randint(2, 3);
        int[] seq = {start, start * ratio, start * pow(ratio, 2), start * pow(ratio, 3)};
        int next = seq[3] * ratio;
        String hint = "Look for a multiplication pattern. What do you multiply each number by to get the next?";
        return nextIn("ILLUSION ARMY", "Mysterio's fake Spider-Men multiply every minute.",
                "How many appear next?", seq, next, List.of(next + ratio, next - ratio, next * 2, next + start),
                hint, rng);
    }

    static GeneratedQuestion mysterioDrones(Rng rng) {
        int start = rng.randint(2, 6);
        int diff = rng.randint(3, 12);
        int[] seq = addSequence(start, diff, 4);
        int next = seq[3] + diff;
        return nextIn("DRONE WAVES", "Quentin Beck launches his illusion drones in waves.",
                "How many drones are in the next wave?", seq, next,
                List.of(next + 1, next - 2, next + diff, next * 2), ADD_HINT, rng);
    }

    static GeneratedQuestion mysterioTower(Rng rng) {
        int start = rng.randint(2, 9);
        int diff = rng.randint(3, 9);
        int n = rng.choice(8, 9, 10, 12);
        int[] seq = addSequence(start, diff, 4);
        int answer = start + (n - 1) * diff;
        String text = "HOLOGRAM TOWER\n\nBeck stacks hologram projectors floor by floor.\n"
                + "Observe the sequence: " + seq[0] + ", " + seq[1] + ", " + seq[2] + ", " + seq[3] + "...\n\n"
                + "What is the " + n + "th number in the sequence?";
        String hint = "Each step adds " + diff + ". To reach the " + n + "th number from the 1st you take "
                + (n - 1) + " steps, not " + n + ".";
        return build(text, answer, List.of(start + n * diff, answer - diff, n * diff, answer + start), hint, rng);
    }

    static GeneratedQuestion mysterioMirror(Rng rng) {
        int start = rng.randint(2, 9);
        int diff = rng.randint(3, 9);
        int[] seq = addSequence(start, diff, 5);
        String text = "MIRROR MAZE\n\nOne mirror in Mysterio's maze is hiding a number.\n"
                + "Observe the pattern: " + seq[0] + ", " + seq[1] + ", ?, " + seq[3] + ", " + seq[4] + "\n\n"
                + "Which number is hidden?";
        String hint = "Find what is added from the first number to the second. "
                + "Add it once more to the second number.";
        return build(text, seq[2], List.of(seq[2] + 1, seq[2] - 1, seq[0] + seq[1], seq[3] - 1), hint, rng);
    }

    // ── Venom: percentages ───────────────────────────────────────────────────
    private static String percentHint(int percent, int total) {
        return "To find " + percent + "% of " + total + ", divide " + percent + " by 100 and multiply by " + total + ".";
    }

    static GeneratedQuestion venomCells(Rng rng) {
        int percent = rng.choice(10, 25, 50);
        int total = rng.randint(1, 10) * 20;
        int part = percent * total / 100;
        String text = "SYMBIOTE BOND\n\n"
                + "The symbiote has bonded with " + percent + "% of Eddie Brock's " + total + " cells in a sample.\n\n"
                + "How many cells are bonded?";
        return build(text, part, List.of(part + 10, part - 20, total - part, part * 2), percentHint(percent, total), rng);
    }

    static GeneratedQuestion venomBell(Rng rng) {
        int percent = rng.choice(10, 25, 50);
        int total = rng.randint(1, 10) * 20;
        int part = percent * total / 100;
        String text = "BELL TOWER\n\n"
                + "Loud sound weakens Venom. A church bell removes " + percent + "% of his " + total
                + " strength points.\n\n"
                + "How many strength points does he lose?";
        return build(text, part, List.of(part + 10, part - 20, total - part, part * 2), percentHint(percent, total), rng);
    }

    static GeneratedQuestion venomWebFluid(Rng rng) {
        int percent = rng.randint(1, 4) * 15;
        int total = rng.randint(2, 6) * 100;
        int part = percent * total / 100;
        String text = "WEB FLUID\n\n"
                + "Spider-Man has " + total + " ml of web fluid. Venom's attack burns " + percent + "% of it.\n\n"
                + "How many ml are burned?";
        return build(text, part, List.of(part + 10, part - 20, total - part, part * 2), percentHint(percent, total), rng);
    }

    static GeneratedQuestion venomSonic(Rng rng) {
        int percent = rng.randint(1, 4) * 15;
        int total = rng.randint(2, 6) * 100;
        int part = percent * total / 100;
        String text = "SONIC BLAST\n\n"
                + "A sonic blast strips away " + percent + "% of the symbiote's " + total + " grams.\n\n"
                + "How many grams are stripped away?";
        return build(text, part, List.of(part + 10, part - 20, total - part, part * 2), percentHint(percent, total), rng);
    }

    static GeneratedQuestion venomGrowth(Rng rng) {
        int base = rng.randint(2, 6) * 100;
        int percent = rng.choice(10, 20, 25, 40);
        int answer = base * (100 + percent) / 100;
        String text = "SYMBIOTE GROWTH\n\n"
                + "Venom weighed " + base + " kg. After absorbing energy he grew by " + percent + "%.\n\n"
                + "How many kg does he weigh now?";
        String hint = "Find " + percent + "% of " + base + " first, then add it on to the " + base + ".";
        List<Integer> wrong = List.of(base * percent / 100, base + percent, base * (100 - percent) / 100, answer + 10);
        return build(text, answer, wrong, hint, rng);
    }

    static GeneratedQuestion venomPhotos(Rng rng) {
        int total = rng.choice(20, 40, 50, 200);
        int percent = rng.choice(20, 30, 40, 60, 70, 80);
        int part = total * percent / 100;
        String text = "NEWSROOM\n\n"
                + "Reporter Eddie Brock checked " + total + " photos and " + part + " were of Spider-Man.\n\n"
                + "What percentage were of Spider-Man?";
        String hint = "Divide the part by the whole, " + part + " / " + total + ", then multiply by 100.";
        return build(text, percent, List.of(part, 100 - percent, percent + 10, percent - 10), hint, rng);
    }

    // ── The Lizard: exponents and radicals ───────────────────────────────────
    static GeneratedQuestion lizardTiles(Rng rng) {
        int base = rng.randint(2, 9);
        int answer = base * base;
        String text = "SEWER LAIR\n\n"
                + "The Lizard's sewer lair has a square floor with " + base + " tiles along each side.\n\n"
                + "How many tiles is that?  " + base + "^2 = ?";
        String hint = base + "^2 means " + base + " multiplied by itself, not " + base + " times 2.";
        return build(text, answer, List.of(base * 2, answer + base, answer - 1, base + 2), hint, rng);
    }

    static GeneratedQuestion lizardCages(Rng rng) {
        int root = rng.randint(2, 10);
        String text = "LAB CAGES\n\n"
                + "Dr. Connors arranges " + (root * root) + " lizard cages in a perfect square.\n\n"
                + "How many cages are along one side?  √" + (root * root) + " = ?";
        String hint = "Which number multiplied by itself gives " + (root * root) + "?";
        return build(text, root, List.of(root * root / 2, root + 1, root - 1, root * 2), hint, rng);
    }

    static GeneratedQuestion lizardRegrowth(Rng rng) {
        int base = rng.randint(2, 5);
        int exp = rng.randint(2, 3);
        int root = rng.randint(2, 9);
        int answer = pow(base, exp) + root;
        String text = "REGROWTH SERUM\n\n"
                + "Dr. Connors' serum grows new cells and repairs old ones.\n\n"
                + "How many cells in total?  " + base + "^" + exp + " + √" + (root * root) + " = ?";
        String hint = "Work out " + base + "^" + exp + " first (" + base + " multiplied by itself " + exp
                + " times), then the square root of " + (root * root) + ", then add.";
        return build(text, answer, List.of(answer + 2, answer - 2, answer + base, answer * 2), hint, rng);
    }

    static GeneratedQuestion lizardScales(Rng rng) {
        int base = rng.randint(2, 5);
        int exp = rng.randint(2, 3);
        int root = rng.randint(2, 9);
        while (pow(base, exp) <= root) { // keep the answer positive
            root--;
        }
        int answer = pow(base, exp) - root;
        String text = "SHEDDING SCALES\n\n"
                + "The Lizard sheds scales, and a few grow back.\n\n"
                + "How many scales are gone?  " + base + "^" + exp + " - √" + (root * root) + " = ?";
        String hint = "Work out " + base + "^" + exp + " first (" + base + " multiplied by itself " + exp
                + " times), then the square root of " + (root * root) + ", then subtract.";
        return build(text, answer, List.of(answer + 2, answer - 2, answer + base, answer * 2), hint, rng);
    }

    static GeneratedQuestion lizardSplitting(Rng rng) {
        int base = rng.randint(2, 3);
        int m = rng.randint(1, 3);
        int n = rng.randint(2, 3);
        int answer = pow(base, m + n);
        String text = "SPLITTING CELLS\n\n"
                + "The Lizard's mutated cells keep splitting in stages.\n\n"
                + "How many cells are there?  " + base + "^" + m + " × " + base + "^" + n + " = ?";
        String hint = "Same base, so add the powers: " + base + "^" + m + " × " + base + "^" + n + " = "
                + base + "^(" + m + "+" + n + ").";
        return build(text, answer, List.of(pow(base, m * n), pow(base * base, m + n), base * (m + n), answer + base),
                hint, rng);
    }

    static GeneratedQuestion lizardWaves(Rng rng) {
        int base = rng.randint(2, 3);
        int m = rng.randint(2, 3);
        int answer = pow(base, 2 * m);
        String text = "MUTATION WAVE\n\n"
                + "Each mutation wave squares the number of reptile cells.\n\n"
                + "How many cells after one wave?  (" + base + "^" + m + ")^2 = ?";
        String hint = "A power of a power multiplies the powers: (" + base + "^" + m + ")^2 = " + base + "^(" + m
                + "×2).";
        return build(text, answer, List.of(pow(base, m + 2), pow(base, m) * 2, base * m * 2, answer - base), hint, rng);
    }

    // ── Kingpin: ratios and proportions ──────────────────────────────────────
    static GeneratedQuestion kingpinGuards(Rng rng) {
        int each = rng.randint(2, 6);
        int inside = rng.randint(2, 9);
        int answer = each * inside;
        String text = "FISK TOWER\n\n"
                + "For every 1 guard inside Fisk Tower, Kingpin posts " + each + " outside.\nThere are " + inside
                + " guards inside.\n\n"
                + "How many guards are outside?";
        String hint = "Each guard inside is matched by " + each + " outside. Multiply " + inside + " by " + each + ".";
        return build(text, answer, List.of(each + inside, answer + each, answer - 1, inside), hint, rng);
    }

    static GeneratedQuestion kingpinChips(Rng rng) {
        int each = rng.randint(2, 6);
        int gold = rng.randint(2, 9);
        int answer = each * gold;
        String text = "CASINO CHIPS\n\n"
                + "In Kingpin's casino, 1 gold chip is worth " + each + " silver chips.\n\n"
                + "How many silver chips are " + gold + " gold chips worth?";
        String hint = "Every gold chip gives " + each + " silver chips. Multiply " + gold + " by " + each + ".";
        return build(text, answer, List.of(each + gold, answer + each, answer - 1, gold), hint, rng);
    }

    static GeneratedQuestion kingpinTrucks(Rng rng) {
        int x1 = rng.randint(2, 6);
        int y1 = rng.randint(3, 7);
        int k = rng.randint(2, 5);
        int x2 = x1 * k;
        int answer = y1 * k;
        String text = "SMUGGLING ROUTE\n\n"
                + "Wilson Fisk's trucks carry " + x1 + " crates for every " + y1 + " barrels.\nA convoy carries "
                + x2 + " crates.\n\n"
                + "How many barrels does it carry?";
        String hint = x1 + " multiplied by what gives " + x2 + "? Multiply " + y1 + " by that same number.";
        return build(text, answer, List.of(answer + y1, answer - x1, x2, answer * 2), hint, rng);
    }

    static GeneratedQuestion kingpinBodyguards(Rng rng) {
        int x1 = rng.randint(2, 6);
        int y1 = rng.randint(3, 7);
        int k = rng.randint(2, 5);
        int x2 = x1 * k;
        int answer = y1 * k;
        String text = "FISK'S CREW\n\n"
                + "Kingpin hires " + x1 + " bodyguards for every " + y1 + " lawyers.\nHe has " + x2
                + " bodyguards.\n\n"
                + "How many lawyers does he have?";
        String hint = x1 + " multiplied by what gives " + x2 + "? Multiply " + y1 + " by that same number.";
        return build(text, answer, List.of(answer + y1, answer - x1, x2, answer * 2), hint, rng);
    }

    static GeneratedQuestion kingpinBlocks(Rng rng) {
        int[] parts = Questions.ratioParts(rng);
        int a = parts[0];
        int b = parts[1];
        int k = parts[2];
        int total = (a + b) * k;
        int answer = a * k;
        String text = "CITY TAKEOVER\n\n"
                + "Kingpin controls " + total + " city blocks. He shares them with Hammerhead in the ratio " + a
                + " : " + b + ".\n\n"
                + "How many blocks does Kingpin keep?";
        String hint = "The ratio has " + a + " + " + b + " = " + (a + b) + " equal parts. Find the size of one part, "
                + "then take " + a + " of them.";
        return build(text, answer, List.of(b * k, total / a, total / (a + b), total - a), hint, rng);
    }

    static GeneratedQuestion kingpinFortune(Rng rng) {
        int[] parts = Questions.ratioParts(rng);
        int a = parts[0];
        int b = parts[1];
        int k = parts[2];
        int total = (a + b) * k;
        int answer = b * k;
        String text = "FISK FORTUNE\n\n"
                + "Wilson Fisk and Vanessa share $" + total + " in the ratio " + a + " : " + b + ".\n\n"
                + "How many dollars does Vanessa get?";
        String hint = "The ratio has " + a + " + " + b + " = " + (a + b) + " equal parts. Find the size of one part. "
                + "Vanessa's share is the second number, so take " + b + " of them.";
        return build(text, answer, List.of(a * k, total / b, total / (a + b), total - b), hint, rng);
    }

    private static Story story(String name, Function<Rng, GeneratedQuestion> make) {
        return new Story(name, make);
    }

    /** Six stories per villain: two for each difficulty tier. */
    public static final Map<String, Map<String, List<Story>>> STORIES = Map.of(
            "goblin", Map.of(
                    "easy", List.of(story("goblinBombs", StoryQuestions::goblinBombs),
                            story("goblinGlider", StoryQuestions::goblinGlider)),
                    "medium", List.of(story("goblinVault", StoryQuestions::goblinVault),
                            story("goblinSerum", StoryQuestions::goblinSerum)),
                    "hard", List.of(story("goblinRace", StoryQuestions::goblinRace),
                            story("goblinCrates", StoryQuestions::goblinCrates))),
            "doc-ock", Map.of(
                    "easy", List.of(story("ockPlate", StoryQuestions::ockPlate),
                            story("ockFence", StoryQuestions::ockFence)),
                    "medium", List.of(story("ockShield", StoryQuestions::ockShield),
                            story("ockHatch", StoryQuestions::ockHatch)),
                    "hard", List.of(story("ockReach", StoryQuestions::ockReach),
                            story("ockReactorRoom", StoryQuestions::ockReactorRoom))),
            "mysterio", Map.of(
                    "easy", List.of(story("mysterioHelmet", StoryQuestions::mysterioHelmet),
                            story("mysterioSmoke", StoryQuestions::mysterioSmoke)),
                    "medium", List.of(story("mysterioArmy", StoryQuestions::mysterioArmy),
                            story("mysterioDrones", StoryQuestions::mysterioDrones)),
                    "hard", List.of(story("mysterioTower", StoryQuestions::mysterioTower),
                            story("mysterioMirror", StoryQuestions::mysterioMirror))),
            "venom", Map.of(
                    "easy", List.of(story("venomCells", StoryQuestions::venomCells),
                            story("venomBell", StoryQuestions::venomBell)),
                    "medium", List.of(story("venomWebFluid", StoryQuestions::venomWebFluid),
                            story("venomSonic", StoryQuestions::venomSonic)),
                    "hard", List.of(story("venomGrowth", StoryQuestions::venomGrowth),
                            story("venomPhotos", StoryQuestions::venomPhotos))),
            "lizard", Map.of(
                    "easy", List.of(story("lizardTiles", StoryQuestions::lizardTiles),
                            story("lizardCages", StoryQuestions::lizardCages)),
                    "medium", List.of(story("lizardRegrowth", StoryQuestions::lizardRegrowth),
                            story("lizardScales", StoryQuestions::lizardScales)),
                    "hard", List.of(story("lizardSplitting", StoryQuestions::lizardSplitting),
                            story("lizardWaves", StoryQuestions::lizardWaves))),
            "kingpin", Map.of(
                    "easy", List.of(story("kingpinGuards", StoryQuestions::kingpinGuards),
                            story("kingpinChips", StoryQuestions::kingpinChips)),
                    "medium", List.of(story("kingpinTrucks", StoryQuestions::kingpinTrucks),
                            story("kingpinBodyguards", StoryQuestions::kingpinBodyguards)),
                    "hard", List.of(story("kingpinBlocks", StoryQuestions::kingpinBlocks),
                            story("kingpinFortune", StoryQuestions::kingpinFortune))));
}
