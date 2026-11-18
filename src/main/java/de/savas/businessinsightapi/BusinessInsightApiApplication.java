package de.savas.businessinsightapi;

import de.savas.businessinsightapi.application.insight.InsightExecutionProperties;
import de.savas.businessinsightapi.infrastructure.security.JwtProperties;
import de.savas.businessinsightapi.infrastructure.security.SecurityHardeningProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


@SpringBootApplication
@EnableConfigurationProperties({
    JwtProperties.class,
    InsightExecutionProperties.class,
    de.savas.businessinsightapi.application.insight.InsightSchedulerProperties.class,
    SecurityHardeningProperties.class
})
public class BusinessInsightApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessInsightApiApplication.class, args);
    }

}
