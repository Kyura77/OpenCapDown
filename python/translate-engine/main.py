import os
import sys
import argparse
import shutil
from tqdm import tqdm
from PIL import Image

# Import local modules
from download_models import main as download_models_main
from detect import TextDetector
from translate import translate_page
from inpaint import Inpainter
from render import render_page
from packer import pack_cbz

def main():
    parser = argparse.ArgumentParser(description="Manga Translation Engine using ONNX & Gemini")
    parser.add_argument("--input", required=True, help="Input directory containing manga images")
    parser.add_argument("--output", required=True, help="Output directory or file path")
    parser.add_argument("--target-lang", default="pt-BR", help="Target translation language (default: pt-BR)")
    parser.add_argument("--gemini-key", help="Gemini API Key")
    parser.add_argument("--format", choices=["folder", "cbz"], default="cbz", help="Output format (default: cbz)")
    args = parser.parse_args()

    # Step 1: Ensure models are downloaded
    print("Checking models...")
    download_models_main()

    # Initialize detectors and inpainters
    print("Loading models...")
    detector = TextDetector()
    inpainter = Inpainter()

    # Create directories
    os.makedirs(args.input, exist_ok=True)
    
    if args.format == "cbz":
        if args.output.endswith(".cbz"):
            temp_output_dir = args.output.replace(".cbz", "")
            cbz_output_path = args.output
        else:
            temp_output_dir = args.output
            cbz_output_path = f"{args.output}.cbz"
    else:
        temp_output_dir = args.output
        cbz_output_path = None

    os.makedirs(temp_output_dir, exist_ok=True)

    # Get input files
    supported_extensions = (".jpg", ".jpeg", ".png", ".webp", ".bmp")
    files = sorted([f for f in os.listdir(args.input) if f.lower().endswith(supported_extensions)])

    if not files:
        print(f"No images found in input directory: {args.input}")
        sys.exit(0)

    print(f"Translating {len(files)} pages to {args.target_lang}...")
    for filename in tqdm(files, desc="Translating pages"):
        input_path = os.path.join(args.input, filename)
        output_path = os.path.join(temp_output_dir, filename)

        if os.path.exists(output_path):
            continue

        try:
            # 1. Detect boxes
            boxes = detector.detect(input_path)

            if not boxes:
                # No text bubbles detected, copy original
                shutil.copy(input_path, output_path)
                continue

            # 2. Translate text inside boxes
            try:
                translations = translate_page(input_path, boxes, target_lang=args.target_lang, api_key=args.gemini_key)
            except Exception as e:
                print(f"\n[translation-error] Failed to translate page {filename}: {e}. Skipping translation for this page.")
                shutil.copy(input_path, output_path)
                continue

            # 3. Inpaint
            inpainted = inpainter.inpaint(input_path, boxes)

            # 4. Render text
            rendered_pil = render_page(inpainted, translations)

            # Save
            rendered_pil.save(output_path)

        except Exception as e:
            print(f"\n[error] Failed to process page {filename}: {e}")
            # Fallback to copy original
            shutil.copy(input_path, output_path)

    # Pack to CBZ if requested
    if args.format == "cbz" and cbz_output_path:
        print(f"Packaging to CBZ: {cbz_output_path}")
        pack_cbz(temp_output_dir, cbz_output_path)
        try:
            shutil.rmtree(temp_output_dir)
        except Exception as e:
            print(f"Failed to delete temp dir {temp_output_dir}: {e}")

    print("Translation completed successfully!")

if __name__ == "__main__":
    main()
