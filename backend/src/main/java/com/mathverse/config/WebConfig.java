package com.mathverse.config;

import com.mathverse.security.AuthInterceptor;
import com.mathverse.security.CurrentUserResolver;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires in the sign-in check and the cross-origin (CORS) rules. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final CurrentUserResolver currentUserResolver;

    public WebConfig(AuthInterceptor authInterceptor, CurrentUserResolver currentUserResolver) {
        this.authInterceptor = authInterceptor;
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }

    /**
     * Lets the frontend (http://localhost:5173) call the API. A servlet filter is
     * used so that error responses (401, 404, ...) carry the CORS headers too, and
     * the browser lets the frontend read them.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(Settings settings) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(settings.corsOrigins());
        cors.setAllowedMethods(List.of("GET", "POST"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false); // the login token is a header, not a cookie
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
