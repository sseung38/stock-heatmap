import { App } from "aws-cdk-lib";
import { StockHeatmapStack } from "../lib/stack";

const app = new App();

new StockHeatmapStack(app, "StockHeatmapStack", {
  // Account comes from your AWS CLI credentials; region is fixed (spec: us-east-1).
  env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: "us-east-1" },
});
