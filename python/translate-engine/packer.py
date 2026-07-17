import os
import zipfile

def pack_cbz(source_dir, output_cbz_path):
    # Ensure target directory exists
    parent = os.path.dirname(output_cbz_path)
    if parent:
        os.makedirs(parent, exist_ok=True)

    # List and sort all files to preserve correct reading order
    files = sorted(os.listdir(source_dir))
    
    with zipfile.ZipFile(output_cbz_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
        for file in files:
            file_path = os.path.join(source_dir, file)
            if os.path.isfile(file_path):
                zipf.write(file_path, file)

    print(f"Packaged {len(files)} files into {output_cbz_path}")
