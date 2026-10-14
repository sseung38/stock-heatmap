import { App } from "aws-cdk-lib";
import { Template } from "aws-cdk-lib/assertions";
import { expect, it } from "vitest";
import { GithubOidcStack } from "../lib/github-oidc-stack";

const template = Template.fromStack(
  new GithubOidcStack(new App(), "TestOidc", {
    env: { account: "123456789012", region: "us-east-1" },
    subjectPrefix: "repo:owner@111/stock-heatmap@222",
  }),
);

it("trusts only GitHub Actions runs on main of this repository", () => {
  const role = Object.values(template.findResources("AWS::IAM::Role"))[0]!;
  const [statement] = role.Properties.AssumeRolePolicyDocument.Statement;

  expect(statement.Action).toBe("sts:AssumeRoleWithWebIdentity");
  expect(statement.Condition).toEqual({
    StringEquals: {
      "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
      "token.actions.githubusercontent.com:sub": "repo:owner@111/stock-heatmap@222:ref:refs/heads/main",
    },
  });
  expect(role.Properties.RoleName).toBe("stock-heatmap-github-deploy");
});

it("can only assume the CDK bootstrap roles", () => {
  const statements = Object.values(template.findResources("AWS::IAM::Policy")).flatMap((p) => p.Properties.PolicyDocument.Statement);

  expect(statements).toHaveLength(1);
  expect(statements[0].Action).toBe("sts:AssumeRole");
  expect(statements[0].Resource).toBe("arn:aws:iam::123456789012:role/cdk-hnb659fds-*-123456789012-us-east-1");
});

it("registers GitHub's token issuer and outputs the role ARN", () => {
  template.hasResourceProperties("AWS::IAM::OIDCProvider", {
    Url: "https://token.actions.githubusercontent.com",
    ClientIdList: ["sts.amazonaws.com"],
  });
  expect(Object.keys(template.toJSON().Outputs)).toContain("DeployRoleArn");
});
