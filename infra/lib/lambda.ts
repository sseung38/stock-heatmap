import { Duration, RemovalPolicy } from "aws-cdk-lib";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as logs from "aws-cdk-lib/aws-logs";
import type { Construct } from "constructs";

export const HANDLER = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest";
export const MAIN_CLASS = "com.stockheatmap.functions.FunctionsApplication";

export interface SpringFunctionProps {
  /** Suffix of the Lambda name: stock-heatmap-<name>. */
  name: string;
  /** Spring bean to run (see LambdaFunctions.java). */
  functionBean: string;
  timeout: Duration;
  /** Path to backend/functions/build/distributions/functions-lambda.zip. */
  codePath: string;
  environment?: Record<string, string>;
  memoryMb?: number;
}

export interface SpringFunction {
  fn: lambda.Function;
  /** "live" alias on the latest published version; SnapStart only applies to published versions. */
  alias: lambda.Alias;
}

/**
 * One Lambda running one Spring Cloud Function bean out of the shared backend zip.
 * Java 21 on Graviton (arm64, cheaper), SnapStart for fast cold starts, JSON logs kept 14 days,
 * and no automatic retries (the next scheduled run is the retry).
 */
export function makeSpringFunction(scope: Construct, id: string, props: SpringFunctionProps): SpringFunction {
  const functionName = `stock-heatmap-${props.name}`;
  const logGroup = new logs.LogGroup(scope, `${id}Logs`, {
    logGroupName: `/aws/lambda/${functionName}`,
    retention: logs.RetentionDays.TWO_WEEKS,
    removalPolicy: RemovalPolicy.DESTROY,
  });
  const fn = new lambda.Function(scope, id, {
    functionName,
    runtime: lambda.Runtime.JAVA_21,
    architecture: lambda.Architecture.ARM_64,
    handler: HANDLER,
    code: lambda.Code.fromAsset(props.codePath),
    memorySize: props.memoryMb ?? 1024,
    timeout: props.timeout,
    snapStart: lambda.SnapStartConf.ON_PUBLISHED_VERSIONS,
    loggingFormat: lambda.LoggingFormat.JSON,
    logGroup,
    retryAttempts: 0,
    environment: {
      MAIN_CLASS,
      spring_cloud_function_definition: props.functionBean,
      // Faster JVM start for short-lived functions.
      JAVA_TOOL_OPTIONS: "-XX:+TieredCompilation -XX:TieredStopAtLevel=1",
      ...props.environment,
    },
  });
  const alias = new lambda.Alias(scope, `${id}Live`, { aliasName: "live", version: fn.currentVersion });
  return { fn, alias };
}
