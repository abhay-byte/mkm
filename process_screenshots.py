import os
import glob
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_rounded_mask(size, radius):
    mask = Image.new('L', size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, size[0], size[1]), radius=radius, fill=255)
    return mask

def create_background(size):
    # Small size for fast heavy blur
    small_size = (size[0]//4, size[1]//4)
    glow = Image.new('RGB', small_size, '#0a0a0f')
    draw = ImageDraw.Draw(glow)
    
    # Draw soft glowing orbs
    draw.ellipse((-100, -100, 250, 300), fill='#4b1d6e')  # Deep Purple
    draw.ellipse((small_size[0]-200, small_size[1]-250, small_size[0]+100, small_size[1]+100), fill='#004859') # Deep Cyan
    
    glow = glow.filter(ImageFilter.GaussianBlur(30))
    base = glow.resize(size, Image.Resampling.LANCZOS).convert('RGBA')
    
    # Draw subtle geometric pattern (diagonal lines)
    pattern = Image.new('RGBA', size, (0,0,0,0))
    pattern_draw = ImageDraw.Draw(pattern)
    spacing = 60
    for i in range(-size[1]*2, size[0] + size[1]*2, spacing):
        pattern_draw.line((i, 0, i + size[1]*2, size[1]*2), fill=(255, 255, 255, 8), width=2)
        
    base.paste(pattern, (0, 0), pattern)
    return base.convert('RGB')

def generate_store_listing(img_path, out_path, title, subtitle, icon_path):
    base_w, base_h = 1080, 1920
    
    # Create stylized background
    base = create_background((base_w, base_h))
    
    # Load and process icon
    icon_size = 160
    try:
        icon = Image.open(icon_path).convert('RGBA')
        icon = icon.resize((icon_size, icon_size), Image.Resampling.LANCZOS)
        # Give icon rounded corners just in case it's a square
        icon_mask = create_rounded_mask((icon_size, icon_size), 35)
        icon_final = Image.new('RGBA', (icon_size, icon_size), (0,0,0,0))
        icon_final.paste(icon, (0,0), icon_mask)
        
        # Paste icon
        icon_y = 100
        icon_x = (base_w - icon_size) // 2
        base.paste(icon_final, (icon_x, icon_y), icon_final)
        
        text_y = icon_y + icon_size + 30
    except Exception as e:
        print(f"Could not load icon: {e}")
        text_y = 150
    
    # Add text
    draw = ImageDraw.Draw(base)
    try:
        font_title = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 60)
        font_sub = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 40)
    except:
        font_title = ImageFont.load_default()
        font_sub = ImageFont.load_default()
        
    # Title
    t_width = draw.textlength(title, font=font_title)
    draw.text(((base_w - t_width)/2, text_y), title, fill="white", font=font_title)
    
    # Subtitle
    s_width = draw.textlength(subtitle, font=font_sub)
    draw.text(((base_w - s_width)/2, text_y + 90), subtitle, fill="#b5b5c9", font=font_sub)
    
    # Load and resize screenshot
    screenshot = Image.open(img_path).convert('RGBA')
    target_w = 900
    ratio = target_w / float(screenshot.size[0])
    target_h = int(float(screenshot.size[1]) * ratio)
    screenshot = screenshot.resize((target_w, target_h), Image.Resampling.LANCZOS)
    
    # Apply rounded corners to screenshot
    mask = create_rounded_mask(screenshot.size, 50)
    screenshot.putalpha(mask)
    
    # Add shadow
    shadow_offset = 30
    shadow = Image.new('RGBA', (screenshot.size[0] + shadow_offset, screenshot.size[1] + shadow_offset), (0,0,0,0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle((0, 0, screenshot.size[0], screenshot.size[1]), radius=50, fill=(0,0,0,140))
    shadow = shadow.filter(ImageFilter.GaussianBlur(25))
    
    # Position (bottom centered)
    y_pos = text_y + 200
    x_pos = (base_w - target_w) // 2
    
    base.paste(shadow, (x_pos + 15, y_pos + 15), shadow)
    base.paste(screenshot, (x_pos, y_pos), screenshot)
    
    base.save(out_path, format='PNG')
    print(f"Saved {out_path}")

def process_images():
    src_dir = 'docs/storelisting/img'
    out_dir = 'docs/storelisting/portrait'
    icon_path = 'fastlane/metadata/android/en-US/images/icon.png'
    os.makedirs(out_dir, exist_ok=True)
    
    mappings = {
        'screenshot_1.png': ('Minimal Kernel Manager', 'Advanced kernel management & monitoring'),
        'screenshot_cpu.png': ('CPU Management', 'Monitor utilization & active clusters'),
        'screenshot_gpu.png': ('GPU Control', 'Fine-tune graphics frequencies'),
        'screenshot_ram.png': ('RAM & Swap', 'Optimize memory performance'),
        'screenshot_battery.png': ('Battery Monitor', 'Track power efficiency'),
        'screenshot_settings.png': ('Customization', 'Configure app appearance & features'),
        'screenshot_overlay.png': ('Floating Overlay', 'Monitor stats from any app'),
        'screenshot_power.png': ('Power Management', 'Optimize device power usage'),
        'screenshot_storage.png': ('Storage Status', 'Monitor disk space & I/O')
    }
    
    images = glob.glob(os.path.join(src_dir, '*.png'))
    images.sort()
    
    for i, img_path in enumerate(images, 1):
        filename = os.path.basename(img_path)
        title, subtitle = mappings.get(filename, ('Minimal Kernel Manager', 'Advanced System Monitor'))
        
        out_name = f'store_listing_{i}.png'
        out_path = os.path.join(out_dir, out_name)
        
        generate_store_listing(img_path, out_path, title, subtitle, icon_path)

if __name__ == '__main__':
    process_images()
