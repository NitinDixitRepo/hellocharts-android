package lecho.lib.hellocharts.renderer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextUtils;

import lecho.lib.hellocharts.ChartComputator;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.util.AxisStops;
import lecho.lib.hellocharts.util.Utils;
import lecho.lib.hellocharts.view.Chart;

/**
 * Default axes renderer. Can draw maximum four axes - two horizontal(top/bottom) and two vertical(left/right).
 *
 * @author Leszek Wach
 */
public class AxesRenderer {
    private static final int DEFAULT_AXIS_MARGIN_DP = 2;
    // Axis positions and also *Tabs indexes.
    private static final int TOP = 0;
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final int BOTTOM = 3;

    private Chart chart;
    private int axisMargin;

    // 4 text paints for every axis, not all have to be used, indexed with TOP, LEFT, RIGHT, BOTTOM.
    private Paint[] textPaintTab = new Paint[]{new Paint(), new Paint(), new Paint(), new Paint()};
    private Paint linePaint;

    private float[][] axisDrawBufferTab = new float[4][0];
    private final AxisStops[] axisStopsBufferTab = new AxisStops[]{new AxisStops(), new AxisStops(), new AxisStops(),
            new AxisStops()};

    private int[] axisFixedCoordinateTab = new int[4];
    private int[] axisBaselineTab = new int[4];
    private int[] axisLabelWidthTab = new int[4];
    private int[] axisLabelTextAscentTab = new int[4];
    private int[] axisLabelTextDescentTab = new int[4];
    private FontMetricsInt[] fontMetricsTab = new FontMetricsInt[]{new FontMetricsInt(), new FontMetricsInt(),
            new FontMetricsInt(), new FontMetricsInt()};

    private float[] valuesBuff = new float[1];
    private char[] labelBuffer = new char[32];
    private static final char[] labelWidthChars = new char[]{'0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
            '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0'};

    private float density;
    private float scaledDensity;

    public AxesRenderer(Context context, Chart chart) {
        this.chart = chart;

        density = context.getResources().getDisplayMetrics().density;
        scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        axisMargin = Utils.dp2px(density, DEFAULT_AXIS_MARGIN_DP);

        linePaint = new Paint();
        linePaint.setAntiAlias(true);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1);

