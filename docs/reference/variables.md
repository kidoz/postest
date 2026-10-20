# Variables

Variables insert reusable values into requests using double braces:

```
{{baseUrl}}/v1/orders/{{orderId}}
```

## Where variables come from

Only the **active environment** provides variables. Each variable has a key, a value, an enabled flag, and an optional secret flag.

- Disabled variables are ignored.
- Keys are matched exactly (case-sensitive).
- Whitespace inside the braces is trimmed, so `{{ baseUrl }}` matches `baseUrl`.
- If no variable matches, the original `{{name}}` text is sent unchanged.

## Where variables are resolved

- request URL
- query parameter keys and values
- header keys and values
- JSON, XML, raw, form-url-encoded, form-data, and GraphQL bodies
- Basic, Bearer, and API-key authentication fields
- client-certificate paths and passphrase
- binary body file path

## Dynamic variables

These are generated for each request:

| Variable | Value |
|----------|-------|
| `{{$guid}}`, `{{$uuid}}`, `{{$randomUUID}}` | random UUID |
| `{{$timestamp}}` | current Unix time in milliseconds |
| `{{$isoTimestamp}}` | current time in ISO-8601 format |
| `{{$randomInt}}` | random integer from 0 to 999 |
| `{{$randomString}}` | random 10-character string |
| `{{$randomEmail}}` | random `example.com` email address |

## Related

- [Manage environments and variables](../how-to/manage-environments.md)
- [Requests and responses](../explanation/request-lifecycle.md)
