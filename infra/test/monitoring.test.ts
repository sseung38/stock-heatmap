import { describe, expect, it } from "vitest";
import { synth } from "./helpers";

const template = synth();

describe("cost guardrail", () => {
  it("emails when this month's spend is forecast to pass $1", () => {
    template.hasResourceProperties("AWS::Budgets::Budget", {
      Budget: {
        BudgetType: "COST",
        TimeUnit: "MONTHLY",
        BudgetLimit: { Amount: 1, Unit: "USD" },
      },
      NotificationsWithSubscribers: [
        {
          Notification: { NotificationType: "FORECASTED", ComparisonOperator: "GREATER_THAN", Threshold: 100, ThresholdType: "PERCENTAGE" },
          Subscribers: [{ SubscriptionType: "EMAIL", Address: "alerts@example.com" }],
        },
      ],
    });
  });
});

describe("failure alarms", () => {
  it("email failures through one SNS topic", () => {
    template.hasResourceProperties("AWS::SNS::Subscription", { Protocol: "email", Endpoint: "alerts@example.com" });
  });

  it("alarm only after failures persist, and treat quiet hours as OK", () => {
    const alarms = Object.values(template.findResources("AWS::CloudWatch::Alarm")).map((a) => a.Properties);
    const byFunction = (name: string) => alarms.find((a) => JSON.stringify(a.Dimensions).includes(name))!;
    const expected: [string, number, number][] = [
      ["RefreshStocks", 300, 3], // 15 minutes of failed 5-minute runs
      ["RefreshCrypto", 900, 3], // 45 minutes of failed 15-minute runs
      ["RefreshBaselines", 3600, 1],
      ["RefreshMetadata", 3600, 1],
    ];

    expect(alarms).toHaveLength(4);
    for (const [fn, period, periods] of expected) {
      const alarm = byFunction(fn);
      expect(alarm, fn).toMatchObject({
        MetricName: "Errors", Namespace: "AWS/Lambda", Statistic: "Sum",
        Period: period, EvaluationPeriods: periods, DatapointsToAlarm: periods,
        Threshold: 1, ComparisonOperator: "GreaterThanOrEqualToThreshold", TreatMissingData: "notBreaching",
      });
      expect(JSON.stringify(alarm.AlarmActions)).toContain("Alerts");
    }
  });
});
