package hikvision.zhanyun.com.hikvision.device.iray;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import androidx.annotation.NonNull;
import android.view.SurfaceView;
import java.util.Vector;
import hikvision.zhanyun.com.hikvision.utils.Log;

public class SVDraw extends SurfaceView{
    private final Context context;
    private final SurfaceView view;
    private Paint paint = new Paint();
    private Bitmap bitmap;
    private Vector<Point> points = new Vector<>();

    public SVDraw(@NonNull Context context, @NonNull SurfaceView view) {
        super(context);
        this.context = context;
        this.paint.setAntiAlias(true);
        setZOrderOnTop(true);
        this.view = view;
        this.view.getHolder().setFormat(PixelFormat.TRANSPARENT);
    }

    // 此函数不能正确清零OSD层
    private void clearDraw() {
        drawBitmap().drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        bitmapRender();
    }

    private void drawLine(int x1, int y1, int x2, int y2, int color, int width) {
        Log.i(Log.TAG, String.format("画线(%d, %d) -- (%d, %d)", x1, y1, x2, y2));
        paint.setColor(color);
        paint.setStrokeWidth(width);
        drawBitmap().drawLine(x1, y1, x2, y2, paint);
        bitmapRender();
    }

    private void drawPoint(int x, int y, int color, int width) {
        Log.i(Log.TAG, String.format("画点(%d, %d)", x, y));
        paint.setColor(color);
        paint.setStrokeWidth(width);
        drawBitmap().drawPoint(x, y, paint);
        bitmapRender();
    }

    public Vector<Point> getPoints() {
        return points;
    }

    public void setPoint(int x, int y) {
        points.add(new Point(x, y));
        if (points.size() == 1) {
            drawPoint(x, y, Color.RED, 3);
        } else {
            Point p = points.get(points.size() - 2);
            drawLine(p.x, p.y, x, y, Color.RED, 2);
        }
    }

    public void finishPoint() {
        Point x1 = points.lastElement();
        Point x2 = points.firstElement();
        if (!x1.equals(x2)) {
            drawLine(x1.x, x1.y, x2.x, x2.y, Color.RED, 2);
        }
    }

    public void clearPoints() {
        //clearDraw();
        points.clear();
    }

    public int width() {
        return view.getWidth();
    }

    public int height() {
        return view.getHeight();
    }

    private Canvas drawBitmap() {
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        }
        return new Canvas(bitmap);
    }

    private void bitmapRender() {
        Canvas canvas = view.getHolder().lockCanvas();
        if (canvas != null) {
            view.getHolder().unlockCanvasAndPost(canvas);
        }
    }
}
