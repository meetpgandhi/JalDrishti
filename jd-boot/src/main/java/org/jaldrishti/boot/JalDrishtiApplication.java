package org.jaldrishti.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "org.jaldrishti")
public class JalDrishtiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JalDrishtiApplication.class, args);
    }
}
