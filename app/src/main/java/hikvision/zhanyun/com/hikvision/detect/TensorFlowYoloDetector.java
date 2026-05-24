/* Copyright 2017 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*//*


package hikvision.zhanyun.com.hikvision.detect;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;

//import org.tensorflow.TensorFlow;
//import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Scanner;

import hikvision.zhanyun.com.hikvision.MainActivity;
import hikvision.zhanyun.com.hikvision.utils.Log;

*/
/**
 * An object detector that uses TF and a YOLO model to recognizeImage objects.
 *//*

public class TensorFlowYoloDetector implements Classifier {
    private final String MODEL_FILE = MainActivity.DATA_DIR + "models.pb";
    private final String LABEL_FILE = MainActivity.DATA_DIR + "labels.txt";
    private final String ANCHOR_FILE = MainActivity.DATA_DIR + "anchors.txt";
    //    private static final int YOLO_INPUT_SIZE = 640;
    private static final int YOLO_INPUT_SIZE = 416;
    private static final String YOLO_INPUT_NAME = "input";
    private static final String YOLO_OUTPUT_NAMES = "output";
    //        private static final String YOLO_INPUT_NAME = "input/input_data";
    //        private static final String YOLO_OUTPUT_NAMES = "pred_mbbox/concat_2,";
    private static final int YOLO_BLOCK_SIZE = 32;
    //    private static final int YOLO_BLOCK_SIZE = 16;
    private static final int MAX_RESULTS = 5;
    private static final int NUM_BOXES_PER_BLOCK = 5;
    private static final float MAX_OVERLAP = 0.5f;

    private static int NUM_CLASSES = 3;
    private static Double[] ANCHORS = null;
    public static String[] LABELS = null;

    // Config values.
    private float confidence = 0.8f;            // 置信度门槛
    private String inputName = YOLO_INPUT_NAME;
    private int inputSize = YOLO_INPUT_SIZE;

    // Pre-allocated buffers.
    private int[] intValues;
    private float[] floatValues;
    private String[] outputNames;
    private int blockSize;
    private static TensorFlowInferenceInterface inferenceInterface = null;

    */
