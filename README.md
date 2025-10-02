# 🍷 Wine Cellar App

A self-hosted wine collection management application built with Clojure and ClojureScript.

![Wine Cellar App Overview](docs/screenshots/overview.png)
*Wine collection overview showing filtering, ratings, and inventory management*

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

![Wine Cellar AI Assistant](docs/screenshots/assistant.png)
*AI-powered wine assistant providing personalized recommendations based on your collection*

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

5. **Open** http://localhost:8080 (frontend) or http://localhost:3000 (backend API)

## Contributor Guide

Review the repository-specific expectations in [Repository Guidelines](AGENTS.md) before opening a PR.

## Tech Stack
- **Backend**: Clojure, Ring, Reitit, HoneySQL, PostgreSQL
- **Frontend**: ClojureScript, Reagent (React), Material-UI  
- **AI**: Anthropic Claude API
- **Auth**: JWT with Google OAuth

### Credentials Management

#### Local Development

This application uses secure credential management:
- **Production**: Environment variables
- **Development**: [`pass`](https://www.passwordstore.org/) password manager

See [environment-variables.md](docs/environment-variables.md) for complete setup instructions and credential requirements.

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

2. Add the following secrets in your GitHub repository:
   - Go to your repository on GitHub
   - Navigate to Settings > Secrets and variables > Actions > Repository secrets
   - Create the required repository secrets (see [docs/environment-variables.md](docs/environment-variables.md) for complete list)
   - At minimum you need:
     - `FLY_API_TOKEN` - Fly.io API token for deployment
     - `FLY_APP_NAME` - Your Fly.io app name
     - `FLY_PRIMARY_REGION` - Fly.io deployment region (e.g., `iad`)
     - All environment variables listed in environment-variables.md

3. The workflow uses `fly.toml.template` and generates the actual `fly.toml` during deployment using your secret values.

Now, whenever you push changes to the main branch, the application will be automatically deployed to Fly.io.

## Development Tools
