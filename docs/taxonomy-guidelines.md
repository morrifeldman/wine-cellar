# Wine Taxonomy Guidelines

This document defines the structural rules for classifying wines in the Wine Cellar database. It serves as the "source of truth" for AI agents and consistency scripts.

## Core Philosophy: "Wine-Centric" over "Political"
The goal is to organize wines by **how they are collected and consumed**, not necessarily by strict political administrative divisions.

## Hierarchy Definitions

### 1. Country
The nation of origin.
*   *Examples:* France, Italy, Spain, United States.

### 2. Region
The primary wine-growing area. This should be the level at which a collector thinks about "sections" of their cellar.
*   **Old World:**
    *   *France:* Bordeaux, Burgundy, Rhône, Champagne, Loire.
    *   *Italy:* Tuscany, Piedmont, Veneto, Sicily.
    *   *Spain:* Catalonia, Castile and León, Rioja.
*   **New World:**
    *   *California:* Use specific wine areas: "Napa Valley", "Sonoma County", "Paso Robles", "Central Coast".
    *   *Oregon:* "Willamette Valley" or "Oregon" (if broad).

### 3. Appellation
The specific legal named place (AOC, DOC, AVA) within the Region.
*   **Rule of Redundancy:** If a wine has no sub-appellation (e.g., a generic Napa Cabernet), repeat the Region name in the Appellation field.
    *   *Napa Example:* Region: **Napa Valley**, Appellation: **Napa Valley**.
    *   *Bordeaux Example:* Region: **Bordeaux**, Appellation: **Bordeaux**.
    *   *Rioja Example:* Region: **Rioja**, Appellation: **Rioja**.
*   *Specifics:*
    *   *Bordeaux:* Pauillac, Saint-Julien, Pomerol, Haut-Médoc.
    *   *Napa Valley:* Oakville, Rutherford, Stags Leap District.
    *   *Sonoma County:* Russian River Valley, Knights Valley, Dry Creek Valley.

### 4. Appellation Tier
The regulatory status acronym.
*   *List:* AOC, AOP, DOCG, DOC, IGT, DO, DOQ, DOCa, AVA.
*   *Usage:* Always use the appropriate acronym even if it feels redundant (e.g., "DOCG" for Barolo).

### 5. Classification
Quality or site-specific ranking officially recognized by the region.
*   *Burgundy:* Grand Cru, Premier Cru.
*   *Bordeaux:* Premier Cru Classé, Grand Cru Classé.
*   *Spain:* Vi de Vila, Vino de Villa.
*   *Note:* Do **NOT** put regulatory tiers (AVA, DOC) here.

### 6. Vineyard
The specific named vineyard site (Lieu-dit, Pago, Single Vineyard).
*   *Usage:* "To Kalon", "Les Clos", "Cannubi".
*   *Spain (Village Wines):* For "Vi de Vila", put the Village Name here (e.g., "Gratallops").

## Specific Regional Rules

### California
*   **Region:** **Napa Valley**
    *   *Appellations:* Oakville, Rutherford, Yountville, Mount Veeder, Napa Valley (generic).
*   **Region:** **Sonoma County**
    *   *Appellations:* Russian River Valley, Sonoma Coast, Alexander Valley, Knights Valley.
*   **Region:** **Paso Robles**
    *   *Appellations:* Adelaida District, Willow Creek District, Paso Robles (generic).

### Spain
*   **Region:** **Catalonia**
    *   *Appellations:* Priorat, Montsant, Penedès.
*   **Region:** **Castile and León**
    *   *Appellations:* Bierzo, Ribera del Duero.
*   **Region:** **Rioja**
    *   *Appellations:* Rioja.

### France (Bordeaux)
*   **Region:** **Bordeaux**
    *   *Appellations:* Bordeaux (generic), Haut-Médoc, Pauillac, Saint-Émilion Grand Cru.
    *   *Metadata:* Use `metadata.commune` for "Commune de..." info (e.g., Cussac-Fort-Médoc).
