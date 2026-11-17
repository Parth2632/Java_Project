package com.mathverse;

import com.mathverse.config.EnvFile;
import com.mathverse.service.SeedCommand;
import com.mathverse.service.VerifyAttemptsCommand;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts the MathVerse API on port 8001. Two helper commands run instead of the
 * server and then exit:
 *   --demo / --reset            the database helper (see {@link SeedCommand})
 *   --verify-attempts '[...]'   the camera check's row check (see {@link VerifyAttemptsCommand})
 */
@SpringBootApplication
public class MathVerseApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MathVerseApplication.class);
        // Values in .env are used only when no environment variable of the same
        // name exists, like load_dotenv() in the Python backend.
        Map<String, Object> defaults = EnvFile.read(".env");

        boolean seeding = SeedCommand.isSeedRun(args);
        boolean verifying = VerifyAttemptsCommand.isVerifyRun(args);
        if (verifying) {
            // The caller reads this program's output as JSON, so nothing else may be printed.
            defaults.put("logging.level.root", "OFF");
        }
        app.setDefaultProperties(defaults);
        if (seeding || verifying) {
            app.setWebApplicationType(WebApplicationType.NONE); // no web server for the helpers
        }

        ConfigurableApplicationContext context = app.run(args);
        if (seeding) {
            context.getBean(SeedCommand.class).run(args);
        }
        if (verifying) {
            context.getBean(VerifyAttemptsCommand.class).run(args);
        }
        if (seeding || verifying) {
            context.close();
        }
    }
}
