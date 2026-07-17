import cv2
import numpy as np
import onnxruntime as ort

class TextDetector:
    def __init__(self, model_path="models/yolo-text-detector.onnx"):
        # Configure CPU execution provider with optimized thread counts
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = 2
        opts.inter_op_num_threads = 2
        self.session = ort.InferenceSession(model_path, sess_options=opts, providers=["CPUExecutionProvider"])
        self.input_name = self.session.get_inputs()[0].name
        self.input_shape = self.session.get_inputs()[0].shape # e.g. [1, 3, 640, 640]
        self.input_w = self.input_shape[3]
        self.input_h = self.input_shape[2]

    def detect(self, image_path, conf_thresh=0.3, nms_thresh=0.45):
        # Read image
        img = cv2.imread(image_path)
        if img is None:
            raise ValueError(f"Could not read image: {image_path}")
        h, w = img.shape[:2]

        # Preprocess: resize to YOLO input dimensions and normalize [0, 1]
        blob = cv2.dnn.blobFromImage(img, 1.0 / 255.0, (self.input_w, self.input_h), swapRB=True, crop=False)

        # Run inference
        outputs = self.session.run(None, {self.input_name: blob})
        output = outputs[0][0] # Shape is either (25200, 6) [YOLOv5] or (5, 8400) [YOLOv8]

        # Determine YOLO architecture format
        if output.shape[0] < output.shape[1]:
            # YOLOv8 format: shape is (coords + classes, num_anchors) -> Transpose to (anchors, fields)
            output = output.T
            is_yolov8 = True
        else:
            is_yolov8 = False

        boxes = []
        confidences = []

        x_scale = w / self.input_w
        y_scale = h / self.input_h

        for row in output:
            if is_yolov8:
                score = row[4]
            else:
                confidence = row[4]
                class_score = row[5] if len(row) > 5 else 1.0
                score = confidence * class_score

            if score > conf_thresh:
                xc, yc, nw, nh = row[0], row[1], row[2], row[3]
                x1 = int((xc - nw / 2) * x_scale)
                y1 = int((yc - nh / 2) * y_scale)
                box_w = int(nw * x_scale)
                box_h = int(nh * y_scale)
                
                # Clip to image boundaries
                x1 = max(0, x1)
                y1 = max(0, y1)
                box_w = min(w - x1, box_w)
                box_h = min(h - y1, box_h)

                if box_w > 0 and box_h > 0:
                    boxes.append([x1, y1, box_w, box_h])
                    confidences.append(float(score))

        # Non-maximum suppression
        indices = cv2.dnn.NMSBoxes(boxes, confidences, conf_thresh, nms_thresh)
        
        final_boxes = []
        if len(indices) > 0:
            # Handle different opencv versions returning flat list or nested array
            flat_indices = np.array(indices).flatten()
            for idx in flat_indices:
                x, y, bw, bh = boxes[idx]
                final_boxes.append([x, y, x + bw, y + bh]) # [x1, y1, x2, y2]

        # Sort boxes: Top-to-bottom, Left-to-right (standard manga flow)
        final_boxes.sort(key=lambda b: (b[1], b[0]))
        return final_boxes
