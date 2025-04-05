#!/bin/bash
set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Default values
APP_NAME="wine-cellar"
DB_NAME="wine-cellar-db"
REGION="ord" # San Jose, CA - change as needed

# Parse command line arguments
while [[ "$#" -gt 0 ]]; do
  case $1 in
  --app)
    APP_NAME="$2"
    shift
    ;;
  --db)
    DB_NAME="$2"
    shift
    ;;
  --region)
    REGION="$2"
    shift
    ;;
  --help)
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  --app NAME     Set the Fly.io app name (default: wine-cellar)"
    echo "  --db NAME      Set the Fly.io PostgreSQL database name (default: wine-cellar-db)"
    echo "  --region CODE  Set the Fly.io region (default: sjc)"
    echo "  --help         Show this help message"
    exit 0
    ;;
  *)
    echo "Unknown parameter: $1"
    exit 1
    ;;
  esac
  shift
done

echo -e "${YELLOW}Deploying Wine Cellar to Fly.io${NC}"
echo -e "App name: ${GREEN}$APP_NAME${NC}"
echo -e "Database name: ${GREEN}$DB_NAME${NC}"
echo -e "Region: ${GREEN}$REGION${NC}"
echo ""

# Check if flyctl is installed
if ! command -v flyctl &>/dev/null; then
  echo -e "${RED}Error: flyctl is not installed${NC}"
  echo "Please install the Fly.io CLI first:"
  echo "curl -L https://fly.io/install.sh | sh"
  exit 1
fi

# Check if user is logged in
echo -e "${YELLOW}Checking Fly.io authentication...${NC}"
if ! flyctl auth whoami &>/dev/null; then
  echo -e "${YELLOW}You need to log in to Fly.io first${NC}"
  flyctl auth login
fi

# Create the app if it doesn't exist
echo -e "${YELLOW}Creating Fly.io app (if it doesn't exist)...${NC}"
if ! flyctl apps list | grep -q "$APP_NAME"; then
  echo -e "${GREEN}Creating new app: $APP_NAME${NC}"
  flyctl apps create "$APP_NAME" --region "$REGION"
else
  echo -e "${GREEN}App $APP_NAME already exists${NC}"
fi

# Create PostgreSQL database if it doesn't exist
echo -e "${YELLOW}Setting up PostgreSQL database...${NC}"
if ! flyctl postgres list | grep -q "$DB_NAME"; then
  echo -e "${GREEN}Creating new PostgreSQL database: $DB_NAME${NC}"
  flyctl postgres create --name "$DB_NAME" --region "$REGION"

  echo -e "${YELLOW}Attaching database to app...${NC}"
  flyctl postgres attach --app "$APP_NAME" "$DB_NAME"
else
  echo -e "${GREEN}Database $DB_NAME already exists${NC}"

  # Check if database is attached to the app
  if ! flyctl postgres attachments --app "$APP_NAME" | grep -q "$DB_NAME"; then
    echo -e "${YELLOW}Attaching database to app...${NC}"
    flyctl postgres attach --app "$APP_NAME" "$DB_NAME"
  else
    echo -e "${GREEN}Database is already attached to the app${NC}"
  fi
fi

# Deploy the application
echo -e "${YELLOW}Deploying application...${NC}"
flyctl deploy --app "$APP_NAME"

# Open the application in browser
echo -e "${GREEN}Deployment complete!${NC}"
echo -e "${YELLOW}Opening application in browser...${NC}"
flyctl open --app "$APP_NAME"

echo -e "${GREEN}Deployment process completed successfully!${NC}"
