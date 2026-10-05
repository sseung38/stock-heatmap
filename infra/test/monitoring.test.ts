import { describe, it } from "vitest";
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
