# WSET Structured Tasting Notes Feature

## Overview

This document outlines the development of a structured tasting notes feature based on the WSET (Wine & Spirit Education Trust) grid system, with typography and presentation inspiration from Wine Folly.

## WSET Level 3 Grid Structure (Implementation Target)

Perfect for serious wine cellar management - comprehensive analysis with cellar-relevant features.

### APPEARANCE
- **Clarity**: clear – hazy (faulty?)
- **Intensity**: pale – medium – deep
- **Colour**: 
  - White: lemon-green – lemon – gold – amber – brown
  - Rosé: pink – salmon – orange
  - Red: purple – ruby – garnet – tawny – brown
- **Other observations**: e.g. legs/tears, deposit, pétillance, bubbles

### NOSE
- **Condition**: clean – unclean (faulty?)
- **Intensity**: light – medium(-) – medium – medium(+) – pronounced
- **Aroma characteristics**: e.g. primary, secondary, tertiary
- **Development**: youthful – developing – fully developed – tired/past its best

### PALATE
- **Sweetness**: dry – off-dry – medium-dry – medium-sweet – sweet – luscious
- **Acidity**: low – medium(-) – medium – medium(+) – high
- **Tannin**: low – medium(-) – medium – medium(+) – high
- **Alcohol**: low – medium – high (fortified wines: low – medium – high)
- **Body**: light – medium(-) – medium – medium(+) – full
- **Mousse**: delicate – creamy – aggressive (for sparkling wines)
- **Flavour intensity**: light – medium(-) – medium – medium(+) – pronounced
- **Flavour characteristics**: e.g. primary, secondary, tertiary
- **Finish**: short – medium(-) – medium – medium(+) – long

### CONCLUSIONS
#### Assessment of Quality
- **Quality level**: faulty – poor – acceptable – good – very good – outstanding
- **Level of readiness**: too young – can drink now, but has potential for ageing – drink now: not suitable for ageing or further ageing – too old

## Feature Components

### 1. WSET Level 3 Form Structure

#### APPEARANCE (wine type derived from existing wine record)
- **Clarity**: clear – hazy (faulty?)
- **Intensity**: pale – medium – deep
- **Colour**: Dropdown based on wine style (lemon-green/lemon/gold/amber/brown for whites, etc.)
- **Other observations**: Checkboxes for legs/tears, deposit, pétillance, bubbles + free text

#### NOSE
- **Condition**: clean – unclean (faulty?)
- **Intensity**: light – medium(-) – medium – medium(+) – pronounced
- **Aroma characteristics**: Multi-select from full WSET lexicon hierarchy + ad-hoc additions
- **Development**: youthful – developing – fully developed – tired/past its best

#### PALATE
- **Sweetness**: dry – off-dry – medium-dry – medium-sweet – sweet – luscious
- **Acidity**: low – medium(-) – medium – medium(+) – high
- **Tannin**: low – medium(-) – medium – medium(+) – high (reds only)
- **Alcohol**: low – medium – high (with fortified option)
- **Body**: light – medium(-) – medium – medium(+) – full
- **Mousse**: delicate – creamy – aggressive (sparkling only)
- **Flavour intensity**: light – medium(-) – medium – medium(+) – pronounced
- **Flavour characteristics**: 
  - Same multi-select interface as nose
  - "Copy from nose" button to auto-populate
  - Visual indicators for: nose-only, palate-only, shared descriptors
  - Diff highlighting shows what changed from nose to palate
- **Finish**: short – medium(-) – medium – medium(+) – long

#### CONCLUSIONS
- **Quality level**: faulty – poor – acceptable – good – very good – outstanding
- **Level of readiness**: too young – can drink now, but has potential for ageing – drink now: not suitable for ageing – too old
- **Notes**: Free text area for additional thoughts

### 2. WSET Level 3 Lexicon Taxonomy

#### Primary Aromas and Flavours
*The aromas and flavours of the grape and alcoholic fermentation*

