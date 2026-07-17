import os
import urllib.request
from tqdm import tqdm

MODELS = {
    "yolo-text-detector.onnx": "https://huggingface.co/dhleong/manga-ocr-android/resolve/main/manga-text-detector.onnx",
    "lama-manga.onnx": "https://huggingface.co/mayocream/lama-manga-onnx/resolve/main/lama-manga.onnx"
}

class DownloadProgressBar(tqdm):
    def update_to(self, b=1, bsize=1, tsize=None):
        if tsize is not None:
            self.total = tsize
        self.update(b * bsize - self.n)

def download_file(url, output_path):
    with DownloadProgressBar(unit='B', unit_scale=True, miniters=1, desc=url.split('/')[-1]) as t:
        urllib.request.urlretrieve(url, filename=output_path, reporthook=t.update_to)

def main():
    os.makedirs("models", exist_ok=True)
    for filename, url in MODELS.items():
        dest = os.path.join("models", filename)
        if not os.path.exists(dest):
            print(f"Downloading {filename}...")
            download_file(url, dest)
        else:
            print(f"{filename} already exists.")

if __name__ == "__main__":
    main()