        for (Paint paint : textPaintTab) {
            paint.setAntiAlias(true);
        }
    }

    public void initAxesAttributes() {

        int axisXTopHeight = initAxisAttributes(chart.getChartData().getAxisXTop(), TOP);
        int axisXBottomHeight = initAxisAttributes(chart.getChartData().getAxisXBottom(), BOTTOM);

        int axisYLeftWidth = initAxisAttributes(chart.getChartData().getAxisYLeft(), LEFT);
        int axisYRightWidth = initAxisAttributes(chart.getChartData().getAxisYRight(), RIGHT);

        chart.getChartComputator().setAxesMargin(axisYLeftWidth, axisXTopHeight, axisYRightWidth, axisXBottomHeight);
    }

    public void draw(Canvas canvas) {
        if (null != chart.getChartData().getAxisYLeft()) {
            drawAxisVertical(canvas, LEFT);
        }

        if (null != chart.getChartData().getAxisYRight()) {
            drawAxisVertical(canvas, RIGHT);
        }

        if (null != chart.getChartData().getAxisXBottom()) {
            drawAxisHorizontal(canvas, BOTTOM);
        }

        if (null != chart.getChartData().getAxisXTop()) {
            drawAxisHorizontal(canvas, TOP);
        }
    }

    public void drawInBackground(Canvas canvas) {

    }

    public void drawInForeground(Canvas canvas) {

    }

    /**
     * Initialize attributes and measurement for axes(left, right, top, bottom); Returns axis measured width( for left
     * and right) or height(for top and bottom).
     */
    private int initAxisAttributes(Axis axis, int position) {
        if (null == axis) {
            return 0;
        }

        Typeface typeface = axis.getTypeface();
        if (null != typeface) {
            textPaintTab[position].setTypeface(typeface);
        }

        textPaintTab[position].setColor(axis.getTextColor());
        textPaintTab[position].setTextSize(Utils.sp2px(scaledDensity, axis.getTextSize()));
        textPaintTab[position].getFontMetricsInt(fontMetricsTab[position]);

        axisLabelTextAscentTab[position] = Math.abs(fontMetricsTab[position].ascent);
        axisLabelTextDescentTab[position] = Math.abs(fontMetricsTab[position].descent);
        axisLabelWidthTab[position] = (int) textPaintTab[position].measureText(labelWidthChars, 0,
                axis.getMaxLabelChars());

        int result = 0;

        if (LEFT == position || RIGHT == position) {

            int width = 0;

            // If auto-generated or has manual values add height for value labels.
            if ((axis.isAutoGenerated() || !axis.getValues().isEmpty()) && !axis.isInside()) {
                width += axisLabelWidthTab[position];
                width += axisMargin;
            }

            // If has name add height for axis name text.
            if (!TextUtils.isEmpty(axis.getName())) {
                width += axisLabelTextAscentTab[position];
                width += axisLabelTextDescentTab[position];
                width += axisMargin;
            }

            result = width;

        } else if (TOP == position || BOTTOM == position) {

            int height = 0;

            // If auto-generated or has manual values add height for value labels.
            if ((axis.isAutoGenerated() || !axis.getValues().isEmpty()) && !axis.isInside()) {
                height += axisLabelTextAscentTab[position];
                height += axisLabelTextDescentTab[position];
                height += axisMargin;
            }

            // If has name add height for axis name text.
            if (!TextUtils.isEmpty(axis.getName())) {
                height += axisLabelTextAscentTab[position];
                height += axisLabelTextDescentTab[position];
                height += axisMargin;
            }

            result = height;

        } else {
            throw new IllegalArgumentException("Invalid axis position: " + position);
        }

        return result;
    }

    // ********** HORIZONTAL X AXES ****************

    private void drawAxisHorizontal(Canvas canvas, int position) {
        final ChartComputator computator = chart.getChartComputator();

        textPaintTab[position].setTextAlign(Align.CENTER);

        final Axis axis;
        final float separationBaseline;

        if (BOTTOM == position) {
            axis = chart.getChartData().getAxisXBottom();

            if (axis.isInside()) {
                axisFixedCoordinateTab[position] = computator.getContentRectWithMargins().bottom - axisMargin - axisLabelTextDescentTab[position];
                axisBaselineTab[position] = computator.getContentRectWithMargins().bottom + axisLabelTextAscentTab[position]
                        + axisMargin;
            } else {
                axisFixedCoordinateTab[position] = computator.getContentRectWithMargins().bottom + axisLabelTextAscentTab[position] + axisMargin;
                axisBaselineTab[position] = axisFixedCoordinateTab[position] + axisMargin + axisLabelTextAscentTab[position] + axisLabelTextDescentTab[position];
            }

            separationBaseline = computator.getContentRect().bottom;

        } else if (TOP == position) {
            axis = chart.getChartData().getAxisXTop();
            if (axis.isInside()) {
                axisFixedCoordinateTab[position] = computator.getContentRectWithMargins().top + axisMargin + axisLabelTextAscentTab[position];
                axisBaselineTab[position] = computator.getContentRectWithMargins().top - axisMargin
                        - axisLabelTextDescentTab[position];
            } else {
                axisFixedCoordinateTab[position] = computator.getContentRectWithMargins().top - axisMargin - axisLabelTextDescentTab[position];
                axisBaselineTab[position] = axisFixedCoordinateTab[position] - axisMargin - axisLabelTextAscentTab[position] - axisLabelTextDescentTab[position];
            }

            separationBaseline = computator.getContentRect().top;

        } else {
            throw new IllegalArgumentException("Invalid position for horizontal axis: " + position);
        }

        if (axis.isAutoGenerated()) {
            drawAxisHorizontalAuto(canvas, axis, axisFixedCoordinateTab[position], position);
        } else {
            drawAxisHorizontal(canvas, axis, axisFixedCoordinateTab[position], position);
        }

        // Drawing axis name
        if (!TextUtils.isEmpty(axis.getName())) {
            canvas.drawText(axis.getName(), computator.getContentRect().centerX(), axisBaselineTab[position], textPaintTab[position]);
        }

        // Draw separation line with the same color as axis text. Only horizontal axes have separation lines.
        canvas.drawLine(computator.getContentRectWithMargins().left, separationBaseline,
                computator.getContentRectWithMargins().right, separationBaseline, textPaintTab[position]);
    }

    private void drawAxisHorizontal(Canvas canvas, Axis axis, float rawY, int position) {
        final ChartComputator computator = chart.getChartComputator();
        final Viewport maxViewport = computator.getMaximumViewport();
        final Viewport visibleViewport = computator.getVisibleViewport();
        final Rect contentRect = computator.getContentRect();
        final Rect contentRectMargins = computator.getContentRectWithMargins();
        float scale = maxViewport.width() / visibleViewport.width();

        final int module = (int) Math.ceil(axis.getValues().size() * axisLabelWidthTab[position]
                / (contentRect.width() * scale));

        if (axis.hasLines() && axisDrawBufferTab[position].length < axis.getValues().size() * 4) {
            axisDrawBufferTab[position] = new float[axis.getValues().size() * 4];
        }

        int lineIndex = 0;
        int valueIndex = 0;

        for (AxisValue axisValue : axis.getValues()) {
            final float value = axisValue.getValue();

            // Draw axis values that area within visible viewport.
            if (value >= visibleViewport.left && value <= visibleViewport.right) {

                // Draw axis values that have 0 module value, this will hide some labels if there is no place for them.
                if (0 == valueIndex % module) {

                    final float rawX = computator.computeRawX(axisValue.getValue());

                    if (checkRawX(contentRect, rawX, axis.isInside(), position)) {

                        valuesBuff[0] = axisValue.getValue();
                        final int nummChars = axis.getFormatter().formatValue(labelBuffer, valuesBuff,
                                axisValue.getLabel());

                        canvas.drawText(labelBuffer, labelBuffer.length - nummChars, nummChars, rawX, rawY,
                                textPaintTab[position]);

                        if (axis.hasLines()) {
                            axisDrawBufferTab[position][lineIndex * 4 + 0] = rawX;
                            axisDrawBufferTab[position][lineIndex * 4 + 1] = contentRectMargins.top;
                            axisDrawBufferTab[position][lineIndex * 4 + 2] = rawX;
                            axisDrawBufferTab[position][lineIndex * 4 + 3] = contentRectMargins.bottom;
                            ++lineIndex;
                        }
                    }
                }
                // If within viewport - increment valueIndex;
                ++valueIndex;
            }
        }

        if (axis.hasLines()) {
            linePaint.setColor(axis.getLineColor());
            canvas.drawLines(axisDrawBufferTab[position], 0, lineIndex * 4, linePaint);
        }
    }

    private void drawAxisHorizontalAuto(Canvas canvas, Axis axis, float rawY, int position) {
        final ChartComputator computator = chart.getChartComputator();
        final Viewport visibleViewport = computator.getVisibleViewport();
        final Rect contentRect = computator.getContentRect();
        final Rect contentRectMargins = computator.getContentRectWithMargins();

        Utils.computeAxisStops(visibleViewport.left, visibleViewport.right, contentRect.width()
                / axisLabelWidthTab[position] / 2, axisStopsBufferTab[position]);

        if (axis.hasLines() && axisDrawBufferTab[position].length < axisStopsBufferTab[position].numStops * 4) {
            axisDrawBufferTab[position] = new float[axisStopsBufferTab[position].numStops * 4];
        }

        int lineIndex = 0;

        for (int i = 0; i < axisStopsBufferTab[position].numStops; ++i) {
            float rawX = computator.computeRawX(axisStopsBufferTab[position].stops[i]);

            if (checkRawX(contentRect, rawX, axis.isInside(), position)) {

                valuesBuff[0] = axisStopsBufferTab[position].stops[i];
                final int nummChars = axis.getFormatter().formatValue(labelBuffer, valuesBuff, null,
                        axisStopsBufferTab[position].decimals);
                canvas.drawText(labelBuffer, labelBuffer.length - nummChars, nummChars, rawX, rawY,
                        textPaintTab[position]);

                if (axis.hasLines()) {
                    axisDrawBufferTab[position][lineIndex * 4 + 0] = rawX;
                    axisDrawBufferTab[position][lineIndex * 4 + 1] = contentRectMargins.top;
                    axisDrawBufferTab[position][lineIndex * 4 + 2] = rawX;
                    axisDrawBufferTab[position][lineIndex * 4 + 3] = contentRectMargins.bottom;
                    ++lineIndex;
                }

            }
        }

        if (axis.hasLines()) {
            linePaint.setColor(axis.getLineColor());
            canvas.drawLines(axisDrawBufferTab[position], 0, lineIndex * 4, linePaint);
        }
    }

    /**
     * For axis inside chart area this method checks if there is place to draw axis label. If yes returns true,
     * otherwise false.
     */
    private boolean checkRawX(Rect rect, float rawX, boolean axisInside, int position) {
        if (axisInside) {
            float margin = axisLabelWidthTab[position] / 2;
            if (rawX >= rect.left + margin && rawX <= rect.right - margin) {
                return true;
            } else {
                return false;
            }
        }

        return true;

    }

    // ********** VERTICAL Y AXES ****************

    private void drawAxisVertical(Canvas canvas, int position) {
        final ChartComputator computator = chart.getChartComputator();

        final Axis axis;

        if (LEFT == position) {
            axis = chart.getChartData().getAxisYLeft();
            textPaintTab[position].setTextAlign(Align.RIGHT);

            if (axis.isInside()) {
                axisFixedCoordinateTab[position] = computator.getContentRectWithMargins().left + axisMargin + axisLabelWidthTab[position];
                axisBaselineTab[position] = computator.getContentRectWithMargins().left - axisMargin
                        - axisLabelTextDescentTab[position];
            } else {
                axisFixedCoordinateTab[position] = computator.getContentRectWithMargins().left - axisMargin;
                axisBaselineTab[position] = axisFixedCoordinateTab[position] - axisLabelWidthTab[position] - axisMargin - axisLabelTextDescentTab[position];
            }

        } else if (RIGHT == position) {
            textPaintTab[position].setTextAlign(Align.LEFT);
            axis = chart.getChartData().getAxisYRight();

            if (axis.isInside()) {
                axisFixedCoordinateTab[position] = computator.getContentRectWithMargins().right - axisMargin - axisLabelWidthTab[position];
                axisBaselineTab[position] = computator.getContentRectWithMargins().right + axisMargin
                        + axisLabelTextAscentTab[position];
            } else {
                axisFixedCoordinateTab[position] = computator.getContentRectWithMargins().right + axisMargin;
                axisBaselineTab[position] = axisFixedCoordinateTab[position] + axisLabelWidthTab[position] + axisMargin + axisLabelTextAscentTab[position];
            }
        } else {
            throw new IllegalArgumentException("Invalid position for horizontal axis: " + position);
        }

        // drawing axis values
        if (axis.isAutoGenerated()) {
            drawAxisVerticalAuto(canvas, axis, axisFixedCoordinateTab[position], position);
        } else {
            drawAxisVertical(canvas, axis, axisFixedCoordinateTab[position], position);
        }

        // drawing axis name
        if (!TextUtils.isEmpty(axis.getName())) {
            textPaintTab[position].setTextAlign(Align.CENTER);
            canvas.save();
            canvas.rotate(-90, computator.getContentRect().centerY(), computator.getContentRect().centerY());
            canvas.drawText(axis.getName(), computator.getContentRect().centerY(), axisBaselineTab[position], textPaintTab[position]);
            canvas.restore();
        }
    }

    private void drawAxisVertical(Canvas canvas, Axis axis, float rawX, int position) {
        final ChartComputator computator = chart.getChartComputator();
        final Viewport maxViewport = computator.getMaximumViewport();
        final Viewport visibleViewport = computator.getVisibleViewport();
        final Rect contentRect = computator.getContentRect();
        final Rect contentRectMargins = computator.getContentRectWithMargins();
        float scale = maxViewport.height() / visibleViewport.height();

        final int module = (int) Math.ceil(axis.getValues().size() * axisLabelTextAscentTab[position] * 2
                / (contentRect.height() * scale));

        if (axis.hasLines() && axisDrawBufferTab[position].length < axis.getValues().size() * 4) {
            axisDrawBufferTab[position] = new float[axis.getValues().size() * 4];
        }

        int lineIndex = 0;
        int valueIndex = 0;

        for (AxisValue axisValue : axis.getValues()) {
            final float value = axisValue.getValue();

            // Draw axis values that area within visible viewport.
            if (value >= visibleViewport.bottom && value <= visibleViewport.top) {

                // Draw axis values that have 0 module value, this will hide some labels if there is no place for them.
                if (0 == valueIndex % module) {

                    final float rawY = computator.computeRawY(value);

                    if (checkRawY(contentRect, rawY, axis.isInside(), position)) {

                        valuesBuff[0] = axisValue.getValue();
                        final int nummChars = axis.getFormatter().formatValue(labelBuffer, valuesBuff,
                                axisValue.getLabel());

                        canvas.drawText(labelBuffer, labelBuffer.length - nummChars, nummChars, rawX, rawY,
                                textPaintTab[position]);

                        if (axis.hasLines()) {
                            axisDrawBufferTab[position][lineIndex * 4 + 0] = contentRectMargins.left;
                            axisDrawBufferTab[position][lineIndex * 4 + 1] = rawY;
                            axisDrawBufferTab[position][lineIndex * 4 + 2] = contentRectMargins.right;
                            axisDrawBufferTab[position][lineIndex * 4 + 3] = rawY;
                            ++lineIndex;
                        }
                    }
                }
                // If within viewport - increment valueIndex;
                ++valueIndex;
            }
        }

        if (axis.hasLines()) {
            linePaint.setColor(axis.getLineColor());
            canvas.drawLines(axisDrawBufferTab[position], 0, lineIndex * 4, linePaint);
        }
    }

    private void drawAxisVerticalAuto(Canvas canvas, Axis axis, float rawX, int position) {
        final ChartComputator computator = chart.getChartComputator();
        final Viewport visibleViewport = computator.getVisibleViewport();
        final Rect contentRect = computator.getContentRect();
        final Rect contentRectMargins = computator.getContentRectWithMargins();

        Utils.computeAxisStops(visibleViewport.bottom, visibleViewport.top, contentRect.height()
                / axisLabelTextAscentTab[position] / 2, axisStopsBufferTab[position]);

        if (axis.hasLines() && axisDrawBufferTab[position].length < axisStopsBufferTab[position].numStops * 4) {
            axisDrawBufferTab[position] = new float[axisStopsBufferTab[position].numStops * 4];
        }

        int lineIndex = 0;

        for (int stopIndex = 0; stopIndex < axisStopsBufferTab[position].numStops; stopIndex++) {
            final float rawY = computator.computeRawY(axisStopsBufferTab[position].stops[stopIndex]);

            if (checkRawY(contentRect, rawY, axis.isInside(), position)) {

                valuesBuff[0] = axisStopsBufferTab[position].stops[stopIndex];
                final int nummChars = axis.getFormatter().formatValue(labelBuffer, valuesBuff, null,
                        axisStopsBufferTab[position].decimals);
                canvas.drawText(labelBuffer, labelBuffer.length - nummChars, nummChars, rawX, rawY,
                        textPaintTab[position]);

                if (axis.hasLines()) {
                    axisDrawBufferTab[position][lineIndex * 4 + 0] = contentRectMargins.left;
                    axisDrawBufferTab[position][lineIndex * 4 + 1] = rawY;
                    axisDrawBufferTab[position][lineIndex * 4 + 2] = contentRectMargins.right;
                    axisDrawBufferTab[position][lineIndex * 4 + 3] = rawY;
                    ++lineIndex;
                }
            }
        }

        if (axis.hasLines()) {
            linePaint.setColor(axis.getLineColor());
            canvas.drawLines(axisDrawBufferTab[position], 0, lineIndex * 4, linePaint);
        }
    }

    /**
     * For axis inside chart area this method checks if there is place to draw axis label. If yes returns true,
     * otherwise false.
     */
    private boolean checkRawY(Rect rect, float rawY, boolean axisInside, int position) {
        if (axisInside) {
            float marginBottom = axisLabelTextAscentTab[BOTTOM] + axisMargin;
            float marginTop = axisLabelTextAscentTab[TOP] + axisMargin;
            if (rawY <= rect.bottom - marginBottom && rawY >= rect.top + marginTop) {
                return true;
            } else {
                return false;
            }
        }

        return true;

    }
}
