package de.jamesbeans.quadrasolve;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.widget.TextView;

import java.text.DecimalFormat;

import static java.lang.Math.sqrt;

/**
 * Created by Simon on 14.08.2017.
 * The Surfaceview which draws the graph and supports tracing, panning & zooming
 */

public class GraphSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder holder;
    public TextView rootTextView1, rootTextView2, apexTextView, curpoint;
    private final Paint whiteline = new Paint(), whitePoints = new Paint(), graphPoints = new Paint(), gridLines = new Paint(), black = new Paint();
    private int canvasWidth, canvasHeight;
    boolean drawPoint;
    private float touchX;
    private double xmin, xmax, ymin, ymax;
    private double a, b, c, x1, x2, roots, scheitelx, scheitely;
    boolean inited;
    private double gridIntervX, gridIntervY;
    String activity;
    private double lastx, lasty;
    private Bitmap bm, bmlastdraw;
    private Canvas canvas;
    private boolean isFirstDrawPoint;
    final int arrowSize = 35;
    final int touchTolerance = 50;
    final int highlightCircleRadius = 15;

    public GraphSurfaceView(Context context) {
        super(context);
        init();
    }

    public GraphSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GraphSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public GraphSurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        holder = getHolder();
        holder.addCallback(this);
        activity = "Tracing";
        //configure the different paints
        whiteline.setColor(Color.WHITE);
        whiteline.setStrokeWidth(6.0F); //should be 4
        whitePoints.setColor(Color.WHITE);
        whitePoints.setStrokeWidth(20.0F);
        graphPoints.setColor(Color.argb(255, 48, 63, 159));
        graphPoints.setStrokeWidth(6.0F);
        graphPoints.setStyle(Paint.Style.STROKE);  //should be 4
        gridLines.setColor(Color.DKGRAY);
        gridLines.setStrokeWidth(2.0F);
        black.setColor(Color.BLACK);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) { draw(); }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) { }

    void draw() {
        if(!inited) {
            inited = true;
            Canvas tmp = holder.lockCanvas();
            canvasWidth = tmp.getWidth();
            canvasHeight = tmp.getHeight();
            holder.unlockCanvasAndPost(tmp);
            bm = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bm);
            //get the different values from the graph class for easier access
            x1 = GraphActivity.x1;
            x2 = GraphActivity.x2;
            a = GraphActivity.a;
            b = GraphActivity.b;
            c = GraphActivity.c;
            roots = (double) GraphActivity.roots;
            scheitelx = GraphActivity.scheitelx;
            scheitely = GraphActivity.scheitely;
            //Calculate xmin, xmax, ymin and ymax
            if (2.0 == roots) {
                xmin = 1.5 * x1 - 0.5 * x2;
                xmax = 1.5 * x2 - 0.5 * x1;
                if((double) 0 < a) {
                    ymax = a * xmin * xmin + b * xmin + c;
                    ymin = -ymax / 2.0;
                } else {
                    ymin = a * xmin * xmin + b * xmin + c;
                    ymax = -ymin / 2.0;
                }
            } else {
                if ((double) 0 < a) {
                    ymin = scheitely - a;
                    ymax = scheitely + 3.0 * a;
                    xmin = -b / (2.0 * a) - Math.sqrt(Math.pow(b / (2.0 * a), 2.0) - (c - ymax) / a);
                    xmax = -b / (2.0 * a) + Math.sqrt(Math.pow(b / (2.0 * a), 2.0) - (c - ymax) / a);
                } else {
                    ymax = scheitely - a;
                    ymin = scheitely + 3.0 * a;
                    xmin = -b / (2.0 * a) - Math.sqrt(Math.pow(b / (2.0 * a), 2.0) - (c - ymin) / a);
                    xmax = -b / (2.0 * a) + Math.sqrt(Math.pow(b / (2.0 * a), 2.0) - (c - ymin) / a);
                }
            }
            calculateGridlinePositions();
        }
        if(drawPoint) {
            if(isFirstDrawPoint) {
                bmlastdraw = bm.copy(Bitmap.Config.ARGB_8888, false);
                isFirstDrawPoint = false;
            }
            //get the current state of the canvas
            canvas.drawBitmap(bmlastdraw, (float) 0, (float) 0, whitePoints);
            //Get the touch points' coordinates in the graph's coordinate system
            final double curx = lirp((double) touchX, (double) 0, (double) canvasWidth, xmin, xmax);
            final double cury = GraphActivity.a * Math.pow(curx, 2.0) + GraphActivity.b * curx + GraphActivity.c;
            if(cury > ymin && cury < ymax) {
                canvas.drawCircle(touchX, (float) lirp(cury, ymin, ymax, (double) canvasHeight, (double) 0), 10.0F, whitePoints);
            }
            DecimalFormat df = new DecimalFormat("#.####");
            if(INVISIBLE == curpoint.getVisibility()) {
                curpoint.setVisibility(VISIBLE);
            }
            curpoint.setText(getResources().getString(R.string.curpoint) + df.format(curx) + getResources().getString(R.string.semicolon) + df.format(cury));
            Canvas screenCanvas = holder.lockCanvas();
            screenCanvas.drawBitmap(bm, (float) 0, (float) 0, whiteline);
            holder.unlockCanvasAndPost(screenCanvas);
        } else {
            //Pre-drawing done in worker thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    canvas.drawRect((float) 0, (float) 0, (float) canvasWidth, (float) canvasHeight, black);
                    isFirstDrawPoint = true;
                    if(activity.equals("Zooming")) {
                        calculateGridlinePositions();
                    }
                    drawGridLines(canvas);
                    //draw the actual function
                    Path p = new Path();
                    float fofx1 = (float) (a * Math.pow(xmin, 2.0) + b * xmin + c);
                    p.moveTo((float) 0, (float) lirp((double) fofx1, ymin, ymax, (double) canvasHeight, (double) 0));
                    p.quadTo((float) (canvasWidth / 2), (float) lirp(((double) fofx1 + (2.0 * xmin * a + b) * (xmax - xmin) / 2.0), ymin, ymax, (double) canvasHeight, (double) 0), (float) canvasWidth, (float) lirp((a * Math.pow(xmax, 2.0) + b * xmax + c), ymin, ymax, (double) canvasHeight, (double) 0));
                    canvas.drawPath(p, graphPoints);
                    //calculate the positions of the axis
                    long xaxis = (long) (int) lirp((double) 0, ymin, ymax, (double) canvasHeight, (double) 0);
                    long yaxis = (long) (int) lirp((double) 0, xmin, xmax, (double) 0, (double) canvasWidth);
                    if ((double) 0 > xmin && (double) 0 < xmax) {
                        canvas.drawLine((float) yaxis, (float) 0, (float) yaxis, (float) canvasHeight, whiteline);
                        canvas.drawLine((float) (yaxis - (long) arrowSize), (float) arrowSize, (float) yaxis, (float) 0, whiteline);
                        canvas.drawLine((float) (yaxis + (long) arrowSize), (float) arrowSize, (float) yaxis, (float) 0, whiteline);
                    }
                    if ((double) 0 > ymin && (double) 0 < ymax) {
                        canvas.drawLine((float) 0, (float) xaxis, (float) canvasWidth, (float) xaxis, whiteline);
                        canvas.drawLine((float) (canvasWidth - arrowSize), (float) (xaxis - (long) arrowSize), (float) canvasWidth, (float) xaxis, whiteline);
                        canvas.drawLine((float) (canvasWidth - arrowSize), (float) (xaxis + (long) arrowSize), (float) canvasWidth, (float) xaxis, whiteline);
                    }
                    //Scheitelpunkt hervorheben
                    canvas.drawCircle((float) Math.round(lirp(scheitelx, xmin, xmax, (double) 0, (double) canvasWidth)), (float) Math.round(lirp(scheitely, ymin, ymax, (double) canvasHeight, (double) 0)), (float) highlightCircleRadius, whitePoints);
                    //Nullstellen hervorheben
                    if ((double) 0 < roots) {
                        canvas.drawCircle((float) Math.round(lirp(x1, xmin, xmax, (double) 0, (double) canvasWidth)), (float) Math.round(lirp((double) 0, ymin, ymax, (double) canvasHeight, (double) 0)), (float) highlightCircleRadius,  whitePoints);
                    }
                    if (2.0 == roots) {
                        canvas.drawCircle((float) Math.round(lirp(x2, xmin, xmax, (double) 0, (double) canvasWidth)), (float) Math.round(lirp((double) 0, ymin, ymax, (double) canvasHeight, (double) 0)), (float) highlightCircleRadius, whitePoints);
                    }
                    post(new Runnable() {
                        @Override
                        public void run() {
                            Canvas screenCanvas = holder.lockCanvas();
                            screenCanvas.drawBitmap(bm, (float) 0, (float) 0, whiteline);
                            holder.unlockCanvasAndPost(screenCanvas);
                        }
                    });
                }
            }).start();
        }
    }

    //maps the value startVal, which ranges from smin to smax, to the range (emin, emax)
    private double lirp(double startVal, double smin, double smax, double emin, double emax) {
        return emin + (emax - emin) * ((startVal - smin) / (smax - smin));
    }

    //Handles all the touch events on the Graph, which include touching roots, calculated points and the apex
    @SuppressWarnings("UnusedAssignment")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(activity.equals("Tracing")) {
            boolean nearSomething;
            //Check, wether the touch was close enough to one of the roots and if so, put highlight on the corresponding textview (Not sure yet which highlight to pick)
            if (0 < GraphActivity.roots && (double) touchTolerance > sqrt(Math.pow((double) event.getX() - lirp(GraphActivity.x1, xmin, xmax, (double) 0, (double) canvasWidth), 2.0) + Math.pow((double) event.getY() - lirp((double) 0, ymin, ymax, (double) canvasHeight, (double) 0), 2.0))) {
                rootTextView1.setTextColor(Color.RED); //setTypeface(null, Typeface.BOLD);
                nearSomething = true;
            } else {
                rootTextView1.setTextColor(Color.WHITE); //setTypeface(null, Typeface.NORMAL);
                nearSomething = false;
            }
            if (2 == GraphActivity.roots && (double) touchTolerance > sqrt(Math.pow((double) event.getX() - lirp(GraphActivity.x2, xmin, xmax, (double) 0, (double) canvasWidth), 2.0) + Math.pow((double) event.getY() - lirp((double) 0, ymin, ymax, (double) canvasHeight, (double) 0), 2.0))) {
                rootTextView2.setTextColor(Color.RED); //setTypeface(null, Typeface.BOLD);
                nearSomething = true;
            } else {
                rootTextView2.setTextColor(Color.WHITE);
                nearSomething = false;
            }
            if ((double) touchTolerance > sqrt(Math.pow((double) event.getX() - lirp(GraphActivity.scheitelx, xmin, xmax, (double) 0, (double) canvasWidth), 2.0) + Math.pow((double) event.getY() - lirp(GraphActivity.scheitely, ymin, ymax, (double) canvasHeight, (double) 0), 2.0))) {
                apexTextView.setTextColor(Color.RED); //setTypeface(null, Typeface.BOLD);
                nearSomething = true;
            } else {
                apexTextView.setTextColor(Color.WHITE);
                nearSomething = false;
            }
            if (!nearSomething) {
                touchX = event.getX();
                drawPoint = true;
                draw();
            }
        } else {
            if(MotionEvent.ACTION_DOWN == event.getActionMasked()) {
                lastx = (double) event.getX();
                lasty = (double) event.getY();
            } else if(MotionEvent.ACTION_MOVE == event.getActionMasked() || MotionEvent.ACTION_UP == event.getActionMasked()) {
                double xchange = lirp((double) event.getX() - lastx, (double) 0, (double) canvasWidth, (double) 0, xmax - xmin);
                xmin -= xchange;
                xmax -= xchange;
                double ychange = lirp((double) event.getY() - lasty, (double) 0, (double) canvasHeight, (double) 0, ymax - ymin);
                ymin += ychange;
                ymax += ychange;
                lastx = (double) event.getX();
                lasty = (double) event.getY();
                activity = "Panning";
                drawPoint = false;
                draw();
            }
        }
        return true;
    }

    private void calculateGridlinePositions() {
        double xspan = xmax - xmin;
        double yspan = ymax - ymin;
        int magordx = (int) Math.floor(Math.log10(xspan));
        double powx = Math.pow(10.0, (double) magordx);
        int magordy = (int) Math.floor(Math.log10(yspan));
        double powy = Math.pow(10.0, (double) magordy);
        int spandurchpowx = (int) Math.floor(xspan / powx);
        int spandurchpowy = (int) Math.floor(yspan / powy);
        if(1 == spandurchpowx) {
            gridIntervX = powx / 5.0;
        } else if (5 > spandurchpowx){
            gridIntervX = powx / 2.0;
        } else {
            gridIntervX = powx;
        }
        if(1 == spandurchpowy) {
            gridIntervY = powy / 5.0;
        } else if (5 > spandurchpowy){
            gridIntervY = powy / 2.0;
        } else {
            gridIntervY = powy;
        }
    }

    private void drawGridLines(Canvas canvas) {
        for(double d = gridIntervX * Math.ceil(xmin / gridIntervX); d <= xmax; d += gridIntervX) {
            long lirped = (long) (int) lirp(d, xmin, xmax, (double) 0, (double) canvasWidth);
            canvas.drawLine((float) lirped, (float) 0, (float) lirped, (float) canvasHeight, gridLines);
        }
        for(double d = gridIntervY * Math.ceil(ymin / gridIntervY); d <= ymax; d += gridIntervY) {
            long lirped = (long) (int) lirp(d, ymin, ymax, (double) canvasHeight, (double) 0);
            canvas.drawLine((float) 0, (float) lirped, (float) canvasWidth, (float) lirped, gridLines);
        }
    }

    //todo drawAxisLabels schreiben
    /*
    private void drawAxisLabels(Canvas canvas) {
        double lisx, lisy;
        for(double d = lisx * Math.ceil(xmin / lisx); d <= xmax; d += lisx) {

        }
    }
    */
}