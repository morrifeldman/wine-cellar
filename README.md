# 🍷 Wine Cellar App

A self-hosted wine collection management application built with Clojure and ClojureScript.

## Features

### Wine Management
- **Track your collection** - Store detailed wine information (producer, vintage, region, style, etc.)
- **Smart search & filtering** - Find wines by name, region, style, price range, or tasting window
- **Physical location tracking** - Know exactly where each bottle is stored (A1, B2, C10, etc.)
- **Quantity & consumption tracking** - Monitor your inventory with easy +/- controls

### AI-Powered Features
- **Label analysis** - Upload wine label photos to automatically extract details
- **Drinking window suggestions** - Get AI recommendations for optimal drinking periods  
- **Wine chat** - Ask questions about your collection and get intelligent responses

### Tasting & Notes
- **Tasting notes & ratings** - Record detailed notes with 1-100 ratings
- **Photo storage** - Upload front/back label images with automatic thumbnails
- **Grape variety management** - Associate wines with varieties and percentages

### Self-Hosted Benefits
- **Your data, your control** - No vendor lock-in or subscription fees
- **Complete privacy** - No data mining or external tracking
- **Customizable** - Modify features to fit your exact needs

## Quick Start

### Prerequisites
- Java 11+
- Node.js 18+
- PostgreSQL
- [pass](https://www.passwordstore.org/) password manager

### Setup
1. **Database Setup**
   ```bash
   ansible-playbook postgresql.yml
   ```

2. **Install Dependencies**
   ```bash
   npm install
   ```

3. **Configure Credentials** (see Credentials section below)

4. **Start Development Server**
   ```bash
   clj -M:dev-all
   ```

5. **Open** http://localhost:3000

## Tech Stack
- **Backend**: Clojure, Ring, Reitit, HoneySQL, PostgreSQL
- **Frontend**: ClojureScript, Reagent (React), Material-UI  
- **AI**: Anthropic Claude API
- **Auth**: JWT with Google OAuth

### Credentials Management

#### Local Development

For local development, this application uses the [`pass`](https://www.passwordstore.org/) password manager to securely store credentials instead of environment variables. This approach provides better security by avoiding plain text storage of sensitive information.

Required credentials in `pass`:

```
wine-cellar/anthropic-api-key
wine-cellar/jwt-secret
wine-cellar/cookie-store-key
wine-cellar/admin-email
wine-cellar/google-oath-json
```

To set up `pass` for local development:

1. Install `pass` if you haven't already:
   ```
   # On Debian/Ubuntu
   sudo apt-get install pass
   
   # On macOS with Homebrew
   brew install pass
   ```

2. Initialize `pass` if you haven't already:
   ```
   pass init your-gpg-id
   ```

3. Store your credentials:
   ```
   pass insert wine-cellar/anthropic-api-key
   pass insert wine-cellar/jwt-secret
   pass insert wine-cellar/cookie-store-key
   pass insert wine-cellar/admin-email
   ```

4. For Google OAuth credentials, store them as a JSON string:
   ```
   pass insert wine-cellar/google-oath-json
   ```
   The JSON should have the format provided by Google OAuth.

See [environment-variables.md](docs/environment-variables.md) for a complete list of required credentials.

## Deployment to Fly.io

### Prerequisites

1. Install the Fly.io CLI:
   ```
   curl -L https://fly.io/install.sh | sh
   ```

2. Login to Fly.io:
   ```
   fly auth login
   ```

### Deployment Steps

1. Create a Fly.io app:
   ```
   fly apps create wine-cellar
   ```

2. Create a PostgreSQL database:
   ```
   fly postgres create --name wine-cellar-db
   ```

3. Attach the database to your app:
   ```
   fly postgres attach --app wine-cellar wine-cellar-db
   ```

5. Deploy the application:
   ```
   fly deploy
   ```

6. Open the application in your browser:
   ```
   fly open
   ```

### CI/CD with GitHub Actions

This repository includes a GitHub Actions workflow that automatically deploys the application to Fly.io when changes are pushed to the main branch.

To set up CI/CD:

1. Create a Fly.io API token:
   ```
   fly auth token
   ```

2. Add the token as a secret in your GitHub repository:
   - Go to your repository on GitHub
   - Navigate to Settings > Secrets and variables > Actions
   - Create a new repository secret named `FLY_API_TOKEN` with the value of your Fly.io API token

Now, whenever you push changes to the main branch, the application will be automatically deployed to Fly.io.
