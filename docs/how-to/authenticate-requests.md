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

## OAuth 2.0

OAuth 2.0 supports **Authorization Code with PKCE** and **Client Credentials**:

1. Select **OAuth 2.0**.
2. Choose the grant type.
3. Enter the provider's **Authorization URL** (Authorization Code only) and **Token URL**. HTTPS is required, except for loopback test endpoints.
4. Enter the **Client ID** and, if the provider issued one, the **Client Secret**. Enable **Send client secret in request body** when the provider expects `client_secret_post` instead of Basic authentication.
5. Enter the **Scope** if required.
6. Choose **Authorize** (Authorization Code) or **Get Token** (Client Credentials).

Authorization Code opens your default browser and returns through a temporary `http://127.0.0.1:<port>/callback` listener. Register loopback redirects with your provider; the port is chosen at runtime. The flow uses PKCE (S256), verifies the `state` value, and times out after five minutes.

The acquired access token is stored with the request. **Clear Token** removes the stored token. When a stored token expires but a refresh token is available, Postest refreshes it in memory for subsequent requests; if the provider rotates refresh tokens, authorize again after restarting.

Grant types not listed here, such as Password or Implicit, are not supported.

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

Credentials, OAuth tokens, and certificate passphrases are stored in the request data in the local database. Encrypted storage protects them at rest; unencrypted storage leaves them readable. They are not sent anywhere except to the target server and, for OAuth, the configured token endpoint. Optional HTTP header logging sanitizes `Authorization`, `Cookie`, and headers containing `token` or `secret`. Authorization codes, tokens, and client secrets are never logged.

See [Security model](../explanation/security-model.md) and [Variables](../reference/variables.md).
