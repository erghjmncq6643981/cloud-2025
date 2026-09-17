package com.chandler.fcc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ChandlerFccApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChandlerFccApplication.class, args);
    }
}
