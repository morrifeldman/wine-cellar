from PIL import Image, ImageDraw
import colorsys

def rgb_to_hex(rgb):
    return '#{:02x}{:02x}{:02x}'.format(*rgb)

def main():
    img_path = 'tools/wine-color-picker/wine-colors.jpg'
    try:
        img = Image.open(img_path)
    except FileNotFoundError:
        print(f"Error: Could not find {img_path}")
        return

    width, height = img.size
    
    rows = 6
    cols = 6
    
    margin_top = 80
    margin_left = 40
    margin_right = 40
    margin_bottom = 200
    
    effective_width = width - margin_left - margin_right
    effective_height = height - margin_top - margin_bottom
    
    cell_w = effective_width / cols
    cell_h = effective_height / rows
    
    wine_names = [
        'pale-straw', 'medium-straw', 'deep-straw', 'pale-yellow', 'medium-yellow', 'deep-yellow',
        'pale-gold', 'medium-gold', 'deep-gold', 'pale-brown', 'medium-brown', 'deep-brown',
        'pale-amber', 'medium-amber', 'deep-amber', 'pale-copper', 'medium-copper', 'deep-copper',
        'pale-salmon', 'medium-salmon', 'deep-salmon', 'pale-pink', 'medium-pink', 'deep-pink',
        'pale-ruby', 'medium-ruby', 'deep-ruby', 'pale-purple', 'medium-purple', 'deep-purple',
        'pale-garnet', 'medium-garnet', 'deep-garnet', 'pale-tawny', 'medium-tawny', 'deep-tawny'
    ]
    
    results = {}
    
    print("Extracting colors using 'Top 50% Saturation' method...")
    
    for i, name in enumerate(wine_names):
        r = i // cols
        c = i % cols
        
        cx = margin_left + (c * cell_w) + (cell_w * 0.5)
        # 42% down - trying to hit the thickest part of the liquid
        cy = margin_top + (r * cell_h) + (cell_h * 0.42) 
        
        radius = 20
        
        candidates = []
        
        for y in range(int(cy)-radius, int(cy)+radius):
            for x in range(int(cx)-radius, int(cx)+radius):
                if x < 0 or x >= width or y < 0 or y >= height:
                    continue
                
                pix = img.getpixel((x, y))
                r_val, g_val, b_val = pix
                h, s, v = colorsys.rgb_to_hsv(r_val/255.0, g_val/255.0, b_val/255.0)
                
                # Exclude obvious junk
                # Too dark (black text/shadows)
                if v < 0.15: continue
                # Too bright/white (paper/highlights)
                if v > 0.90 and s < 0.05: continue
                
                candidates.append({'rgb': pix, 's': s})
        
        if candidates:
            # Sort by saturation (descending)
            candidates.sort(key=lambda x: x['s'], reverse=True)
            
            # Take top 50% saturated pixels (to avoid the washed out parts)
            # But if there are very few, take what we have.
            num_to_take = max(1, len(candidates) // 2)
            top_candidates = candidates[:num_to_take]
            
            r_avg = sum(p['rgb'][0] for p in top_candidates) // len(top_candidates)
            g_avg = sum(p['rgb'][1] for p in top_candidates) // len(top_candidates)
            b_avg = sum(p['rgb'][2] for p in top_candidates) // len(top_candidates)
            
            hex_code = rgb_to_hex((r_avg, g_avg, b_avg))
            results[name] = hex_code
        else:
            print(f"Warning: No valid pixels for {name}")
            results[name] = "#000000"

    print("\n;; Colors extracted from poster (Saturation Weighted)")
    print("(def wine-folly-colors")
    print("  {")
    for name in wine_names:
        hex_c = results.get(name, "#000000")
        print(f"   :{name.ljust(15)} \"{hex_c}\"    ")
    print("  })")

if __name__ == "__main__":
    main()