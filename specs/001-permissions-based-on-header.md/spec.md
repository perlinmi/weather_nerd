# Permissions based on browser header

## Summary

The weather service asks the permissions service whether a caller is granted
access. The permissions service classifies the caller from the `User-Agent`
request header into one of three roles: `Safari`, `Chrome`, or `Other`, then
evaluates the grant for that role.

All roles use the same endpoint. The permissions service is an internal service
and this endpoint does not require caller authentication. User-Agent values are
client-controlled and spoofable; this endpoint provides browser classification,
not caller authentication or a security boundary.

This feature implements the permissions service endpoint. Integrating a
weather-service request flow with the endpoint is deferred until that flow is
defined.

## Endpoint contract

- Method and path: `POST /permissions/is_granted`
- Input: the original caller's `User-Agent` in the HTTP request header, forwarded
  by the weather service; no request body.
- A granted request returns `200 OK` with an empty body.
- A denied request returns `403 Forbidden` with a JSON body:
  `{"error":"Permission denied"}` and `Content-Type: application/json`.
- This is an internal endpoint and does not require caller authentication.

## Browser classification

- Parse the `User-Agent` header and assign one of the `Safari`, `Chrome`, or
  `Other` roles, regardless of browser version.
- Assign `Chrome` only when the header identifies Google Chrome. A Chrome
  token by itself is insufficient if the header identifies another browser;
  known alternate-browser identifiers take precedence. Edge and Opera are
  `Other`.
- Assign `Safari` when both Safari and its version token are present, and no
  Chrome or known alternate-browser identifier is present.
- Assign `Other` for other browsers and for missing or malformed headers.
- Browser version ranges do not affect role assignment.
- Recognize Chrome by `Chrome/<version>` after checking for `Edg/`, `EdgA/`,
  `EdgiOS/`, `OPR/`, `Opera/`, `Firefox/`, `FxiOS/`, `SamsungBrowser/`,
  `Vivaldi/`, and `YaBrowser/`; those alternate identifiers map to `Other`.
- Recognize Safari by both `Version/<version>` and `Safari/` after excluding
  Chrome and the alternate identifiers listed above. Unknown and blank values
  map to `Other`.

## Authorization policy

The endpoint grants access to the `Safari` and `Chrome` roles and denies access
to the `Other` role. No separate permission/action identifier is required.

## Acceptance criteria

1. The endpoint is available at `POST /permissions/is_granted` for all three
   roles; no role has a separate route.
2. The permissions service infers exactly one of `Safari`, `Chrome`, or
   `Other`, independent of browser version.
3. A User-Agent identifying Google Chrome maps to `Chrome`, unless it contains
   one of the specified alternate-browser identifiers, which maps to `Other`.
4. A User-Agent identifying Safari with both required tokens and no competing
   browser identifier maps to `Safari`.
5. A missing, blank, malformed, or unrecognized `User-Agent` maps to `Other`
   rather than producing a classification error.
6. `Safari` and `Chrome` are granted; `Other` is denied.
7. A granted request returns `200 OK` with an empty body. A denied request
   returns `403 Forbidden` with the JSON body
   `{"error":"Permission denied"}`.
8. The endpoint does not treat the User-Agent header as an authenticated or
   trustworthy caller identity.

## Open questions

- Exact browser-detection rules for less common or ambiguous User-Agent strings
  not covered by the examples above.