- **Floral**: acacia, honeysuckle, chamomile, elderflower, geranium, blossom, rose, violet
- **Green fruit**: apple, gooseberry, pear, pear drop, quince, grape
- **Citrus fruit**: grapefruit, lemon, lime (juice or zest?), orange peel, lemon peel
- **Stone fruit**: peach, apricot, nectarine
- **Tropical fruit**: banana, lychee, mango, melon, passion fruit, pineapple
- **Red fruit**: redcurrant, cranberry, raspberry, strawberry, red cherry, red plum
- **Black fruit**: blackcurrant, blackberry, bramble, blueberry, black cherry, black plum
- **Dried/cooked fruit**: fig, prune, raisin, sultana, kirsch, jamminess, baked/stewed fruits, preserved fruits
- **Herbaceous**: green bell pepper (capsicum), grass, tomato leaf, asparagus, blackcurrant leaf
- **Herbal**: eucalyptus, mint, medicinal, lavender, fennel, dill
- **Pungent spice**: black/white pepper, liquorice
- **Other**: flint, wet stones, wet wool

#### Secondary Aromas and Flavours
*The aromas and flavours of post-fermentation winemaking*

- **Yeast (lees, autolysis)**: biscuit, bread, toast, pastry, brioche, bread dough, cheese
- **MLF**: butter, cheese, cream
- **Oak**: vanilla, cloves, nutmeg, coconut, butterscotch, toast, cedar, charred wood, smoke, chocolate, coffee, resinous

#### Tertiary Aromas and Flavours
*The aromas and flavours of maturation*

- **Deliberate oxidation**: almond, marzipan, hazelnut, walnut, chocolate, coffee, toffee, caramel
- **Fruit development**: dried apricot, marmalade, dried apple, dried banana (whites); fig, prune, tar, dried blackberry, dried cranberry (reds)
- **Bottle age**: petrol, kerosene, cinnamon, ginger, nutmeg, toast, nutty, mushroom, hay, honey (whites); leather, earth, forest floor, mushroom, game, tobacco, tar, smoke (reds)

### 3. Lexicon Data Structure

#### EDN Format for Easy Integration
```clojure
{:primary
 {:floral ["acacia" "honeysuckle" "chamomile" "elderflower" "geranium" "blossom" "rose" "violet"]
  :green-fruit ["apple" "gooseberry" "pear" "pear drop" "quince" "grape"]
  :citrus-fruit ["grapefruit" "lemon" "lime (juice or zest?)" "orange peel" "lemon peel"]
  :stone-fruit ["peach" "apricot" "nectarine"]
  :tropical-fruit ["banana" "lychee" "mango" "melon" "passion fruit" "pineapple"]
  :red-fruit ["redcurrant" "cranberry" "raspberry" "strawberry" "red cherry" "red plum"]
  :black-fruit ["blackcurrant" "blackberry" "bramble" "blueberry" "black cherry" "black plum"]
  :dried-cooked-fruit ["fig" "prune" "raisin" "sultana" "kirsch" "jamminess" "baked/stewed fruits" "preserved fruits"]
  :herbaceous ["green bell pepper (capsicum)" "grass" "tomato leaf" "asparagus" "blackcurrant leaf"]
  :herbal ["eucalyptus" "mint" "medicinal" "lavender" "fennel" "dill"]
  :pungent-spice ["black/white pepper" "liquorice"]
  :other ["flint" "wet stones" "wet wool"]}
 :secondary
 {:yeast ["biscuit" "bread" "toast" "pastry" "brioche" "bread dough" "cheese"]
  :mlf ["butter" "cheese" "cream"]
  :oak ["vanilla" "cloves" "nutmeg" "coconut" "butterscotch" "toast" "cedar" "charred wood" "smoke" "chocolate" "coffee" "resinous"]}
 :tertiary
 {:deliberate-oxidation ["almond" "marzipan" "hazelnut" "walnut" "chocolate" "coffee" "toffee" "caramel"]
  :fruit-development-white ["dried apricot" "marmalade" "dried apple" "dried banana"]
  :fruit-development-red ["fig" "prune" "tar" "dried blackberry" "dried cranberry" "cooked blackberry" "cooked red plum"]
  :bottle-age-white ["petrol" "kerosene" "cinnamon" "ginger" "nutmeg" "toast" "nutty" "mushroom" "hay" "honey"]
  :bottle-age-red ["leather" "earth" "forest floor" "mushroom" "game" "tobacco" "tar" "smoke"]}}
```

