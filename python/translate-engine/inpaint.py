import cv2
import numpy as np
import onnxruntime as ort

class Inpainter:
    def __init__(self, model_path="models/lama-manga.onnx"):
        self.model_path = model_path
        self.session = None

    def _init_session(self):
        if self.session is None:
            opts = ort.SessionOptions()
            opts.intra_op_num_threads = 2
            opts.inter_op_num_threads = 2
            try:
                self.session = ort.InferenceSession(self.model_path, sess_options=opts, providers=["CPUExecutionProvider"])
            except Exception as e:
                print(f"\n[ONNX-Session-Error] LaMa model {self.model_path} failed to load: {e}. Re-downloading...")
                import os
                if os.path.exists(self.model_path):
                    try:
                        os.remove(self.model_path)
                    except Exception:
                        pass
                from download_models import main as download_models_main
                download_models_main()
                self.session = ort.InferenceSession(self.model_path, sess_options=opts, providers=["CPUExecutionProvider"])

    def inpaint_solid(self, img, mask):
        # OpenCV Telea inpainting for solid backgrounds
        return cv2.inpaint(img, mask, inpaintRadius=3, flags=cv2.INPAINT_TELEA)

    def inpaint_lama(self, img, mask):
        self._init_session()
        h, w = img.shape[:2]

        # Convert mask to 2D binary if it has channels
        if len(mask.shape) == 3:
            mask = mask[:, :, 0]

        # Pad image and mask to modulo 8
        padded_img, (pad_h, pad_w) = self._pad_to_modulo(img, 8)
        padded_mask, _ = self._pad_to_modulo(mask, 8)

        # Normalize image to float32 [0.0, 1.0] and transpose to NCHW
        img_n = padded_img.astype(np.float32) / 255.0
        img_n = np.transpose(img_n, (2, 0, 1)) # (3, H, W)
        img_tensor = img_n[np.newaxis, ...] # (1, 3, H, W)

        # Normalize mask to float32 [0.0, 1.0] (binary: 0 or 1) and transpose to NCHW
        mask_n = (padded_mask > 0).astype(np.float32)
        mask_n = mask_n[np.newaxis, np.newaxis, ...] # (1, 1, H, W)

        # Run inference
        ort_inputs = {
            self.session.get_inputs()[0].name: img_tensor,
            self.session.get_inputs()[1].name: mask_n
        }
        outputs = self.session.run(None, ort_inputs)
        out_tensor = outputs[0][0] # (3, H, W)

        # Convert back to uint8 HWC (BGR)
        out_img = np.transpose(out_tensor, (1, 2, 0))
        out_img = np.clip(out_img * 255.0, 0.0, 255.0).astype(np.uint8)

        # Unpad
        return out_img[:h, :w]

    def _pad_to_modulo(self, img, mod=8):
        h, w = img.shape[:2]
        pad_h = (mod - h % mod) % mod
        pad_w = (mod - w % mod) % mod
        if len(img.shape) == 3:
            padded = np.pad(img, ((0, pad_h), (0, pad_w), (0, 0)), mode="edge")
        else:
            padded = np.pad(img, ((0, pad_h), (0, pad_w)), mode="constant", constant_values=0)
        return padded, (pad_h, pad_w)

    def inpaint(self, image_path, boxes):
        img = cv2.imread(image_path)
        if img is None:
            raise ValueError(f"Could not read image: {image_path}")

        h, w = img.shape[:2]
        # Create global mask for all boxes
        global_mask = np.zeros((h, w), dtype=np.uint8)

        for box in boxes:
            x1, y1, x2, y2 = box
            # Extract box ROI
            roi = img[y1:y2, x1:x2]
            if roi.size == 0:
                continue

            # Detect text by thresholding (text is typically dark)
            gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
            # Threshold to get dark pixels (text) on light background (bubble)
            _, thresh = cv2.threshold(gray, 100, 255, cv2.THRESH_BINARY_INV)

            # Dilate mask slightly to cover edges of characters
            kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
            dilated = cv2.dilate(thresh, kernel, iterations=1)

            # Place back into global mask
            global_mask[y1:y2, x1:x2] = dilated

        # Check if there are complex backgrounds in the boxes
        has_complex = False
        for box in boxes:
            x1, y1, x2, y2 = box
            roi = img[y1:y2, x1:x2]
            roi_mask = global_mask[y1:y2, x1:x2]
            if roi.size == 0:
                continue
            bg_pixels = roi[roi_mask == 0]
            if len(bg_pixels) > 0:
                bg_gray = cv2.cvtColor(bg_pixels.reshape(-1, 1, 3), cv2.COLOR_BGR2GRAY)
                if np.std(bg_gray) >= 15.0:
                    has_complex = True
                    break

        if has_complex:
            try:
                # Use LaMa for the whole image (using global mask) to ensure seamless blending
                inpainted_img = self.inpaint_lama(img, global_mask)
            except Exception as e:
                print(f"\n[inpaint-error] LaMa inpainting failed: {e}. Falling back to OpenCV Telea.")
                inpainted_img = self.inpaint_solid(img, global_mask)
        else:
            # Use OpenCV Telea (solid background) for the whole image
            inpainted_img = self.inpaint_solid(img, global_mask)

        return inpainted_img
