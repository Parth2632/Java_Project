package com.mathverse.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * One JSON rule that application.properties cannot express.
 *
 * By default the JSON library turns a number or true/false into text when a
 * text field expects it, so {"boss_id": 5} would be read as "5". The Python
 * backend rejects that with 422 ("Input should be a valid string"), and so does
 * this one. Text that holds a number ("5" for a number field) is still accepted,
 * as Pydantic accepts it.
 */
@Configuration
public class JsonConfig {

    @Bean
    JsonMapperBuilderCustomizer textMustBeText() {
        return builder -> builder.withCoercionConfig(LogicalType.Textual, config -> {
            config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }
}
