import { App } from "aws-cdk-lib";
import { GithubOidcStack } from "../lib/github-oidc-stack";
import { StockHeatmapStack } from "../lib/stack";

const alertEmail = process.env.ALERT_EMAIL;
if (!alertEmail) throw new Error("ALERT_EMAIL is required (where cost and failure alerts are emailed)");

const app = new App();

// Account comes from your AWS CLI credentials; region is fixed (spec: us-east-1).
const env = { account: process.env.CDK_DEFAULT_ACCOUNT, region: "us-east-1" };

new StockHeatmapStack(app, "StockHeatmapStack", { env, alertEmail });

// One-time setup, deployed by hand:
//   cdk deploy StockHeatmapGithubOidc -c githubSubjectPrefix=$(gh api repos/<owner>/<name>/actions/oidc/customization/sub -q .sub_claim_prefix)
const subjectPrefix = app.node.tryGetContext("githubSubjectPrefix") as string | undefined;
if (subjectPrefix) new GithubOidcStack(app, "StockHeatmapGithubOidc", { env, subjectPrefix });
