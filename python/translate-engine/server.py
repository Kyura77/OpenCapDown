import os
import shutil
import tempfile
import zipfile
from fastapi import FastAPI, UploadFile, File, Query, HTTPException
from fastapi.responses import FileResponse
from starlette.background import BackgroundTasks
import uvicorn

# Import local modules from translate-engine
from detect import TextDetector
from translate import translate_page
from inpaint import Inpainter
from render import render_page
from packer import pack_cbz
from download_models import main as download_models_main

app = FastAPI(title="OpenCapDown Translation Server", version="1.0.0")

# Lazy-loaded detectors/inpainters
detector = None
inpainter = None

def get_models():
    global detector, inpainter
    if detector is None or inpainter is None:
        print("Downloading/Checking models...")
        download_models_main()
        print("Initializing models...")
        detector = TextDetector()
        inpainter = Inpainter()
    return detector, inpainter

@app.get("/health")
def health():
    gemini_key = os.getenv("GEMINI_API_KEY")
    has_key = gemini_key is not None and len(gemini_key) > 0
    models_exist = os.path.exists("models/yolo-text-detector.onnx") and os.path.exists("models/lama-manga.onnx")
    return {
        "status": "ok",
        "gemini_api_key_configured": has_key,
        "models_exist": models_exist
    }

@app.post("/translate")
async def translate(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    target_lang: str = Query("pt-BR")
):
    # Ensure models are loaded
    try:
        det, inp = get_models()
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to load models: {str(e)}")

    # Create temporary directories
    temp_dir = tempfile.mkdtemp()
    input_dir = os.path.join(temp_dir, "input")
    output_dir = os.path.join(temp_dir, "output")
    os.makedirs(input_dir, exist_ok=True)
    os.makedirs(output_dir, exist_ok=True)

    zip_path = os.path.join(temp_dir, "input.zip")
    cbz_path = os.path.join(temp_dir, "output.cbz")

    try:
        # Save uploaded zip
        with open(zip_path, "wb") as f:
            shutil.copyfileobj(file.file, f)

        # Extract zip
        with zipfile.ZipFile(zip_path, "r") as z:
            z.extractall(input_dir)

        # Get input files
        supported_extensions = (".jpg", ".jpeg", ".png", ".webp", ".bmp")
        files = sorted([f for f in os.listdir(input_dir) if f.lower().endswith(supported_extensions)])

        if not files:
            raise HTTPException(status_code=400, detail="No valid images found in the uploaded zip file.")

        print(f"Server: translating {len(files)} pages to {target_lang}...")
        for filename in files:
            input_path = os.path.join(input_dir, filename)
            output_path = os.path.join(output_dir, filename)

            try:
                # 1. Detect boxes
                boxes = det.detect(input_path)

                if not boxes:
                    shutil.copy(input_path, output_path)
                    continue

                # 2. Translate
                try:
                    translations = translate_page(input_path, boxes, target_lang=target_lang)
                except Exception as e:
                    print(f"Gemini translation failed for {filename}: {e}")
                    shutil.copy(input_path, output_path)
                    continue

                # 3. Inpaint
                inpainted = inp.inpaint(input_path, boxes)

                # 4. Render
                rendered_pil = render_page(inpainted, translations)
                rendered_pil.save(output_path)

            except Exception as e:
                print(f"Error processing page {filename}: {e}")
                shutil.copy(input_path, output_path)

        # Pack to CBZ
        pack_cbz(output_dir, cbz_path)

        # Verify CBZ file exists and has content
        if not os.path.exists(cbz_path) or os.path.getsize(cbz_path) == 0:
            raise HTTPException(status_code=500, detail="Failed to generate output CBZ.")

        # Return file response and register cleanup task
        def cleanup():
            shutil.rmtree(temp_dir, ignore_errors=True)

        background_tasks.add_task(cleanup)
        return FileResponse(cbz_path, media_type="application/octet-stream", filename="translated.cbz")

    except Exception as e:
        shutil.rmtree(temp_dir, ignore_errors=True)
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    port = int(os.getenv("TRANSLATION_SERVER_PORT", 5050))
    uvicorn.run("server:app", host="0.0.0.0", port=port, reload=False)
