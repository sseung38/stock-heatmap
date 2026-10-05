import { Match } from "aws-cdk-lib/assertions";
import { describe, expect, it } from "vitest";
import { actionsOf, statementsFor, synth } from "./helpers";

const template = synth();

describe("data resources", () => {
  it("keeps the data bucket private and encrypted in transit", () => {
    template.hasResourceProperties("AWS::S3::Bucket", {
      PublicAccessBlockConfiguration: {
        BlockPublicAcls: true, BlockPublicPolicy: true, IgnorePublicAcls: true, RestrictPublicBuckets: true,
      },
    });
    template.hasResourceProperties("AWS::S3::BucketPolicy", {
      PolicyDocument: { Statement: Match.arrayWith([Match.objectLike({ Effect: "Deny", Condition: { Bool: { "aws:SecureTransport": "false" } } })]) },
    });
  });

  it("uses the always-free DynamoDB capacity", () => {
    template.hasResourceProperties("AWS::DynamoDB::Table", {
      TableName: "Watchlist",
      KeySchema: [{ AttributeName: "userId", KeyType: "HASH" }],
      ProvisionedThroughput: { ReadCapacityUnits: 1, WriteCapacityUnits: 1 },
    });
  });
});

describe("refresh Lambdas", () => {
  it("run the Spring Cloud Function handler on Java 21 / arm64 with SnapStart", () => {
    for (const [name, bean] of [["stock-heatmap-refresh-stocks", "refreshStocks"], ["stock-heatmap-refresh-crypto", "refreshCrypto"]]) {
      template.hasResourceProperties("AWS::Lambda::Function", {
        FunctionName: name,
        Runtime: "java21",
        Architectures: ["arm64"],
        MemorySize: 1024,
        Handler: "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest",
        SnapStart: { ApplyOn: "PublishedVersions" },
        LoggingConfig: Match.objectLike({ LogFormat: "JSON" }),
        Environment: {
          Variables: Match.objectLike({
            MAIN_CLASS: "com.stockheatmap.functions.FunctionsApplication",
            spring_cloud_function_definition: bean,
          }),
        },
      });
    }
    template.resourcePropertiesCountIs("AWS::Lambda::Alias", { Name: "live" }, 2);
  });

  it("keep logs for 14 days and never retry a failed scheduled run", () => {
    template.allResourcesProperties("AWS::Logs::LogGroup", { RetentionInDays: 14 });
    template.allResourcesProperties("AWS::Lambda::EventInvokeConfig", { MaximumRetryAttempts: 0 });
  });

  it("are scheduled in New York time against the live alias", () => {
    const schedules = template.findResources("AWS::Scheduler::Schedule");
    const expressions = Object.values(schedules).map((s) => s.Properties.ScheduleExpression).sort();

    expect(expressions).toEqual([
      "cron(0-20/5 16 ? * MON-FRI *)",
      "cron(0/5 10-15 ? * MON-FRI *)",
      "cron(30-55/5 9 ? * MON-FRI *)",
      "rate(15 minutes)",
    ]);
    for (const s of Object.values(schedules)) {
      expect(s.Properties.ScheduleExpressionTimezone).toBe("America/New_York");
      const targetId = s.Properties.Target.Arn.Ref as string; // Ref to a Lambda alias returns its ARN
      expect(template.toJSON().Resources[targetId].Type).toBe("AWS::Lambda::Alias");
      expect(s.Properties.Target.RetryPolicy.MaximumRetryAttempts).toBe(0);
    }
  });
});

describe("least privilege (spec §5.4)", () => {
  it("lets refresh-stocks write only stocks.json and read only the Alpaca keys", () => {
    const statements = statementsFor(template, "stock-heatmap-refresh-stocks");
    const writes = statements.filter((s) => actionsOf(s).some((a) => a.startsWith("s3:PutObject")));
    const ssm = statements.filter((s) => actionsOf(s).some((a) => a.startsWith("ssm:")));

    expect(writes).toHaveLength(1);
    expect(JSON.stringify(writes[0]!.Resource)).toContain("/stocks.json");
    expect(ssm).toHaveLength(1);
    expect(actionsOf(ssm[0]!)).toEqual(["ssm:GetParameter"]);
    const params = JSON.stringify(ssm[0]!.Resource);
    expect(params).toContain("parameter/stock-heatmap/alpaca-key-id");
    expect(params).toContain("parameter/stock-heatmap/alpaca-secret");
    expect(params).not.toContain("finnhub");
    expect(params).not.toContain("coingecko");
  });

  it("lets refresh-crypto write only crypto.json and read only the CoinGecko key", () => {
    const statements = statementsFor(template, "stock-heatmap-refresh-crypto");
    const writes = statements.filter((s) => actionsOf(s).some((a) => a.startsWith("s3:PutObject")));
    const params = JSON.stringify(statements.filter((s) => actionsOf(s).includes("ssm:GetParameter")).map((s) => s.Resource));

    expect(JSON.stringify(writes.map((w) => w.Resource))).toContain("/crypto.json");
    expect(JSON.stringify(writes.map((w) => w.Resource))).not.toContain("stocks.json");
    expect(params).toContain("coingecko-key");
    expect(params).not.toContain("alpaca");
  });

  it("gives refresh jobs read-only access to the watchlist table", () => {
    const actions = statementsFor(template, "stock-heatmap-refresh-stocks").flatMap(actionsOf);

    expect(actions.some((a) => a.startsWith("dynamodb:Scan"))).toBe(true);
    expect(actions.some((a) => a.startsWith("dynamodb:PutItem"))).toBe(false);
  });
});
