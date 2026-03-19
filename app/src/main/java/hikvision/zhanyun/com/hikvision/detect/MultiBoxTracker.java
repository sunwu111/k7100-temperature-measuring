/* Copyright 2016 The TensorFlow Authors. All Rights Reserved.

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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import hikvision.zhanyun.com.hikvision.detect.Classifier.Recognition;
import hikvision.zhanyun.com.hikvision.utils.Log;

/**
 * A tracker wrapping ObjectTracker that also handles non-max suppression and matching existing
 * objects to new detections.
 */
public class MultiBoxTracker {
    private final Logger logger = new Logger();

    private static final float TEXT_SIZE_DIP = 18;

    // Maximum percentage of a box that can be overlapped by another box at detection time. Otherwise
    // the lower scored box (new or old) will be removed.
    private static final float MAX_OVERLAP = 0.2f;

    private static final float MIN_SIZE = 16.0f;

    // Allow replacement of the tracked box with new results if
    // correlation has dropped below this level.
    private static final float MARGINAL_CORRELATION = 0.75f;

    // Consider object to be lost if correlation falls below this threshold.
    private static final float MIN_CORRELATION = 0.3f;

    private static final int[] COLORS = {
            Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE,
            Color.parseColor("#55FF55"), Color.parseColor("#FFA500"), Color.parseColor("#FF8888"),
            Color.parseColor("#AAAAFF"), Color.parseColor("#FFFFAA"), Color.parseColor("#55AAAA"),
            Color.parseColor("#AA33AA"), Color.parseColor("#0D0068")
    };

    private final Queue<Integer> availableColors = new LinkedList<Integer>();

    public ObjectTracker objectTracker;

    public final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();

    private static class TrackedRecognition {
        ObjectTracker.TrackedObject trackedObject;
        RectF location;
        float detectionConfidence;
        int color;
        String title;
    }

    public final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();

    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();
    private Matrix frameToCanvasMatrix;

    private int frameWidth;
    private int frameHeight;

    private int sensorOrientation;
    private Context context;

