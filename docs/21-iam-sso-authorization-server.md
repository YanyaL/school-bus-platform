# IAM SSO Authorization Server — Phase 1

## Status

Phase 1 adds an OAuth 2.1 / OpenID Connect authorization server to the
independent IAM service. It is additive: the existing JSON login, refresh and
logout endpoints remain available while the browser clients migrate to SSO.

## Implemented scope

- Spring Authorization Server hosted by `school-bus-iam`;
- OIDC discovery and JWK publication;
- two public browser clients: student (`:5173`) and admin (`:5174`);
- Authorization Code flow with mandatory PKCE;
- account authentication through the existing `iam_account` repository and
  delegating password encoder;
- JWT access tokens whose `sub`, `roles` and `aud` claims remain compatible
  with the existing Gateway and resource services;
- separate security filter chains for authorization endpoints, stateless APIs
  and the browser login session.

## Deliberate security decisions

The browser clients have no client secret because any secret embedded in a
downloaded SPA can be extracted. They use `ClientAuthenticationMethod.NONE`
and must provide a PKCE code challenge. The implicit and resource-owner
password grants are not registered.

Spring Authorization Server intentionally does not issue refresh tokens to a
public client. The existing custom refresh-token rotation still serves the
legacy JSON login path. A later phase can introduce a confidential BFF if the
OIDC browser flow needs server-side refresh-token rotation and coordinated
logout. The project must not claim that the public PKCE clients already receive
refresh tokens.

## Transaction and session boundaries

OAuth authorization requires a short-lived browser session so that login and
consent can span redirects. REST APIs remain stateless and continue to validate
Bearer JWTs. Separating the filter chains prevents an accidental global switch
from stateless APIs to server sessions.

## Verification evidence

- IAM compile and automated test suite;
- registered-client tests prove public-client authentication, authorization
  code only, mandatory PKCE and absence of the refresh-token grant;
- account adapter tests prove normalized student-number lookup, role mapping
  and disabled-account rejection.

## Remaining work

Student frontend integration is now implemented in Phase 2; see
`docs/22-student-sso-frontend.md`.

1. Add the admin frontend callback route and OIDC client integration.
2. Decide between SPA-only access tokens and a confidential BFF for refresh
   token rotation.
3. Implement RP-initiated logout and distributed session revocation.
4. Add a browser-level authorization-code acceptance test against real IAM.