/**
     * Initializes a native TensorFlow session for classifying images.
     *//*

    public TensorFlowYoloDetector(AssetManager assetManager) {
        if (ANCHORS == null) loadAnchors(ANCHOR_FILE);
        if (LABELS == null) loadLabels(LABEL_FILE);

        outputNames = YOLO_OUTPUT_NAMES.split(",");
        intValues = new int[inputSize * inputSize];
        floatValues = new float[inputSize * inputSize * 3];
        blockSize = YOLO_BLOCK_SIZE;

        if (new File(MODEL_FILE).exists()) {
            if (inferenceInterface == null) {
                inferenceInterface = new TensorFlowInferenceInterface(assetManager, MODEL_FILE);
                Log.i(Log.TAG, "TensorFlow版本: " + TensorFlow.version());
            }
        } else {
            Log.w(Log.TAG, "物体检测模型不存在，无法检测外力破坏");
        }
    }

    private static void loadLabels(String labelFile) {
        if (labelFile == null || !new File(labelFile).exists()) return;
        try {
            List<String> list = new ArrayList<String>();
            FileInputStream fis = new FileInputStream(labelFile);
            Scanner scanner = new Scanner(fis);
            while (scanner.hasNextLine())
                list.add(scanner.nextLine());
            String[] tempArray = new String[list.size()];
            LABELS = list.toArray(tempArray);
            NUM_CLASSES = list.size();
            fis.close();
        } catch (Exception e) {
            Log.e(Log.TAG, "加载标签失败: " + e.getMessage());
        }
    }

    private static void loadAnchors(String file) {
        if (file == null || !new File(file).exists()) return;

        try {
            List<Double> list = new ArrayList<Double>();
            FileInputStream fis = new FileInputStream(file);
            Scanner scanner = new Scanner(fis);
            while (scanner.hasNextDouble())
                list.add(scanner.nextDouble());
            Double[] tempArray = new Double[list.size()];
            ANCHORS = list.toArray(tempArray);
            fis.close();
        } catch (Exception e) {
            Log.e(Log.TAG, "加载锚点失败: " + e.getMessage());
        }
    }

    private float expit(final float x) {
        return (float) (1. / (1. + Math.exp(-x)));
    }

    private void softmax(final float[] vals) {
        float max = Float.NEGATIVE_INFINITY;
        for (final float val : vals) {
            max = Math.max(max, val);
        }
        float sum = 0.0f;
        for (int i = 0; i < vals.length; ++i) {
            vals[i] = (float) Math.exp(vals[i] - max);
            sum += vals[i];
        }
        for (int i = 0; i < vals.length; ++i) {
            vals[i] = vals[i] / sum;
        }
    }

    @Override
    public List<Classifier.Recognition> recognizeImage(String file) {
        Bitmap bmp = BitmapFactory.decodeFile(file);
        List<Classifier.Recognition> ret = recognizeImage(bmp);
        bmp.recycle();
        return ret;
    }

    @Override
    public List<Recognition> recognizeImage(Bitmap raw) {
        if (inferenceInterface == null) return null;

        Bitmap bitmap = Bitmap.createScaledBitmap(raw, inputSize, inputSize, false);

        long start = System.currentTimeMillis();
        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int i = 0; i < intValues.length; ++i) {
            floatValues[i * 3 + 0] = ((intValues[i] >> 16) & 0xFF) / 255.0f;
            floatValues[i * 3 + 1] = ((intValues[i] >> 8) & 0xFF) / 255.0f;
            floatValues[i * 3 + 2] = (intValues[i] & 0xFF) / 255.0f;
        }

        // Copy the input data into TensorFlow.
        inferenceInterface.feed(inputName, floatValues, 1, inputSize, inputSize, 3);

        // Run the inference call.
        inferenceInterface.run(outputNames, false);

        // Copy the output Tensor back into the output array.
        final int gridWidth = bitmap.getWidth() / blockSize;
        final int gridHeight = bitmap.getHeight() / blockSize;
//        final float[] output = new float[gridWidth * gridHeight * (NUM_CLASSES + 5) * NUM_BOXES_PER_BLOCK];
        final float[] output = new float[200000];
        inferenceInterface.fetch(outputNames[0], output);

        // Find the best detections.
        final PriorityQueue<Recognition> pq =
                new PriorityQueue<Recognition>(1,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(final Recognition lhs, final Recognition rhs) {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });

        for (int y = 0; y < gridHeight; ++y) {
            for (int x = 0; x < gridWidth; ++x) {
                for (int b = 0; b < NUM_BOXES_PER_BLOCK; ++b) {
                    final int offset = (gridWidth * (NUM_BOXES_PER_BLOCK * (NUM_CLASSES + 5))) * y
                            + (NUM_BOXES_PER_BLOCK * (NUM_CLASSES + 5)) * x
                            + (NUM_CLASSES + 5) * b;

                    final float xPos = (x + expit(output[offset + 0])) * blockSize;
                    final float yPos = (y + expit(output[offset + 1])) * blockSize;

                    final float w = (float) (Math.exp(output[offset + 2]) * ANCHORS[2 * b + 0]) * blockSize;
                    final float h = (float) (Math.exp(output[offset + 3]) * ANCHORS[2 * b + 1]) * blockSize;

                    final RectF rect = new RectF(Math.max(0, xPos - w / 2),
                            Math.max(0, yPos - h / 2), Math.min(bitmap.getWidth() - 1, xPos + w / 2),
                            Math.min(bitmap.getHeight() - 1, yPos + h / 2));
                    final float confidence = expit(output[offset + 4]);

                    int detectedClass = -1;
                    float maxClass = 0;

                    final float[] classes = new float[NUM_CLASSES];
                    for (int c = 0; c < NUM_CLASSES; ++c) {
                        classes[c] = output[offset + 5 + c];
                    }
                    softmax(classes);

                    for (int c = 0; c < NUM_CLASSES; ++c) {
                        if (classes[c] > maxClass) {
                            detectedClass = c;
                            maxClass = classes[c];
                        }
                    }

                    final float confidenceInClass = maxClass * confidence;
                    if (confidenceInClass > 0.2f) {
                        pq.add(new Recognition(detectedClass, "" + offset, LABELS[detectedClass], confidenceInClass, rect));
                        Log.i(Log.TAG, String.format("检测到对象: %s (%f) %s", LABELS[detectedClass], confidenceInClass, rect));
                    }
                }
            }
        }

        final ArrayList<Recognition> result = new ArrayList<Recognition>();
        for (int i = 0; i < Math.min(pq.size(), MAX_RESULTS); ++i) {
            Recognition a = pq.poll();
            // 要去掉重复检测的对象，如果一个RECT在另外一个RECT内部，就不需要再次添加了
            boolean duplicate = false;
            for (Recognition b : result) {
                if (a.getClassID() != b.getClassID()) continue;

                final RectF intersection = new RectF();
                final boolean intersects = intersection.setIntersect(a.getLocation(), b.getLocation());
                final float intersectArea = intersection.width() * intersection.height();
                final float totalArea = a.getLocation().width() * a.getLocation().height() + b.getLocation().width() * b.getLocation().height() - intersectArea;
                final float intersectOverUnion = intersectArea / totalArea;

                if (intersects && intersectOverUnion > MAX_OVERLAP) {
                    if (a.getConfidence() > b.getConfidence()) {
                        b.setConfidence(a.getConfidence());
                        b.setLocation(a.getLocation());
                    }
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate && a.getConfidence() >= confidence) {
                result.add(a);
            }
        }

        // 坐标转换
        for (Recognition r: result) {
            final RectF location = r.getLocation();
            location.left = location.left / inputSize * raw.getWidth();
            location.top = location.top / inputSize * raw.getHeight();
            location.right = location.right / inputSize * raw.getWidth();
            location.bottom = location.bottom / inputSize * raw.getHeight();
            r.setLocation(location);
        }
        Log.d(Log.TAG, "AI检测耗时(ms): " + (System.currentTimeMillis() - start));
        return result;
    }

    @Override
    public void close() {
        inferenceInterface.close();
    }

    @Override
    public void setConfidence(float value) {
        this.confidence = value;
    }
}
*/
