/* Copyright 2015 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package hikvision.zhanyun.com.hikvision.detect;

import android.graphics.Bitmap;
import android.graphics.RectF;

import java.util.List;

import hikvision.zhanyun.com.hikvision.Settings;

/**
 * Generic interface for interacting with different recognition engines.
 * AI 识别抽象类，英伟达，华为等各种识别算法，应该实现Classifier接口，即可做到通用
 * 内置 AI 算法实现了TensorFlowYolo检测算法
 */

public interface Classifier {
    /**
     * An immutable result returned by a Classifier describing what was recognized.
     */
    public class Recognition {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final String id;

        private final int classID;
        /**
         * Display name for the recognition.
         */
        private final String title;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         */
        private Float confidence;

        /**
         * Optional location within the source image for the location of the recognized object.
         */
        private RectF location;

        public Recognition(final int classID, final String id, final String title, final Float confidence, final RectF location) {
            this.id = id;
            this.classID = classID;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public int getClassID() {
            return classID;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }
        public void setConfidence(float confidence) {
            this.confidence = confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        public void setLocation(RectF location) {
            this.location = location;
        }

        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }

    //////
    // 先不增加智能测距功能
//    public class RecognitionObb {
//        /**
//         * A unique identifier for what has been recognized. Specific to the class, not the instance of
//         * the object.
//         */
//        private final String id;
//
//        private final int classID;
//        /**
//         * Display name for the recognition.
//         */
//        private final String title;
//
//        /**
//         * A sortable score for how good the recognition is relative to others. Higher should be better.
//         */
//        private Float confidence;
//
//        /**
//         * Optional location within the source image for the location of the recognized object.
//         */
//        private Float centerX;
//        private Float centerY;
//        private Float width;
//        private Float height;
//
//        /**
//         * Optional location within the source image for the rotation polygon of the recognized object.
//         */
//        private Vector<Point> polygon;
//
//        public RecognitionObb(final int classID, final String id, final String title, final Float confidence, final Float centerX, final Float centerY, final Float width, final Float height, final Vector<Point> polygon) {
//            this.id = id;
//            this.classID = classID;
//            this.title = title;
//            this.confidence = confidence;
//            this.centerX = centerX;
//            this.centerY = centerY;
//            this.width = width;
//            this.height = height;
//            this.polygon = polygon;
//        }
//
//        public int getClassID() {
//            return classID;
//        }
//
//        public String getId() {
//            return id;
//        }
//
//        public String getTitle() {
//            return title;
//        }
//
//        public Float getConfidence() {
//            return confidence;
//        }
//        public void setConfidence(float confidence) {
//            this.confidence = confidence;
//        }
//
//        public Float getCenterX() {
//            return centerX;
//        }
//
//        public void setCenterX(Float centerX) {
//            this.centerX = centerX;
//        }
//
//        public Float getCenterY() {
//            return centerY;
//        }
//
//        public void setCenterY(Float centerY) {
//            this.centerY = centerY;
//        }
//
//        public Float getWidth() {
//            return width;
//        }
//
//        public void setWidth(Float width) {
//            this.width = width;
//        }
//
//        public Float getHeight() {
//            return height;
//        }
//
//        public void setHeight(Float height) {
//            this.height = height;
//        }
//
//        public Vector<Point> getPolygon() {
//            return new Vector<Point>(polygon);
//        }
//
//        public void setPolygon(Vector<Point> polygon) {
//            this.polygon = polygon;
//        }
//
//        @Override
//        public String toString() {
//            String resultString = "";
//            if (id != null) {
//                resultString += "[" + id + "] ";
//            }
//
//            if (title != null) {
//                resultString += title + " ";
//            }
//
//            if (confidence != null) {
//                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
//            }
//
//            if (centerX != null) {
//                resultString += centerX + " ";
//            }
//
//            if (centerY != null) {
//                resultString += centerY + " ";
//            }
//
//            if (width != null) {
//                resultString += width + " ";
//            }
//
//            if (height != null) {
//                resultString += height + " ";
//            }
//
//            return resultString.trim();
//        }
//    }
    //////

    void close();

    public List<Classifier.Recognition> recognizeImage(String file);

    List<Recognition> recognizeImage(Bitmap bitmap, Settings.AIAlertType[] alertTypes);

    //////
    // 先不增加智能测距功能
//    public List<Classifier.RecognitionObb> recognizeImageObb(String file);
//
//    List<RecognitionObb> recognizeImageObb(Bitmap bitmap);
    //////

    public void setConfidence(float value);
}
