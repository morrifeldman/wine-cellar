# Environment Variables and Credentials

This document lists all environment variables and credentials used by the Wine Cellar application.

## Production Environment Variables

In production (when `CLOJURE_ENV=production`), the following environment variables are used:

### Authentication

- `GOOGLE_CLIENT_ID`: Google OAuth client ID
- `GOOGLE_CLIENT_SECRET`: Google OAuth client secret
- `OAUTH_REDIRECT_URI`: Redirect URI for OAuth callbacks
- `JWT_SECRET`: Secret key for signing JWT tokens
- `COOKIE_STORE_KEY`: Secret key for the cookie store
- `ADMIN_EMAIL`: Email address of the admin user

### Database

- `DATABASE_URL`: PostgreSQL connection string

### AI Features

- `ANTHROPIC_API_KEY`: API key for Anthropic's Claude API (required for wine label analysis)
- `ANTHROPIC_MODEL`: Claude model to use (optional, defaults to claude-3-7-sonnet-20250219)

### Server Configuration

- `PORT`: Server port (defaults to 3000)
- `CLOJURE_ENV`: Set to "production" to enable production mode

## Local Development Credentials

For local development, the application uses the `pass` password manager to securely store credentials. The following credentials should be stored in `pass`:

```
wine-cellar/anthropic-api-key
wine-cellar/jwt-secret  
wine-cellar/cookie-store-key
wine-cellar/admin-email
wine-cellar/google-client-id
wine-cellar/google-client-secret
wine-cellar/oauth-redirect-uri
wine-cellar/anthropic-model       # Optional
```

See the README.md for instructions on setting up `pass` for local development.
