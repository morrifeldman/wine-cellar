# Grape Varieties Implementation Plan

## Database Changes

1. **Create New Tables**
   - `grape_varieties` table to store standardized grape variety names
   - `wine_grape_varieties` join table to link wines with varieties

2. **Schema Definition**
   - Add schema definitions in `wine_cellar.db.schema` namespace
   - Define the tables with appropriate constraints

## Backend Implementation

1. **Database API**
   - Add CRUD operations for grape varieties in `wine_cellar.db.api`
   - Create functions to associate varieties with wines
   - Implement functions to query wines by variety

2. **API Routes**
   - Add new routes in `wine_cellar.routes` for grape variety operations
   - Define appropriate specs for request validation

3. **Request Handlers**
   - Implement handlers in `wine_cellar.handlers` for the new routes

## Frontend Implementation

1. **API Client**
   - Add functions in `wine_cellar.api` to interact with grape variety endpoints

2. **UI Components**
   - Create a multi-select component for grape varieties in the wine form
   - Add percentage input for each selected variety (optional)
   - Update the wine detail view to display associated grape varieties

3. **Wine Form Enhancement**
   - Modify `wine_cellar.views.wines.form` to include grape variety selection
   - Add validation for grape variety inputs

4. **Wine Detail Enhancement**
   - Update `wine_cellar.views.wines.detail` to display grape varieties

## Data Flow

1. **Creating/Editing Wines**
   - When creating or editing a wine, allow selection of multiple grape varieties
   - For each variety, optionally specify percentage
   - Submit varieties along with other wine data

2. **Displaying Wines**
   - Fetch associated grape varieties when displaying wine details
   - Show varieties and percentages in the wine detail view

## Implementation Sequence

1. Implement database schema changes -- do not add migrations
2. Add backend API functions and routes
3. Create frontend components for variety selection
4. Update wine form and detail views
5. Test the complete flow
