import { CfnOutput, Stack, type StackProps } from "aws-cdk-lib";
import type { Construct } from "constructs";
import { fileURLToPath } from "node:url";
import { Api } from "./api";
import { DataResources } from "./data";
import { Jobs } from "./jobs";
import { Monitoring } from "./monitoring";
import { Site } from "./site";

export const DEFAULT_CODE_PATH = fileURLToPath(
  new URL("../../backend/functions/build/distributions/functions-lambda.zip", import.meta.url),
);

export const DEFAULT_WEB_DIST_PATH = fileURLToPath(new URL("../../web/dist", import.meta.url));

export interface StockHeatmapStackProps extends StackProps {
  /** Lambda package; tests pass a tiny fixture zip. */
  codePath?: string;
  /** Built web app (npm run build -w web); tests pass a fixture folder. */
  webDistPath?: string;
  /** Where cost (and, from Task 21, failure) alerts are emailed. */
  alertEmail: string;
}

export class StockHeatmapStack extends Stack {
  constructor(scope: Construct, id: string, props: StockHeatmapStackProps) {
    super(scope, id, props);
    const codePath = props.codePath ?? DEFAULT_CODE_PATH;

    const data = new DataResources(this, "Data");
    new Jobs(this, "Jobs", { data, codePath });
    // The site's address must exist before Cognito and CORS can allow it; config.json then needs the API's values.
    const site = new Site(this, "Site", { webDistPath: props.webDistPath ?? DEFAULT_WEB_DIST_PATH });
    const api = new Api(this, "Api", { data, codePath, siteUrls: [site.url, "http://localhost:5173"] });
    site.deploy(api.webConfig);
    new Monitoring(this, "Monitoring", { alertEmail: props.alertEmail });

    new CfnOutput(this, "DataBucketName", { value: data.dataBucket.bucketName });
  }
}
