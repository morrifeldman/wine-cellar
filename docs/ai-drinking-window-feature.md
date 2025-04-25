# AI Drinking Window Suggestion Feature

This document outlines the implementation of the AI-powered feature to suggest optimal drinking windows for wines in the Wine Cellar application.

## Overview

The feature uses Anthropic's Claude API to analyze wine characteristics and suggest an appropriate drinking window (when to start drinking and when the wine might be past its prime). This helps users make informed decisions about when to consume their wines.

## Implementation Status

### 1. Backend Changes ✅

#### 1.1 Anthropic API Integration ✅

- Added `create-drinking-window-prompt` function in `wine_cellar.ai.anthropic` that formats wine data for Claude
- Created specialized prompt that includes wine characteristics (vintage, style, region, classification, etc.)
- Implemented `suggest-drinking-window` function that uses `call-anthropic-api` to get suggestions
- Response is parsed and returned as structured data (drink_from_year, drink_until_year, confidence, reasoning)

#### 1.2 API Endpoint ✅

- Added new endpoint in `wine_cellar.routes` for requesting drinking window suggestions
- Created corresponding handler in `wine_cellar.handlers`
- Endpoint accepts wine details and returns the suggested drinking window

### 2. Frontend Changes ✅

#### 2.1 API Client Extension ✅

- Added `suggest-drinking-window` function in `wine_cellar.api` to call the endpoint
- Implemented proper loading states and error handling

#### 2.2 UI Components ✅

- Added "Suggest Drinking Window" button to both the wine form and detail view
- Implemented loading indicators during API calls
- Added success message display with reasoning from Claude

#### 2.3 User Experience Flow ✅

- User clicks "Suggest Drinking Window" button
- System shows loading indicator
- When suggestion is received:
  - The drinking window fields are automatically updated
  - A success message displays the suggestion with confidence level and reasoning
  - User can modify the values if desired

### 3. AI Prompt Engineering ✅

#### 3.1 Prompt Design ✅

Created a specialized prompt that:
- Provides context about the wine (vintage, region, style, classification, etc.)
- Asks for a specific drinking window recommendation with justification
- Requests structured data in a consistent format

#### 3.2 Response Parsing ✅

- JSON response from Claude is parsed
- Error handling for unexpected responses is implemented
- Extracted drinking window and confidence level are applied to the wine

## Future Enhancements

- Allow users to provide feedback on AI suggestions to improve future recommendations
- Batch processing to suggest drinking windows for multiple wines at once
- Integrate with notifications to alert users when wines enter their optimal drinking window
- Add visualization of drinking windows in the wine list view

## Technical Considerations

- The AI response time is several seconds, so the UI handles this gracefully with loading indicators
- Error handling for API rate limits or service disruptions is implemented
- The feature degrades gracefully if the AI service is unavailable
