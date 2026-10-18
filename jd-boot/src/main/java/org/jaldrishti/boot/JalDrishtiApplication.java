package org.jaldrishti.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "org.jaldrishti")
@EnableScheduling
public class JalDrishtiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JalDrishtiApplication.class, args);
    }
}
