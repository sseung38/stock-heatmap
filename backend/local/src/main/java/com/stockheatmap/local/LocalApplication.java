package com.stockheatmap.local;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Runs the backend on your machine. Without options it serves the mock API on :8787 for the
 * React dev server. With --refresh=<job> or --smoke it runs that command once and exits.
 */
@SpringBootApplication
public class LocalApplication {

  public static void main(String[] args) {
    ConfigurableApplicationContext context = SpringApplication.run(LocalApplication.class, args);
    boolean oneShot = Arrays.stream(args).anyMatch(a -> a.startsWith("--refresh") || a.equals("--smoke"));
    if (oneShot) {
      System.exit(SpringApplication.exit(context));
    }
  }
}
