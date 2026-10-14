import { App } from "aws-cdk-lib";
import { GithubOidcStack } from "../lib/github-oidc-stack";
import { StockHeatmapStack } from "../lib/stack";

const alertEmail = process.env.ALERT_EMAIL;
if (!alertEmail) throw new Error("ALERT_EMAIL is required (where cost and failure alerts are emailed)");

const app = new App();

// Account comes from your AWS CLI credentials; region is fixed (spec: us-east-1).
const env = { account: process.env.CDK_DEFAULT_ACCOUNT, region: "us-east-1" };

new StockHeatmapStack(app, "StockHeatmapStack", { env, alertEmail });

// One-time setup, deployed by hand: cdk deploy StockHeatmapGithubOidc -c githubRepo=<owner>/<name>
const githubRepo = app.node.tryGetContext("githubRepo") as string | undefined;
if (githubRepo) new GithubOidcStack(app, "StockHeatmapGithubOidc", { env, repo: githubRepo });
