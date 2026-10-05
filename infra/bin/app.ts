import { App } from "aws-cdk-lib";
import { StockHeatmapStack } from "../lib/stack";

const alertEmail = process.env.ALERT_EMAIL;
if (!alertEmail) throw new Error("ALERT_EMAIL is required (where cost and failure alerts are emailed)");

const app = new App();

new StockHeatmapStack(app, "StockHeatmapStack", {
  // Account comes from your AWS CLI credentials; region is fixed (spec: us-east-1).
  env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: "us-east-1" },
  alertEmail,
});