### 4. Nose-to-Palate Workflow

#### Interface Design
- **Nose section**: Tag-based multi-select from hierarchical WSET lexicon
- **Palate section**: Same interface + "Copy from nose" button
- **Difference visualization**:
  - Green badges: Shared between nose and palate
  - Blue badges: Nose-only descriptors
  - Orange badges: Palate-only descriptors
  - Summary: "Added on palate: vanilla, leather" / "Lost from nose: grass, mint"

#### Data Structure
```clojure
{:nose-characteristics ["blackcurrant" "vanilla" "grass" "leather"]
 :palate-characteristics ["blackcurrant" "vanilla" "leather" "tobacco"]
 :nose-only ["grass"]           ; computed
 :palate-only ["tobacco"]       ; computed  
 :shared ["blackcurrant" "vanilla" "leather"]  ; computed
}
```

### 5. Implementation Plan

#### Phase 1: Core WSET Form
- WSET Level 3 structured form sections
- Hierarchical descriptor selection interface
- Nose-to-palate copy functionality
- Basic difference highlighting

#### Phase 2: Enhanced Analysis
- Advanced diff visualization
- Wine Folly-inspired typography
- Descriptor search/filter
- Mobile optimization

## Database Schema Design

### Extend Existing `tasting_notes` Table

Add JSON column to existing table for maximum flexibility:

```sql
ALTER TABLE tasting_notes 
ADD COLUMN wset_data JSONB;
```

### WSET JSON Schema

```json
{
  "note_type": "wset_level_3",
  "version": "1.0",
  "wset_wine_style": "RED",
  
  "appearance": {
    "clarity": "CLEAR",
    "intensity": "MEDIUM", 
    "colour": "RUBY",
    "other_observations": "legs, slight deposit, orange rim"
  },
  
  "nose": {
    "condition": "CLEAN",
    "intensity": "MEDIUM+",
    "development": "DEVELOPING",
    "aroma_characteristics": {
      "primary": {
        "floral": ["ROSE", "jasmine"],
        "black-fruit": ["BLACKCURRANT"],
        "herbaceous": ["GRASS"],
        "other": ["graphite", "pencil shavings"]
      },
      "secondary": {
        "oak": ["VANILLA"]
      },
      "tertiary": {
        "bottle-age": ["LEATHER"]
      }
    }
  },
  
  "palate": {
    "sweetness": "DRY",
    "acidity": "MEDIUM+",
    "tannin": "MEDIUM",
    "alcohol": "MEDIUM", 
    "body": "MEDIUM+",
    "mousse": null,
    "flavour_intensity": "PRONOUNCED",
    "finish": "LONG",
    "flavour_characteristics": {
      "primary": {
        "black-fruit": ["BLACKCURRANT"],
        "other": ["graphite"]
      },
      "secondary": {
        "oak": ["VANILLA"]
      },
      "tertiary": {
        "bottle-age": ["LEATHER", "tobacco note"]
      }
    }
  },
  
  "conclusions": {
    "quality_level": "VERY GOOD",
    "readiness": "DRINK OR HOLD", 
    "additional_notes": "Will benefit from 2-3 years cellaring"
  }
}
```

### UI Implementation Strategy

- **Entry Mode Toggle**: "Free-form" vs "WSET structured"
- **Backwards Compatible**: Existing text notes unchanged
- **Hybrid Option**: Both structured + additional free text
- **Form Logic**: `wset_wine_style` determines visible fields (tannin, mousse, etc.)

---

*JSON blob enables rapid iteration while maintaining full backwards compatibility.*