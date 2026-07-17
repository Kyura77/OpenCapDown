import os
import json
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

    # Call Gemini 2.5 Flash model
    response = client.models.generate_content(
        model='gemini-2.5-flash',
        contents=[img, prompt],
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            temperature=0.2
        )
    )

    try:
        # Parse JSON output from the model
        data = json.loads(response.text)
        return data
    except Exception as e:
        print(f"Failed to parse JSON response:\n{response.text}")
        raise e
