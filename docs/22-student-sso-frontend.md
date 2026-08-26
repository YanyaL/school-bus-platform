# Student SSO Frontend — Phase 2

## Status

Phase 2 connects the Vue 3 student application to the IAM OpenID Connect
authorization server. The standards-based SSO path is now implemented in code
and covered by frontend and IAM automated tests. A real browser acceptance run
against all cloud services remains a separate infrastructure verification step.

## Implemented flow

```text
Student clicks SSO login
  -> oidc-client-ts creates state, nonce and PKCE verifier/challenge
  -> IAM authenticates the existing school-bus account
  -> IAM redirects to /auth/callback with an authorization code
  -> client validates the callback and exchanges code + verifier
  -> validated access-token claims populate the Pinia session
  -> student returns to the originally requested internal route
```

The callback only accepts local application return paths. Absolute and
protocol-relative URLs fall back to `/trips`, preventing an open-redirect path.

## Session model and migration boundary

The new SSO path and the previous JSON login coexist:

| Path | Session restoration | Refresh behavior |
|------|---------------------|------------------|
| OIDC Authorization Code + PKCE | OIDC user in `sessionStorage` | no public-client refresh token; re-authorize after expiry |
| Legacy JSON login | Pinia plus refresh token in `localStorage` | existing SHA-256-backed rotation API |

The application restores a valid OIDC user before trying the legacy refresh
path. An SSO access-token expiry never sends an OIDC token to the legacy refresh
endpoint. This separation avoids accidentally claiming that the public SPA
receives refresh tokens.

Keeping the old login form is a Strangler Fig migration choice: the SSO path can
be introduced and rolled back without replacing every caller in one release.

## Browser storage trade-off

`oidc-client-ts` stores the OIDC user, including its access token, in
`sessionStorage`. This limits persistence to the browser tab and avoids the
long-lived legacy `localStorage` refresh token, but it does not protect against
XSS. A production hardening phase can introduce a confidential BFF so browser
JavaScript no longer owns a long-lived credential.

## CORS policy

The IAM token and metadata endpoints permit only explicitly configured origins.
The default student origin is `http://127.0.0.1:5173`. Preflight requests for
OAuth/OIDC endpoints are routed through the authorization-server CORS filter:

- the configured student origin receives an allow-origin response;
- an untrusted origin is rejected;
- credentials are not enabled and `*` is not used.

## Local development invariants

Start the frontend and complete the callback on the same origin:

```text
http://127.0.0.1:5173
http://127.0.0.1:5173/auth/callback
```

`localhost` and `127.0.0.1` are different browser origins. Mixing them loses the
state and PKCE values stored before redirect.

The IAM issuer must also exactly match the frontend authority. For local direct
IAM access, use:

```powershell
$env:JWT_ISSUER='http://localhost:8084'
$env:SSO_STUDENT_ORIGIN='http://127.0.0.1:5173'
```

All JWT resource services participating in the run must trust the same issuer.

## Logout boundary

The local-only logout described by the original Phase 2 implementation has
been replaced by OIDC RP-Initiated Logout in Phase 3. The frontend now redirects
to IAM's discovered `end_session_endpoint` with an ID Token hint and a
registered post-logout callback. See `docs/23-sso-rp-initiated-logout.md` for the
flow, tests and the remaining cross-application revocation boundary.

## Verification

- OIDC client tests cover code-flow settings, internal return-path validation
  and claim-to-session mapping;
- Pinia tests cover callback state, SSO-first restoration and isolation from the
  legacy refresh-token endpoint;
- router tests cover the public callback route;
- IAM tests cover trusted-origin preflight and untrusted-origin rejection;
- IAM: 85 tests passed with zero failures;
- frontend: 26 tests passed, TypeScript type checking passed and the production
  build completed successfully.

## Remaining work

1. Run a real browser acceptance test against IAM, Gateway and a resource
   service.
2. Integrate the administrator frontend as the second OIDC client.
3. Define cross-application token revocation or Back-Channel Logout semantics.
4. Evaluate a confidential BFF if server-side refresh-token rotation is needed.
