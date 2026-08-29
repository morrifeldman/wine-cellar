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
- `OPENAI_API_KEY`: API key for OpenAI Responses (optional, required for ChatGPT support)
- `GEMINI_API_KEY`: API key for Google's Gemini API (optional, required for Gemini support)

### Server Configuration

- `PORT`: Server port (defaults to 3000)
- `CLOJURE_ENV`: Set to "production" to enable production mode

## AI Models

Model choices live in `resources/ai-models.edn`, which is checked into the
repo and shipped in the Docker image:

```clojure
{:default-provider :anthropic
 :anthropic {:model "claude-opus-5" :small-model "claude-haiku-4-5"}
 ...}
```

Editing that file is the normal way to change models — commit the change and
deploy. Each provider has a `:model` for the main work and a `:small-model`
for quick tasks like conversation titles, and `:default-provider` picks the
provider used when the UI has not selected one.

For a one-off change without editing the file, set the matching environment
variable; it wins over the file at startup:

- `ANTHROPIC_MODEL`, `ANTHROPIC_SMALL_MODEL`
- `OPENAI_MODEL`, `OPENAI_SMALL_MODEL`
- `GEMINI_MODEL`, `GEMINI_SMALL_MODEL`
- `AI_DEFAULT_PROVIDER` ("anthropic", "openai", or "gemini")

In production that is `fly secrets set ANTHROPIC_MODEL=...`; in development,
export the variable before starting the dev environment. All of them are
optional — with none set, the app runs on the values in the edn file.

## Local Development Credentials

For local development, the application uses the `pass` password manager to securely store credentials. The following credentials should be stored in `pass`:

```
wine-cellar/anthropic-api-key
wine-cellar/jwt-secret  
wine-cellar/cookie-store-key
wine-cellar/admin-email
wine-cellar/google-client-id
wine-cellar/google-client-secret
wine-cellar/openai-api-key        # Optional
wine-cellar/gemini-api-key        # Optional
```

See the README.md for instructions on setting up `pass` for local development.
