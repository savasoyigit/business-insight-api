package de.savas.businessinsightapi.infrastructure.config;

import de.savas.businessinsightapi.infrastructure.security.OrganizationAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final OrganizationAccessInterceptor organizationAccessInterceptor;

    public WebConfig(OrganizationAccessInterceptor organizationAccessInterceptor) {
        this.organizationAccessInterceptor = organizationAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(organizationAccessInterceptor).addPathPatterns("/api/insight/**");
    }
}
