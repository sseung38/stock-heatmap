import { Duration } from "aws-cdk-lib";
import * as budgets from "aws-cdk-lib/aws-budgets";
import * as cloudwatch from "aws-cdk-lib/aws-cloudwatch";
import * as actions from "aws-cdk-lib/aws-cloudwatch-actions";
import * as sns from "aws-cdk-lib/aws-sns";
import * as subscriptions from "aws-cdk-lib/aws-sns-subscriptions";
import { Construct } from "constructs";
import type { SpringFunction } from "./lambda";

export interface MonitoringProps {
  /** The scheduled jobs to watch. */
  functions: { stocks: SpringFunction; crypto: SpringFunction; baselines: SpringFunction; metadata: SpringFunction };
  alertEmail: string;
}

/**
 * Emails when something needs attention (spec §11):
 * - a refresh job keeps failing (CloudWatch alarm → SNS topic → email), and
 * - this month's spend is *forecast* to exceed $1 (AWS Budgets). The app is designed to cost $0,
 *   so any forecast spend means something unexpected is running.
 */
export class Monitoring extends Construct {
  readonly topic: sns.Topic;

  constructor(scope: Construct, id: string, props: MonitoringProps) {
    super(scope, id);

    // SNS sends a "Confirm subscription" email first; alerts arrive only after you click it.
    this.topic = new sns.Topic(this, "Alerts", { displayName: "Stock Heatmap alerts" });
    this.topic.addSubscription(new subscriptions.EmailSubscription(props.alertEmail));

    // One failed run is retried by the next scheduled run, so alarm only when failures persist.
    const { stocks, crypto, baselines, metadata } = props.functions;
    this.alarmOnErrors("StocksFailing", stocks, Duration.minutes(5), 3);
    this.alarmOnErrors("CryptoFailing", crypto, Duration.minutes(15), 3);
    this.alarmOnErrors("BaselinesFailing", baselines, Duration.hours(1), 1);
    this.alarmOnErrors("MetadataFailing", metadata, Duration.hours(1), 1);

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

  /** Alarms when every one of `periods` consecutive periods had at least one error. */
  private alarmOnErrors(id: string, target: SpringFunction, period: Duration, periods: number) {
    const alarm = new cloudwatch.Alarm(this, id, {
      alarmDescription: `${target.fn.functionName} failed in ${periods} consecutive ${period.toHumanString()} periods`,
      metric: target.fn.metricErrors({ statistic: cloudwatch.Stats.SUM, period }),
      threshold: 1,
      comparisonOperator: cloudwatch.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
      evaluationPeriods: periods,
      datapointsToAlarm: periods,
      // No runs (nights, weekends) means no data: that's normal, not a failure.
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
    });
    alarm.addAlarmAction(new actions.SnsAction(this.topic));
  }
}
