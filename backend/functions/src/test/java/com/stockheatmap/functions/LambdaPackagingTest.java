package com.stockheatmap.functions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.function.adapter.aws.FunctionInvoker;

/**
 * Lambda loads FunctionInvoker as the handler; it implements RequestStreamHandler from
 * aws-lambda-java-core, which the Spring Cloud Function adapter only declares as optional.
 * Without it on the runtime classpath the function fails during initialization.
 */
class LambdaPackagingTest {

  @Test
  void handlerInterfaceIsOnTheRuntimeClasspath() throws Exception {
    Class<?> handlerInterface = Class.forName("com.amazonaws.services.lambda.runtime.RequestStreamHandler");

    assertThat(handlerInterface.isAssignableFrom(FunctionInvoker.class)).isTrue();
  }
}
