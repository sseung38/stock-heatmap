import * as budgets from "aws-cdk-lib/aws-budgets";
import { Construct } from "constructs";

export interface MonitoringProps {
  alertEmail: string;
}

/**
 * Cost guardrail: AWS Budgets emails when this month's spend is *forecast* to exceed $1.
 * The app is designed to cost $0, so any forecast spend means something unexpected is running.
 * (Lambda failure alarms are added here in Task 21.)
 */
export class Monitoring extends Construct {
  constructor(scope: Construct, id: string, props: MonitoringProps) {
    super(scope, id);
    new budgets.CfnBudget(this, "MonthlyBudget", {
      budget: {
        budgetName: "stock-heatmap-monthly",
        budgetType: "COST",
        timeUnit: "MONTHLY",
        budgetLimit: { amount: 1, unit: "USD" },
      },
      notificationsWithSubscribers: [
        {
          notification: { notificationType: "FORECASTED", comparisonOperator: "GREATER_THAN", threshold: 100, thresholdType: "PERCENTAGE" },
          subscribers: [{ subscriptionType: "EMAIL", address: props.alertEmail }],
        },
      ],
    });
  }
}
