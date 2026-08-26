# SSO RP-Initiated Logout — Phase 3

## Status

The student SPA and IAM service now implement the OpenID Connect
RP-Initiated Logout protocol. Automated IAM and frontend tests cover provider
metadata, registered post-logout redirects, callback routing and local cleanup.
A real multi-browser acceptance run remains a deployment verification step.

## Flow

```text
Student clicks "退出统一认证"
  -> oidc-client-ts loads the current ID Token
  -> browser redirects to IAM end_session_endpoint
     with id_token_hint, post_logout_redirect_uri and state
  -> IAM identifies the client, validates the allow-listed callback
     and terminates its browser authentication session
  -> IAM redirects to /auth/logout/callback?state=...
  -> oidc-client-ts validates the stored logout state
  -> Pinia, OIDC sessionStorage and legacy local state are cleared
  -> browser returns to /login
```

The dedicated logout callback is intentional. Redirecting straight to the
guest-only login page would let the router initialize the old application
session before the protocol callback had validated `state`.

## Security properties

- `id_token_hint` lets IAM identify the original login session and client.
- `post_logout_redirect_uri` must exactly match a URI registered for that
  client; an arbitrary attacker-controlled redirect is not accepted.
- `oidc-client-ts` persists a one-time logout state before navigation and
  validates it at the callback, protecting the response correlation.
- callback failure still clears local application credentials, but is shown to
  the user rather than being reported as a successful provider logout.
- an old or incomplete local OIDC record without an ID Token falls back to
  local cleanup because it cannot safely construct a standards-based request.

## Failure behavior

IAM being unavailable must not trap a user in a locally authenticated UI. The
student application therefore attempts provider logout first, then removes the
local OIDC user and Pinia session if the redirect cannot be started. This is a
safe local logout, but it is not reported as proof that IAM terminated its
browser session.

## Important boundary

Ending the IAM browser session is not the same as revoking every bearer token
already issued to every relying party. Existing JWT Access Tokens remain valid
until expiry unless resource servers consult a revocation mechanism. The phase
also does not yet implement administrator-frontend integration, Front-Channel
Logout or Back-Channel Logout.

An accurate project claim is therefore:

> Implemented OIDC RP-Initiated Logout for the student SPA, including ID Token
> hint, allow-listed post-logout redirect and state-validated callback; global
> revocation of already-issued tokens remains outside this phase.

## Verification

- IAM Discovery exposes `end_session_endpoint` at `/connect/logout`.
- the student public client registers only its dedicated post-logout callback;
- an IAM integration test issues an ID Token via Authorization Code + PKCE and
  verifies a real logout request redirects to the registered URI with state;
- frontend tests cover redirect initiation, missing-ID-Token fallback,
  callback-failure cleanup and the public callback route;
- full IAM and frontend suites, TypeScript checking and production build are
  required before this phase can be merged.
