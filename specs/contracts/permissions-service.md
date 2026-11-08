# Permissions Service Contract

## Check browser-based access

`POST /permissions/is_granted`

The request has a JSON body identifying the weather API endpoint being
accessed. The caller supplies the original client's `User-Agent` as an HTTP
header:

```http
POST /permissions/is_granted
Content-Type: application/json
User-Agent: Mozilla/5.0 (...) Chrome/125.0.0.0 Safari/537.36

{"endpoint":"forecast"}
```

The endpoint classifies the header as `SAFARI`, `CHROME`, or `OTHER`.
Access is evaluated for the requested endpoint:

| `endpoint` | `SAFARI` | `CHROME` | `OTHER` |
|---|---|---|---|
| `forecast` | Granted | Granted | Denied |
| `nearby` | Denied | Granted | Denied |

A missing, blank, malformed, or unrecognized `User-Agent` is classified as
`OTHER`. A missing or unrecognized endpoint key is denied. Browser
classification and endpoint-specific authorization are handled only by the
permissions service.

| Status | Meaning | Response body |
|---|---|---|
| `200 OK` | Access granted | Empty |
| `403 Forbidden` | Access denied | `{"error":"Permission denied"}` |

The 403 response is JSON (`Content-Type: application/json`). The endpoint does
not require authentication. User-Agent values are client-controlled and
spoofable; this decision is a browser classification policy, not caller
authentication. Weather API callers must send the endpoint key in the JSON
body for every permission check.