    public MultiBoxTracker(final Context context) {
        this.context = context;
        for (final int color : COLORS) {
            availableColors.add(color);
        }

        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Style.STROKE);
        boxPaint.setStrokeWidth(3.0f);
    }

    private Matrix getFrameToCanvasMatrix() {
        return frameToCanvasMatrix;
    }

    public synchronized void trackResults(
            final List<Classifier.Recognition> results, final byte[] frame, final long timestamp) {
        logger.i("Processing %d results from %d", results.size(), timestamp);
        processResults(timestamp, results, frame);
    }

    /**
     * Returns a transformation matrix from one reference frame into another.
     * Handles cropping (if maintaining aspect ratio is desired) and rotation.
     *
     * @param srcWidth Width of source frame.
     * @param srcHeight Height of source frame.
     * @param dstWidth Width of destination frame.
     * @param dstHeight Height of destination frame.
     * @param applyRotation Amount of rotation to apply from one frame to another.
     *  Must be a multiple of 90.
     * @param maintainAspectRatio If true, will ensure that scaling in x and y remains constant,
     * cropping the image if necessary.
     * @return The transformation fulfilling the desired requirements.
     */
    public static Matrix getTransformationMatrix(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation,
            final boolean maintainAspectRatio) {
        final Matrix matrix = new Matrix();

        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                Log.w(Log.TAG,"Rotation of %d % 90 != 0" + applyRotation);
            }

            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;

        final int inWidth = transpose ? srcHeight : srcWidth;
        final int inHeight = transpose ? srcWidth : srcHeight;

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            final float scaleFactorX = dstWidth / (float) inWidth;
            final float scaleFactorY = dstHeight / (float) inHeight;

            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                final float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
                matrix.postScale(scaleFactor, scaleFactor);
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY);
            }
        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        }

        return matrix;
    }

    public synchronized void draw(final Canvas canvas) {
        final boolean rotated = sensorOrientation % 180 == 90;
        final float multiplier =
                Math.min(canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                        canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
        frameToCanvasMatrix =
                getTransformationMatrix(
                        frameWidth,
                        frameHeight,
                        (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                        (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                        sensorOrientation,
                        false);
        for (final TrackedRecognition recognition : trackedObjects) {
            final RectF trackedPos =
                    (objectTracker != null)
                            ? recognition.trackedObject.getTrackedPositionInPreviewFrame()
                            : new RectF(recognition.location);

            getFrameToCanvasMatrix().mapRect(trackedPos);
            boxPaint.setColor(recognition.color);

            canvas.drawRect(trackedPos, boxPaint);
            final String labelString =
                    !TextUtils.isEmpty(recognition.title)
                            ? String.format("%s %.2f", recognition.title, recognition.detectionConfidence)
                            : String.format("%.2f", recognition.detectionConfidence);
            int fontSize = Math.max(12, canvas.getHeight() / 20);
            textPaint.setColor(recognition.color);
            textPaint.setTextSize(fontSize);
            canvas.drawText(labelString, trackedPos.left + 20, trackedPos.top + fontSize, textPaint);
        }
    }

    private boolean initialized = false;

    public synchronized void onFrame(
            final int w,
            final int h,
            final int rowStride,
            final int sensorOrienation,
            final byte[] frame,
            final long timestamp) {
        if (objectTracker == null && !initialized) {
            ObjectTracker.clearInstance();

            logger.i("Initializing ObjectTracker: %dx%d", w, h);
            objectTracker = ObjectTracker.getInstance(w, h, rowStride, true);
            frameWidth = w;
            frameHeight = h;
            this.sensorOrientation = sensorOrienation;
            initialized = true;

            if (objectTracker == null) {
                String message =
                        "Object tracking support not found. "
                                + "See tensorflow/examples/android/README.md for details.";
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                logger.e(message);
            }
        }

        if (objectTracker == null) {
            return;
        }

        objectTracker.nextFrame(frame, null, timestamp, null, true);

        // Clean up any objects not worth tracking any more.
        final LinkedList<TrackedRecognition> copyList =
                new LinkedList<TrackedRecognition>(trackedObjects);
        for (final TrackedRecognition recognition : copyList) {
            final ObjectTracker.TrackedObject trackedObject = recognition.trackedObject;
            final float correlation = trackedObject.getCurrentCorrelation();
            if (correlation < MIN_CORRELATION) {
                logger.v("Removing tracked object %s because NCC is %.2f", trackedObject, correlation);
                trackedObject.stopTracking();
                trackedObjects.remove(recognition);

                availableColors.add(recognition.color);
            }
        }
    }

    private void processResults(
            final long timestamp, final List<Classifier.Recognition> results, final byte[] originalFrame) {
        final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

        screenRects.clear();
        final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

        for (final Recognition result : results) {
            if (result.getLocation() == null) {
                continue;
            }
            final RectF detectionFrameRect = new RectF(result.getLocation());

            final RectF detectionScreenRect = new RectF();
            rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

            logger.v(
                    "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

            screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

            if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
                logger.w("Degenerate rectangle! " + detectionFrameRect);
                continue;
            }

            rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));
        }

        if (rectsToTrack.isEmpty()) {
            logger.v("Nothing to track, aborting.");
            return;
        }

        if (objectTracker == null) {
            trackedObjects.clear();
            for (final Pair<Float, Recognition> potential : rectsToTrack) {
                final TrackedRecognition trackedRecognition = new TrackedRecognition();
                trackedRecognition.detectionConfidence = potential.first;
                trackedRecognition.location = new RectF(potential.second.getLocation());
                trackedRecognition.trackedObject = null;
                trackedRecognition.title = potential.second.getTitle();
                trackedRecognition.color = COLORS[trackedObjects.size()];
                trackedObjects.add(trackedRecognition);

                if (trackedObjects.size() >= COLORS.length) {
                    break;
                }
            }
            return;
        }

        logger.i("%d rects to track", rectsToTrack.size());
        for (final Pair<Float, Recognition> potential : rectsToTrack) {
            handleDetection(originalFrame, timestamp, potential);
        }
    }

    private void handleDetection(
            final byte[] frameCopy, final long timestamp, final Pair<Float, Recognition> potential) {
        final ObjectTracker.TrackedObject potentialObject =
                objectTracker.trackObject(potential.second.getLocation(), timestamp, frameCopy);

        final float potentialCorrelation = potentialObject.getCurrentCorrelation();
        logger.v(
                "Tracked object went from %s to %s with correlation %.2f",
                potential.second, potentialObject.getTrackedPositionInPreviewFrame(), potentialCorrelation);

        if (potentialCorrelation < MARGINAL_CORRELATION) {
            logger.v("Correlation too low to begin tracking %s.", potentialObject);
            potentialObject.stopTracking();
            return;
        }

        final List<TrackedRecognition> removeList = new LinkedList<TrackedRecognition>();

        float maxIntersect = 0.0f;

        // This is the current tracked object whose color we will take. If left null we'll take the
        // first one from the color queue.
        TrackedRecognition recogToReplace = null;

        // Look for intersections that will be overridden by this object or an intersection that would
        // prevent this one from being placed.
        for (final TrackedRecognition trackedRecognition : trackedObjects) {
            final RectF a = trackedRecognition.trackedObject.getTrackedPositionInPreviewFrame();
            final RectF b = potentialObject.getTrackedPositionInPreviewFrame();
            final RectF intersection = new RectF();
            final boolean intersects = intersection.setIntersect(a, b);

            final float intersectArea = intersection.width() * intersection.height();
            final float totalArea = a.width() * a.height() + b.width() * b.height() - intersectArea;
            final float intersectOverUnion = intersectArea / totalArea;

            // If there is an intersection with this currently tracked box above the maximum overlap
            // percentage allowed, either the new recognition needs to be dismissed or the old
            // recognition needs to be removed and possibly replaced with the new one.
            if (intersects && intersectOverUnion > MAX_OVERLAP) {
                if (potential.first < trackedRecognition.detectionConfidence
                        && trackedRecognition.trackedObject.getCurrentCorrelation() > MARGINAL_CORRELATION) {
                    // If track for the existing object is still going strong and the detection score was
                    // good, reject this new object.
                    potentialObject.stopTracking();
                    return;
                } else {
                    removeList.add(trackedRecognition);

                    // Let the previously tracked object with max intersection amount donate its color to
                    // the new object.
                    if (intersectOverUnion > maxIntersect) {
                        maxIntersect = intersectOverUnion;
                        recogToReplace = trackedRecognition;
                    }
                }
            }
        }

        // If we're already tracking the max object and no intersections were found to bump off,
        // pick the worst current tracked object to remove, if it's also worse than this candidate
        // object.
        if (availableColors.isEmpty() && removeList.isEmpty()) {
            for (final TrackedRecognition candidate : trackedObjects) {
                if (candidate.detectionConfidence < potential.first) {
                    if (recogToReplace == null
                            || candidate.detectionConfidence < recogToReplace.detectionConfidence) {
                        // Save it so that we use this color for the new object.
                        recogToReplace = candidate;
                    }
                }
            }
            if (recogToReplace != null) {
                logger.v("Found non-intersecting object to remove.");
                removeList.add(recogToReplace);
            } else {
                logger.v("No non-intersecting object found to remove");
            }
        }

        // Remove everything that got intersected.
        for (final TrackedRecognition trackedRecognition : removeList) {
            logger.v(
                    "Removing tracked object %s with detection confidence %.2f, correlation %.2f",
                    trackedRecognition.trackedObject,
                    trackedRecognition.detectionConfidence,
                    trackedRecognition.trackedObject.getCurrentCorrelation());
            trackedRecognition.trackedObject.stopTracking();
            trackedObjects.remove(trackedRecognition);
            if (trackedRecognition != recogToReplace) {
                availableColors.add(trackedRecognition.color);
            }
        }

        if (recogToReplace == null && availableColors.isEmpty()) {
            logger.e("No room to track this object, aborting.");
            potentialObject.stopTracking();
            return;
        }

        // Finally safe to say we can track this object.
        logger.v(
                "Tracking object %s (%s) with detection confidence %.2f at position %s",
                potentialObject,
                potential.second.getTitle(),
                potential.first,
                potential.second.getLocation());
        final TrackedRecognition trackedRecognition = new TrackedRecognition();
        trackedRecognition.detectionConfidence = potential.first;
        trackedRecognition.trackedObject = potentialObject;
        trackedRecognition.title = potential.second.getTitle();

        // Use the color from a replaced object before taking one from the color queue.
        trackedRecognition.color =
                recogToReplace != null ? recogToReplace.color : availableColors.poll();
        trackedObjects.add(trackedRecognition);
    }
}
