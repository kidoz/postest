# Authenticate requests

Authentication is configured per request on the **Auth** tab.

## Basic authentication

1. Open the request's **Auth** tab.
2. Select **Basic**.
3. Enter the username and password.

Postest sends an `Authorization: Basic ...` header.

## Bearer token

1. Select **Bearer**.
2. Enter the token.

Postest sends `Authorization: Bearer <token>`.

## API key

1. Select **API Key**.
2. Choose where the key is sent:
   - **Header** - adds the key as a request header
   - **Query parameter** - adds the key to the URL query string
3. Enter the key name and value.

## Client certificate (mTLS)

The client-certificate section is separate from the authentication type. It can be combined with Basic, Bearer, or API-key auth.

1. Enable **Client Certificate (mTLS)**.
2. Enter the certificate path.
   - A `.p12` or `.pfx` file is loaded as PKCS#12. Enter the passphrase if the file is protected.
   - Any other extension is treated as a PEM certificate. Enter the certificate path, the private-key path, and the passphrase if the key is encrypted.
3. Send the request.

Paths and passphrases support environment variables, so you can keep them in an environment.

## Use variables

All authentication fields support `{{variable}}` substitution from the active environment. This is useful for tokens and usernames that change per environment.

## Troubleshooting

- **401 Unauthorized** - check the scheme and the value. Bearer tokens should not include the `Bearer` prefix in the token field.
- **403 Forbidden** - the credentials are valid but the server denies access.
- **Certificate errors** - confirm the certificate and key match, the key is readable, and the passphrase is correct. PKCS#12 files must contain a key entry.
- **Variables are sent literally** - confirm the environment is active and the variable is enabled.

## Security

Credentials and certificate passphrases are stored in the request data in the local database. They are not sent anywhere except to the target server. Optional HTTP header logging sanitizes `Authorization`, `Cookie`, and headers containing `token` or `secret`.

See [Security model](../explanation/security-model.md) and [Variables](../reference/variables.md).
