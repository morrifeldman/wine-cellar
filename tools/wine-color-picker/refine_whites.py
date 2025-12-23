from PIL import ImageColor
import colorsys

# Current values (Saturation Weighted)
colors = {
   'pale-straw':      "#dddac2",
   'medium-straw':    "#dad4a6",
   'deep-straw':      "#e6e19f",
   'pale-yellow':     "#f4f1bb",
   'medium-yellow':   "#e2db7d",
   'deep-yellow':     "#e5d649",
   'pale-gold':       "#e1d7a1",
   'medium-gold':     "#e6d580",
   'deep-gold':       "#f2d063",
   'pale-brown':      "#daa235",
   'medium-brown':    "#bf7a29",
   'deep-brown':      "#74411d",
   'pale-amber':      "#e8b249",
   'medium-amber':    "#e79526",
   'deep-amber':      "#de7129",
   'pale-copper':     "#ebb78f",
   'medium-copper':   "#eb8d4e",
   'deep-copper':     "#df6d34",
   'pale-salmon':     "#e7b3a9",
   'medium-salmon':   "#f08d79",
   'deep-salmon':     "#ea6445",
   'pale-pink':       "#f2c2c2",
   'medium-pink':     "#e77c81",
   'deep-pink':       "#e05162",
   'pale-ruby':       "#8d1631",
   'medium-ruby':     "#8e192f",
   'deep-ruby':       "#781b2d",
   'pale-purple':     "#951940",
   'medium-purple':   "#721a3a",
   'deep-purple':     "#571329",
   'pale-garnet':     "#bf2d24",
   'medium-garnet':   "#951c20",
   'deep-garnet':     "#731714",
   'pale-tawny':      "#bb4d26",
   'medium-tawny':    "#983d20",
   'deep-tawny':      "#762317"
}

def adjust_color(hex_c, sat_factor=0.7, val_factor=1.1):
    rgb = ImageColor.getrgb(hex_c)
    h, s, v = colorsys.rgb_to_hsv(rgb[0]/255.0, rgb[1]/255.0, rgb[2]/255.0)
    
    # Adjust
    s = max(0, min(1, s * sat_factor))
    v = max(0, min(1, v * val_factor))
    
    # Convert back
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return '#{:02x}{:02x}{:02x}'.format(int(r*255), int(g*255), int(b*255))

print(";; Refined Wine Colors")
print("(def wine-folly-colors")
print("  {")

whites = ['pale-straw', 'medium-straw', 'deep-straw', 
          'pale-yellow', 'medium-yellow', 'deep-yellow',
          'pale-gold', 'medium-gold', 'deep-gold']

for name, hex_c in colors.items():
    final_hex = hex_c
    if name in whites:
        # Desaturate and Brighten whites significantly
        # 'pale' needs more cleaning
        if 'pale' in name:
            final_hex = adjust_color(hex_c, sat_factor=0.4, val_factor=1.15)
        elif 'medium' in name:
            final_hex = adjust_color(hex_c, sat_factor=0.6, val_factor=1.1)
        else:
            final_hex = adjust_color(hex_c, sat_factor=0.8, val_factor=1.05)
            
    print(f"   :{name.ljust(15)} \"{final_hex}\"    ")
print("  })")
