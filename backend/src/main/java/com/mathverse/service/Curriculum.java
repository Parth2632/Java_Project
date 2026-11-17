package com.mathverse.service;

import java.util.List;

/** The six bosses and the maths topic each one teaches. Order = unlock order (Python: services/curriculum.py). */
public final class Curriculum {

    public record Boss(String id, String name, String topic, String label, String keyIdea) {
    }

    public static final List<Boss> BOSSES = List.of(
            new Boss("goblin", "Green Goblin", "linear_equations", "Algebra (Linear Equations)",
                    "Do the same operation to both sides; undo + or - first, then divide."),
            new Boss("doc-ock", "Doctor Octopus", "area_perimeter", "Geometry (Area & Perimeter)",
                    "Rectangle area = width x length; perimeter = 2 x (width + length); "
                            + "triangle area = half of base x height."),
            new Boss("mysterio", "Mysterio", "sequences_patterns", "Number Patterns & Sequences",
                    "Arithmetic sequences add the same amount each step; "
                            + "geometric sequences multiply by the same amount."),
            new Boss("venom", "Venom", "percentages", "Percentages",
                    "P% of a number = P / 100 x the number; a part as a percentage = part / whole x 100."),
            new Boss("lizard", "The Lizard", "exponents_radicals", "Exponents & Radicals",
                    "a^n means a multiplied by itself n times; same base multiplied: add the powers; "
                            + "a square root undoes squaring."),
            new Boss("kingpin", "Kingpin", "ratios_proportions", "Ratios & Proportions",
                    "Equal ratios scale both numbers by the same factor; "
                            + "to share in a ratio, find the size of one part first."));

    public static final List<String> BOSS_IDS = BOSSES.stream().map(Boss::id).toList();

    private Curriculum() {
    }

    /** The boss with this id, or null if there is none. */
    public static Boss getBoss(String bossId) {
        return BOSSES.stream().filter(b -> b.id().equals(bossId)).findFirst().orElse(null);
    }

    public static Boss bossForTopic(String topic) {
        return BOSSES.stream().filter(b -> b.topic().equals(topic)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown topic: " + topic));
    }

    /** A student at level N may play the first N bosses. */
    public static List<String> unlockedBosses(int level) {
        return BOSS_IDS.subList(0, Math.max(1, Math.min(level, BOSS_IDS.size())));
    }
}
