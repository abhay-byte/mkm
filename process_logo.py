#!/usr/bin/env python3
from PIL import Image, ImageDraw, ImageOps
import os

def make_circular_transparent(input_path, output_path, size):
    """
    Takes an image, removes white background, makes it circular, and saves it.
    """
    # Open and convert to RGBA
    img = Image.open(input_path).convert("RGBA")
    
    # Resize to target size
    img = img.resize((size, size), Image.Resampling.LANCZOS)
    
    # Create a mask for transparency (remove white background)
    data = img.getdata()
    new_data = []
    
    for item in data:
        # If pixel is mostly white (with some tolerance), make it transparent
        if item[0] > 240 and item[1] > 240 and item[2] > 240:
            new_data.append((255, 255, 255, 0))  # Transparent
        else:
            new_data.append(item)
    
    img.putdata(new_data)
    
    # Create circular mask
    mask = Image.new('L', (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size, size), fill=255)
    
    # Create output image
    output = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    output.paste(img, (0, 0))
    output.putalpha(mask)
    
    # Save the image
    output.save(output_path, 'PNG')
    print(f"Created: {output_path} ({size}x{size})")

# Define mipmap sizes for Android
sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

input_image = '/tmp/logo.png'
base_output_dir = '/home/flux/repos/swap-creator/app/src/main/res'

# Create logo for each density
for density, size in sizes.items():
    output_dir = os.path.join(base_output_dir, f'mipmap-{density}')
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, 'ic_launcher.png')
    make_circular_transparent(input_image, output_path, size)

print("\n✓ All logo files created successfully!")
