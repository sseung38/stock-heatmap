import { CfnOutput, Stack, type StackProps } from "aws-cdk-lib";
import type { Construct } from "constructs";
import { fileURLToPath } from "node:url";
import { DataResources } from "./data";
import { Jobs } from "./jobs";
import { Monitoring } from "./monitoring";

export const DEFAULT_CODE_PATH = fileURLToPath(
  new URL("../../backend/functions/build/distributions/functions-lambda.zip", import.meta.url),
);

export interface StockHeatmapStackProps extends StackProps {
  /** Lambda package; tests pass a tiny fixture zip. */
  codePath?: string;
  /** Where cost (and, from Task 21, failure) alerts are emailed. */
  alertEmail: string;
}

export class StockHeatmapStack extends Stack {
  constructor(scope: Construct, id: string, props: StockHeatmapStackProps) {
    super(scope, id, props);
    const codePath = props.codePath ?? DEFAULT_CODE_PATH;

    const data = new DataResources(this, "Data");
    new Jobs(this, "Jobs", { data, codePath });
    new Monitoring(this, "Monitoring", { alertEmail: props.alertEmail });

    new CfnOutput(this, "DataBucketName", { value: data.dataBucket.bucketName });
  }
}
