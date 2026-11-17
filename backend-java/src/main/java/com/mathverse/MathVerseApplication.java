package com.mathverse;

import com.mathverse.config.EnvFile;
import com.mathverse.service.SeedCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts the MathVerse API on port 8001. With --demo or --reset it runs the
 * database helper instead (see {@link SeedCommand}) and exits.
 */
@SpringBootApplication
public class MathVerseApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MathVerseApplication.class);
        // Values in .env are used only when no environment variable of the same
        // name exists, like load_dotenv() in the Python backend.
        app.setDefaultProperties(EnvFile.read(".env"));

        boolean seeding = SeedCommand.isSeedRun(args);
        if (seeding) {
            app.setWebApplicationType(WebApplicationType.NONE); // no web server for the helper
        }
        ConfigurableApplicationContext context = app.run(args);
        if (seeding) {
            context.getBean(SeedCommand.class).run(args);
            context.close();
        }
    }
}
