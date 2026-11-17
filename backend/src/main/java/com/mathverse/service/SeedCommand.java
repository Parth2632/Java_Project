package com.mathverse.service;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Database helper (Python: seed.py), run instead of the server:
 *
 *     mvnw spring-boot:run -Dspring-boot.run.arguments=--demo    create the demo accounts (safe to re-run)
 *     mvnw spring-boot:run -Dspring-boot.run.arguments=--reset   delete ALL data and recreate empty tables
 *
 * or, with the built jar: java -jar target/mathverse-backend-1.0.0.jar --demo
 */
@Component
public class SeedCommand {

    public static final List<String> FLAGS = List.of("--demo", "--reset");

    private final DemoData demoData;
    private final EntityManagerFactory database;

    public SeedCommand(DemoData demoData, EntityManagerFactory database) {
        this.demoData = demoData;
        this.database = database;
    }

    public static boolean isSeedRun(String[] args) {
        return List.of(args).stream().anyMatch(FLAGS::contains);
    }

    public void run(String[] args) {
        List<String> flags = List.of(args);
        if (flags.contains("--reset")) {
            // Drop every table and create them again, empty (Python: drop_all, create_all).
            database.getSchemaManager().drop(true);
            database.getSchemaManager().create(true);
            System.out.println("All tables dropped and recreated, empty.");
        }
        if (flags.contains("--demo")) {
            boolean created = demoData.createDemoAccounts();
            System.out.println(created ? "Demo accounts created." : "Demo accounts already exist; nothing changed.");
        }
    }
}
