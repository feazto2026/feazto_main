package com.codewild.food;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FoodPlatformApplication {
  public static void main(String[] args) {
    SpringApplication.run(FoodPlatformApplication.class, args);
  }
}
