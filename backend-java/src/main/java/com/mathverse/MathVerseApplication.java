package com.mathverse;

import com.mathverse.config.EnvFile;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the MathVerse API on port 8001. */
@SpringBootApplication
public class MathVerseApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MathVerseApplication.class);
        // Values in .env are used only when no environment variable of the same
        // name exists, like load_dotenv() in the Python backend.
        app.setDefaultProperties(EnvFile.read(".env"));
        app.run(args);
    }
}
