# Permissions Service Contract

## Check browser-based access

`POST /permissions/is_granted`

The request has no body. The caller supplies the original client's
`User-Agent` as an HTTP header:

```http
POST /permissions/is_granted
User-Agent: Mozilla/5.0 (...) Chrome/125.0.0.0 Safari/537.36
```

The endpoint classifies the header as `SAFARI`, `CHROME`, or `OTHER`.
`SAFARI` and `CHROME` are granted; `OTHER` is denied. A missing, blank,
malformed, or unrecognized header is classified as `OTHER`.

| Status | Meaning | Response body |
|---|---|---|
| `200 OK` | Access granted | Empty |
| `403 Forbidden` | Access denied | `{"error":"Permission denied"}` |

The 403 response is JSON (`Content-Type: application/json`). The endpoint does
not require authentication. User-Agent values are client-controlled and
spoofable; this decision is a browser classification policy, not caller
authentication.
