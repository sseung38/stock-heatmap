import { App } from "aws-cdk-lib";
import { Template } from "aws-cdk-lib/assertions";
import { fileURLToPath } from "node:url";
import { StockHeatmapStack } from "../lib/stack";

export const FIXTURE_ZIP = fileURLToPath(new URL("./fixtures/lambda.zip", import.meta.url));
export const FIXTURE_WEB_DIST = fileURLToPath(new URL("./fixtures/web-dist", import.meta.url));

export function synth(): Template {
  const app = new App();
  const stack = new StockHeatmapStack(app, "TestStack", {
    env: { account: "123456789012", region: "us-east-1" },
    codePath: FIXTURE_ZIP,
    webDistPath: FIXTURE_WEB_DIST,
    alertEmail: "alerts@example.com",
  });
  return Template.fromStack(stack);
}

type Statement = { Action: string | string[]; Resource: unknown };

/** All IAM policy statements attached to the role of the Lambda with this function name. */
export function statementsFor(template: Template, functionName: string): Statement[] {
  const functions = template.findResources("AWS::Lambda::Function", { Properties: { FunctionName: functionName } });
  const roleRef = JSON.stringify(Object.values(functions)[0]!.Properties.Role);
  const roleId = JSON.parse(roleRef)["Fn::GetAtt"][0] as string;
  return Object.values(template.findResources("AWS::IAM::Policy"))
    .filter((p) => JSON.stringify(p.Properties.Roles).includes(`"${roleId}"`))
    .flatMap((p) => p.Properties.PolicyDocument.Statement as Statement[]);
}

export const actionsOf = (s: Statement) => (Array.isArray(s.Action) ? s.Action : [s.Action]);
