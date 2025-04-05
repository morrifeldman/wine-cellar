# Wine Cellar App

A personal wine cellar tracking application built with Clojure and ClojureScript.

## Local Setup

Use the Ansible playbook to setup postgresql.

`ansible-playbook postgresql.yml`

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
