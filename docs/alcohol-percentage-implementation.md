# Implementing Wine Alcohol Percentage Tracking

This document outlines the steps needed to implement tracking of wine alcohol percentage in the wine cellar app.

## Database Changes

1. Add `alcohol_percentage` column to the `wines` table
   - Data type: `decimal(4,2)` to store values like 13.50%
   - Allow NULL values (not all wines will have this information)

## Backend Changes

1. Update `wine->db-wine` and `db-wine->wine` functions in `wine_cellar.db.api` to handle the new field
2. No changes needed to API endpoints as they already pass through all wine fields

## Frontend Changes

1. Add a new number field to the wine form to capture alcohol percentage
   - Include validation to ensure values are between 0 and 100
   - Format display with % symbol
   - Add appropriate helper text

## Implementation Steps

1. Modify database schema
2. Update backend code to handle the new field
3. Add the field to the frontend form
4. Test the feature end-to-end

## Testing Plan

1. Verify that alcohol percentage can be saved when creating a new wine
2. Verify that alcohol percentage can be updated for existing wines
3. Verify that the field accepts valid values (e.g., 13.5) and rejects invalid ones
4. Verify that the field is optional
