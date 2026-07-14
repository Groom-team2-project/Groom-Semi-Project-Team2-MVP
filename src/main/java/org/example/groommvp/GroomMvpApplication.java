package org.example.groommvp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class GroomMvpApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroomMvpApplication.class, args);
    }

}
