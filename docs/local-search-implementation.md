# Implementation Plan: In-Conversation Search

## Goals
- Allow keyword search within an active conversation.
- Highlight all matches safely without breaking Markdown links.
- Navigate between matches using Up/Down arrows with auto-scrolling.

## Tasks

### 1. Data Structure & State Management
- [x] Define search state in `[:chat]` within `app-state`:
    - `:local-search-term` (string)
    - `:search-matches` (vector of `{:message-id, :match-id}`)
    - `:current-match-index` (int)
- [x] Implement a **Ref Map** in the `chat-messages` component to map `message-id -> DOM element`.

### 2. Safe Highlighting Pipeline
- [x] Refactor `render-text-with-links` into `render-rich-text`.
- [x] Implement Stage 1: Split by Markdown links `[title](wine:id)`.
- [x] Implement Stage 2: Split remaining text chunks by `:local-search-term`.
- [x] Implement Stage 3: Wrap matches in styled `<span>`.
- [x] Implement Stage 4: Differentiate "Active" match via `:current-match-index`.

### 3. Match Calculation Logic
- [x] Write logic to scan `messages` and build `:search-matches`.
- [x] Escaping logic for search terms (regex safety).

### 4. Find Bar UI
- [x] Create `local-search-controls` component in chat header.
- [x] Show `X of Y` match counter.
- [x] Add Up/Down arrows and Close button.

### 5. Navigation & Scrolling
- [x] Implement scroll-to-ref logic when `:current-match-index` changes.
- [x] Ensure smooth scrolling and centering.

### 6. Integration
- [x] Connect Sidebar search term to `:local-search-term` on click.
- [x] Verification across various wine/link combinations.
