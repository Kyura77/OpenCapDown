import os
import json
import time
from google import genai
from google.genai import types
from PIL import Image

def translate_page(image_path, boxes, target_lang="pt-BR", api_key=None):
    if not api_key:
        api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY is not set.")

    client = genai.Client(api_key=api_key)
    
    # Open the image using PIL
    img = Image.open(image_path)
    
    # Formulate structured prompt
    prompt = f"""
You are an expert manga translator.
I have detected text bubbles at the following bounding box coordinates [x1, y1, x2, y2]: {boxes}
For each bounding box:
1. Locate the text bubble at those coordinates on the manga page.
2. Transcribe the original text inside that bubble.
3. Translate the transcribed text naturally and contextually into {target_lang}.
4. Return a JSON array matching this format:
[
  {{
    "box": [x1, y1, x2, y2],
    "text": "Original text inside the bubble",
    "translation": "Translated text in {target_lang}"
  }}
]
Make sure every box in the list has an entry. Return ONLY valid JSON matching the schema.
"""

    models_to_try = ['gemini-2.5-flash', 'gemini-2.0-flash-lite', 'gemini-1.5-flash']
    last_exception = None

    for model_name in models_to_try:
        for attempt in range(3):
            try:
                # Call Gemini
                response = client.models.generate_content(
                    model=model_name,
                    contents=[img, prompt],
                    config=types.GenerateContentConfig(
                        response_mime_type="application/json",
                        temperature=0.2
                    )
                )

                # Parse JSON output from the model
                data = json.loads(response.text)

                # Output validation: Verify if all input boxes are represented in the response
                box_keys = {tuple(b) for b in boxes}
                data_keys = set()
                for item in data:
                    b = item.get("box")
                    if b and len(b) == 4:
                        data_keys.add(tuple(b))

                missing_boxes = box_keys - data_keys
                if missing_boxes and attempt < 2:
                    print(f"\n[gemini-validation] Model {model_name} response missed boxes: {missing_boxes}. Retrying page...")
                    raise ValueError(f"Response missing boxes: {missing_boxes}")

                return data
            except Exception as e:
                last_exception = e
                delay_sec = 2.0 ** attempt
                print(f"\n[gemini-error] Model {model_name} failed (attempt {attempt + 1}/3): {e}. Retrying in {delay_sec}s...")
                time.sleep(delay_sec)
        
        print(f"\n[gemini-fallback] Model {model_name} failed all attempts. Trying next fallback...")

    raise last_exception or ValueError("Failed to translate page after attempting all models.")

