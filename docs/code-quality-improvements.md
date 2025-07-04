# Code Quality Improvements

This document outlines identified areas for improvement in the wine cellar codebase, focusing on architecture, maintainability, and code quality.

## Todo List

### High Priority
- [ ] **Refactor API layer to separate data fetching from state management** - return promises instead of directly manipulating app-state
- [ ] **Create generic editable field factory** to eliminate repetition in wine detail component

### Medium Priority  
- [ ] **Move wine filtering logic from chat component** to dedicated utility namespace
- [ ] **Break down oversized wine detail component** into smaller focused components

### Low Priority
- [ ] **Separate navigation logic from API layer** - remove browser history manipulation from API functions

## Detailed Analysis

### 1. State Management in API Layer (High Priority)

**Problem**: The API layer (`src/cljs/wine_cellar/api.cljs`) is heavily coupled with state management logic that should be in calling components.

**Examples**:
- `fetch-classifications` (lines 84-89) directly manipulates app-state
- `fetch-wines` (lines 127-138) manages loading states and data
- Nearly identical state management patterns across create/update/delete operations

**Recommendation**: Refactor API functions to return promises/channels and let calling components handle state updates. Create separate data-fetching functions and state-management functions.

### 2. Code Repetition

**Pattern 1: Repetitive State Management**
- Lines 91-102, 104-112, 114-122 in api.cljs show nearly identical patterns for create/update/delete operations

**Pattern 2: Editable Field Repetition**
- Lines 29-75 in wine detail: Multiple similar editable field implementations
- `editable-location`, `editable-purveyor`, `editable-purchase-date`, `editable-price` all follow the same pattern

**Pattern 3: Form Field Validation**
- Validation logic scattered across form components
- Similar validation patterns repeated for different fields

**Recommendation**: Create generic factory functions and shared validation utilities.

### 3. Misplaced Logic

**Issue 1: Business Logic in View Components**
- Lines 167-171 in wine_chat.cljs: Wine filtering logic embedded in chat component
- Should be moved to dedicated state management or utility namespace

**Issue 2: Navigation Logic in API Layer**
- Lines 177-178 in api.cljs: Browser history manipulation mixed with API calls
- Navigation concerns should be separated from API layer

### 4. Component Structure Issues

**Issue 1: Oversized Wine Detail Component**
- Lines 325-664 in wine detail: Single massive component with too many responsibilities
- Handles image management, multiple editable fields, tasting notes, wine varieties, action buttons, and state management
- Should be broken into smaller, focused components like `WineBasicInfo`, `WineImages`, `WineTastingWindow`

**Issue 2: God Object App-State**
- App-state accessed directly throughout codebase with deeply nested paths
- Examples: `[:chat :open?]`, `[:new-wine :vintage]`, `[:window-suggestion :message]`
- Should use focused state management namespaces with specific update functions

**Issue 3: Mixed Concerns in Form Components**
- Lines 96-131 in wine form: Form validation, AI integration, and DOM manipulation all mixed together

### 5. Hacks and Workarounds

**Issue 1: Uncontrolled Component Workaround**
- Lines 183-202 in form.cljs: Complex uncontrolled text area with local atom optimization
- Lines 163-181: Another text area with local atom for performance

**Issue 2: Material-UI Workaround**
- Lines 289-294 in form.cljs: Workaround comment for Material-UI bug

**Issue 3: Complex Input Event Handling**
- Lines 196-200 in wine_chat.cljs: Manual DOM event triggering

### 6. Validation Logic Distribution (High Priority)

**Problem**: Validation logic is scattered across multiple layers with significant duplication and inconsistency.

**Current Distribution**:
- **Database Layer**: PostgreSQL constraints in `schema.clj` (NOT NULL, CHECK constraints, ranges)
- **Backend API**: Clojure spec validation in `routes.clj` (required fields, types, ranges)
- **Frontend Forms**: Manual validation in form components (same business rules re-implemented)

**Specific Duplications**:

1. **Required Field Validation**:
   - Backend: Clojure spec defines required fields (routes.clj:66-67)
   - Frontend: Manual validation checks same fields (wines/form.cljs:99-107)
   - Database: NOT NULL constraints enforce same requirements

2. **Range Validation**:
   - Rating validation duplicated across all three layers (1-100 range)
   - Database: CHECK constraint (schema.clj:60-61)
   - Backend: Spec validation (routes.cljs:39)
   - Frontend: Number field constraints (tasting_notes/form.cljs:137-138)

3. **Format Validation**:
   - Location validation implemented in shared utility but still checked in multiple places
   - Error messages inconsistent across layers

**Impact**: 
- Business rules changes require updates in 3+ places
- Inconsistent error messages confuse users
- Maintenance overhead and bug risk

**Recommendation**: Create centralized validation schemas using `.cljc` files with shared specs that work across frontend/backend, or migrate to Malli for better cross-platform validation.

## Recommended Improvements

1. **Refactor API Layer**: Separate data fetching from state management
2. **Centralize Validation Logic**: Create shared validation schemas to eliminate duplication across database/backend/frontend layers
3. **Create Generic Components**: Build reusable field components to reduce repetition
4. **State Management Refactor**: Use more focused state atoms or implement a proper state management pattern
5. **Component Decomposition**: Break large components into smaller, single-purpose components
6. **Business Logic Extraction**: Move domain logic out of view components into dedicated namespaces
7. **Event Handling Cleanup**: Replace DOM manipulation hacks with proper React patterns

## Impact Assessment

The most impactful improvement would be **refactoring the API layer** to separate data fetching from state management. This would:
- Make components more testable
- Reduce coupling between layers
- Improve code reusability
- Make state flow more predictable

The codebase shows good Clojure/ClojureScript practices in many areas but would benefit significantly from these architectural improvements to enhance maintainability and reduce technical debt.