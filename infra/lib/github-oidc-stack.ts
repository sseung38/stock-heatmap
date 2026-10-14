import { CfnOutput, Duration, Stack, type StackProps } from "aws-cdk-lib";
import * as iam from "aws-cdk-lib/aws-iam";
import type { Construct } from "constructs";

export interface GithubOidcStackProps extends StackProps {
  /**
   * The repository as GitHub writes it at the start of the token's "sub" claim. GitHub now uses
   * immutable IDs ("repo:<owner>@<ownerId>/<name>@<repoId>"), so a repository deleted and
   * re-created under the same name can't inherit this role. Get it with:
   *   gh api repos/<owner>/<name>/actions/oidc/customization/sub -q .sub_claim_prefix
   */
  subjectPrefix: string;
}

const ISSUER = "token.actions.githubusercontent.com";

/**
 * Lets GitHub Actions deploy without any stored AWS keys (spec §12). Each workflow run gets a
 * short-lived token from GitHub; AWS trusts it only for pushes to main of this one repository,
 * and the role can do nothing except assume CDK's bootstrap roles, which perform the deploy.
 */
export class GithubOidcStack extends Stack {
  constructor(scope: Construct, id: string, props: GithubOidcStackProps) {
    super(scope, id, props);

    const provider = new iam.OidcProviderNative(this, "Github", {
      url: `https://${ISSUER}`,
      clientIds: ["sts.amazonaws.com"],
    });

    const role = new iam.Role(this, "DeployRole", {
      roleName: "stock-heatmap-github-deploy",
      description: `GitHub Actions deploys from main (${props.subjectPrefix})`,
      maxSessionDuration: Duration.hours(1),
      assumedBy: new iam.WebIdentityPrincipal(provider.oidcProviderArn, {
        StringEquals: {
          [`${ISSUER}:aud`]: "sts.amazonaws.com",
          [`${ISSUER}:sub`]: `${props.subjectPrefix}:ref:refs/heads/main`,
        },
      }),
    });
    role.addToPolicy(
      new iam.PolicyStatement({
        actions: ["sts:AssumeRole"],
        resources: [`arn:aws:iam::${this.account}:role/cdk-hnb659fds-*-${this.account}-${this.region}`],
      }),
    );

    new CfnOutput(this, "DeployRoleArn", { value: role.roleArn });
  }
}
