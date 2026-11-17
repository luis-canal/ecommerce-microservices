package br.edu.atitus.analyticsapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AnalyticsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApiApplication.class, args);
    }
}
