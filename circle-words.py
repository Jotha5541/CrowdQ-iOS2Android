import sys
from PIL import Image, ImageDraw, ImageFont


# 1. Setup constants
diameter = 50
text = sys.argv[1]
circle_color = sys.argv[2]

# 2. Create a square image with an Alpha channel (RGBA) for transparency
# We use diameter x diameter to fit the circle perfectly
img = Image.new('RGBA', (diameter, diameter), (255, 255, 255, 0))
draw = ImageDraw.Draw(img)

# 3. Draw the orange circle
# The bounding box is [top-left corner, bottom-right corner]
draw.ellipse([0, 0, diameter - 1, diameter - 1], fill=circle_color)

# 4. Add the text
try:
    # Use a basic font; adjust size to fit word into 50px
    font = ImageFont.load_default()
except lib:
    font = ImageFont.load_default()

# Calculate text position to center it
# Using textbbox to get dimensions (Pillow 8.0.0+)
bbox = draw.textbbox((0, 0), text, font=font)
text_width = bbox[2] - bbox[0]
text_height = bbox[3] - bbox[1]

position = ((diameter - text_width) / 2, (diameter - text_height) / 2 - 2)

# Draw the text (using black for contrast against orange)
draw.text(position, text, fill="black", font=font)

# 5. Save the result
img.save("circle.png")
img.show()
