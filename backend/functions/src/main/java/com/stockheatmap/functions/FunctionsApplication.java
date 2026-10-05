package com.stockheatmap.functions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Spring Boot application behind all six Lambdas. Lambda calls Spring Cloud Function's
 * FunctionInvoker, which starts this application (named by the MAIN_CLASS environment variable)
 * and runs the bean named by spring_cloud_function_definition, e.g. "refreshStocks".
 */
@SpringBootApplication
public class FunctionsApplication {

  public static void main(String[] args) {
    SpringApplication.run(FunctionsApplication.class, args);
  }
}
