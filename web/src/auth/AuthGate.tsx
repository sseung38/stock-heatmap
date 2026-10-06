import type { AppConfig } from "@sh/shared";
import { useEffect, type ReactNode } from "react";
import { AuthProvider, useAuth } from "react-oidc-context";
import { ApiProvider } from "../api/ApiProvider";

type Auth = NonNullable<AppConfig["auth"]>;

/**
 * Signs the user in with Cognito (OpenID Connect, authorization code + PKCE) before the app
 * renders, then gives every API call the access token. Locally config.auth is null: no login.
 */
export function AuthGate({ config, children }: { config: AppConfig; children: ReactNode }) {
  if (!config.auth) {
    return <ApiProvider config={config} token={null}>{children}</ApiProvider>;
  }
  return (
    <AuthProvider
      authority={config.auth.authority}
      client_id={config.auth.clientId}
      redirect_uri={window.location.origin}
      scope="openid email"
      automaticSilentRenew
      // After the redirect back from Cognito, drop ?code=…&state=… from the address bar.
      onSigninCallback={() => window.history.replaceState({}, document.title, window.location.pathname)}
    >
      <SignedIn config={config} auth={config.auth}>{children}</SignedIn>
    </AuthProvider>
  );
}

function SignedIn({ config, children }: { config: AppConfig; auth: Auth; children: ReactNode }) {
  const auth = useAuth();
  const signedOut = !auth.isLoading && !auth.isAuthenticated && !auth.activeNavigator && !auth.error;

  useEffect(() => {
    if (signedOut) void auth.signinRedirect();
  }, [signedOut, auth]);

  // The refresh token expired (30 days) or was revoked: start a fresh login.
  useEffect(() => auth.events.addSilentRenewError(() => void auth.signinRedirect()), [auth]);

  if (auth.error) {
    return (
      <div className="auth-message">
        <p>Sign-in failed: {auth.error.message}</p>
        <button type="button" onClick={() => void auth.signinRedirect()}>Sign in again</button>
      </div>
    );
  }
  if (!auth.isAuthenticated || !auth.user) return <div className="auth-message">Signing in…</div>;
  return <ApiProvider config={config} token={auth.user.access_token}>{children}</ApiProvider>;
}
