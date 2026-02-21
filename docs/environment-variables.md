# Environment Variables and Credentials

This document lists all environment variables and credentials used by the Wine Cellar application.

## Production Environment Variables

In production (when `CLOJURE_ENV=production`), the following environment variables are used:

### Authentication

- `GOOGLE_CLIENT_ID`: Google OAuth client ID
- `GOOGLE_CLIENT_SECRET`: Google OAuth client secret
- `JWT_SECRET`: Secret key for signing JWT tokens
- `COOKIE_STORE_KEY`: Secret key for the cookie store
- `ADMIN_EMAIL`: Email address of the admin user

### Database

- `DATABASE_URL`: PostgreSQL connection string

### AI Features

- `ANTHROPIC_API_KEY`: API key for Anthropic's Claude API (required for wine label analysis)
- `ANTHROPIC_MODEL`: Claude model to use (optional, defaults to claude-sonnet-4-6)
- `ANTHROPIC_LIGHT_MODEL`: Lightweight Claude model for quick tasks like conversation titles (optional, defaults to claude-haiku-4-5)
- `OPENAI_API_KEY`: API key for OpenAI Responses (optional, required for ChatGPT support)
- `OPENAI_MODEL`: OpenAI model to use (optional, defaults to gpt-5)
- `OPENAI_LIGHT_MODEL`: Lightweight OpenAI model for quick tasks like conversation titles (optional, defaults to gpt-5-mini; gpt-5-nano also works well)
- `GEMINI_API_KEY`: API key for Google's Gemini API (optional, required for Gemini support)
- `GEMINI_MODEL`: Gemini model to use (optional, defaults to gemini-1.5-flash)
- `GEMINI_LITE_MODEL`: Lightweight Gemini model for quick tasks (optional, defaults to gemini-1.5-flash-8b)
- `AI_DEFAULT_PROVIDER`: Preferred provider when the UI has not selected one ("anthropic", "openai", or "gemini")

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
wine-cellar/anthropic-model       # Optional
wine-cellar/openai-api-key        # Optional
wine-cellar/openai-model          # Optional
wine-cellar/anthropic-light-model # Optional
wine-cellar/openai-light-model    # Optional
wine-cellar/gemini-api-key        # Optional
wine-cellar/gemini-model          # Optional
wine-cellar/gemini-light-model    # Optional
```

See the README.md for instructions on setting up `pass` for local development.
