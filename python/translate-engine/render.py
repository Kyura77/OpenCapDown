import os
from PIL import Image, ImageDraw, ImageFont

def get_font(size):
    # Try common manga/comic style fonts or system standard fonts
    font_paths = [
        "fonts/animeace2_reg.ttf", # Project local if bundled
        "C:\\Windows\\Fonts\\comic.ttf", # Windows Comic Sans
        "C:\\Windows\\Fonts\\arial.ttf", # Windows Arial
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", # Ubuntu DejaVu
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf", # Linux standard
    ]
    
    for path in font_paths:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()

def wrap_text(text, font, max_width):
    words = text.split()
    lines = []
    current_line = []
    
    # If font is the default un-scalable font, return simple split
    if isinstance(font, ImageFont.ImageFont):
        # Default font has fixed size, wrap by character count approximation
        char_w = 6
        max_chars = max(1, int(max_width / char_w))
        current_len = 0
        for word in words:
            if current_len + len(word) + 1 <= max_chars:
                current_line.append(word)
                current_len += len(word) + 1
            else:
                if current_line:
                    lines.append(' '.join(current_line))
                    current_line = [word]
                    current_len = len(word)
                else:
                    lines.append(word)
        if current_line:
            lines.append(' '.join(current_line))
        return lines

    for word in words:
        test_line = ' '.join(current_line + [word])
        bbox = font.getbbox(test_line)
        w = bbox[2] - bbox[0]
        if w <= max_width:
            current_line.append(word)
        else:
            if current_line:
                lines.append(' '.join(current_line))
                current_line = [word]
            else:
                lines.append(word)
    if current_line:
        lines.append(' '.join(current_line))
    return lines

def render_page(image, translations, min_font_size=10, max_font_size=36):
    # Convert OpenCV image (numpy array) to PIL Image
    # Wait, image can be either a PIL Image, path, or numpy array. Let's handle all!
    if isinstance(image, str):
        pil_img = Image.open(image)
    elif isinstance(image, Image.Image):
        pil_img = image.copy()
    else:
        # Assume numpy array from OpenCV (BGR) -> RGB PIL
        import cv2
        rgb_img = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        pil_img = Image.fromarray(rgb_img)

    draw = ImageDraw.Draw(pil_img)

    for item in translations:
        box = item["box"]
        text = item["translation"]
        if not text or text.strip() == "":
            continue

        x1, y1, x2, y2 = box
        box_w = x2 - x1
        box_h = y2 - y1

        # Search for best fitting font size
        best_size = min_font_size
        best_lines = [text]

        # Don't try sizing if default font is returned (default is unscalable)
        font_test = get_font(12)
        if not isinstance(font_test, ImageFont.ImageFont):
            for size in range(max_font_size, min_font_size - 1, -2):
                font = get_font(size)
                lines = wrap_text(text, font, box_w)
                if not lines:
                    continue

                total_height = 0
                max_line_w = 0
                for line in lines:
                    bbox = font.getbbox(line)
                    lh = bbox[3] - bbox[1]
                    lw = bbox[2] - bbox[0]
                    total_height += lh + 4
                    max_line_w = max(max_line_w, lw)

                if total_height <= box_h and max_line_w <= box_w:
                    best_size = size
                    best_lines = lines
                    break
        else:
            # Fallback to character-count wrapping using default font
            best_lines = wrap_text(text, font_test, box_w)

        font = get_font(best_size)

        # Draw centered text lines
        total_height = 0
        line_heights = []
        for line in best_lines:
            if isinstance(font, ImageFont.ImageFont):
                lh = 12
            else:
                bbox = font.getbbox(line)
                lh = bbox[3] - bbox[1]
            line_heights.append(lh)
            total_height += lh + 4

        y = y1 + (box_h - total_height) / 2
        for i, line in enumerate(best_lines):
            if isinstance(font, ImageFont.ImageFont):
                lw = len(line) * 6
            else:
                bbox = font.getbbox(line)
                lw = bbox[2] - bbox[0]
            
            x = x1 + (box_w - lw) / 2
            
            # Render a thin outline for better contrast
            outline_color = "black"
            draw.text((x - 1, y), line, font=font, fill=outline_color)
            draw.text((x + 1, y), line, font=font, fill=outline_color)
            draw.text((x, y - 1), line, font=font, fill=outline_color)
            draw.text((x, y + 1), line, font=font, fill=outline_color)
            
            # Render white text
            draw.text((x, y), line, font=font, fill="white")
            y += line_heights[i] + 4

    return pil_img
