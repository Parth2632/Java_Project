package com.mathverse.service;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;

/**
 * Random numbers for the question generator, with the same helpers the Python
 * code uses (random.randint, random.choice, ...). A seed makes the sequence
 * repeatable, which the tests use. The numbers differ from Python's for the same
 * seed, so the two backends produce different questions; the rules are the same.
 */
public final class Rng {

    private final Random random;

    public Rng() {
        random = new Random();
    }

    public Rng(long seed) {
        // java.util.Random gives almost the same first numbers for neighbouring
        // seeds (0, 1, 2, ...), so the seed is mixed first. Same seed, same sequence.
        random = new Random(new SplittableRandom(seed).nextLong());
    }

    /** A whole number from low to high, both included (Python: randint). */
    public int randint(int low, int high) {
        return low + random.nextInt(high - low + 1);
    }

    /** A number from 0 (included) to 1 (excluded) (Python: random). */
    public double random() {
        return random.nextDouble();
    }

    /** True or false, evenly. */
    public boolean coin() {
        return random() > 0.5;
    }

    /** One item of the list, picked at random (Python: choice). */
    public <T> T choice(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    public int choice(int... items) {
        return items[random.nextInt(items.length)];
    }

    /** Puts the list in random order, in place (Python: shuffle). */
    public void shuffle(List<?> items) {
        Collections.shuffle(items, random);
    }
}
