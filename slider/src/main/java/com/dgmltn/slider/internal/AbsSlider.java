package com.dgmltn.slider.internal;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.support.annotation.IntDef;
import android.util.AttributeSet;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

import com.dgmltn.slider.R;
import com.dgmltn.slider.ThumbView;

/**
 * Created by doug on 11/1/15.
 */
public abstract class AbsSlider extends ViewGroup implements ThumbView.OnValueChangedListener {

	private static final int DEFAULT_TICK_COLOR = Color.BLACK;
	private static final int DEFAULT_TEXT_COLOR = Color.WHITE;

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({ STYLE_CONTINUOUS, STYLE_DISCRETE })
	public @interface SliderStyle {
	}

	public static final int STYLE_CONTINUOUS = 0;
	public static final int STYLE_DISCRETE = 1;

	// Bar properties
	private boolean hasTicks = false;
	private
	@SliderStyle
	int sliderStyle = STYLE_CONTINUOUS;
	protected int max = 100;
	protected int thumbs = 1;

	// Colors
	private ColorStateList trackColor;
	private ColorStateList tickColor = ColorStateList.valueOf(DEFAULT_TICK_COLOR);

	// Properties for autogenerated thumbs
	private ColorStateList thumbColor;
	private
	@ThumbView.ThumbStyle
	int thumbStyle = ThumbView.STYLE_CIRCLE;
	private int thumbTextColor = DEFAULT_TEXT_COLOR;

	public AbsSlider(Context context, AttributeSet attrs) {
		super(context, attrs);
		setClipToPadding(false);
		setClipChildren(false);
		setWillNotDraw(false);

		if (attrs != null) {
			TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.AbsSlider, 0, 0);
			max = ta.getInteger(R.styleable.AbsSlider_max, max);
			thumbs = ta.getInteger(R.styleable.AbsSlider_thumbs, thumbs);
			hasTicks = ta.getBoolean(R.styleable.AbsSlider_hasTicks, hasTicks);
			sliderStyle = validateSliderStyle(ta.getInt(R.styleable.AbsSlider_style, sliderStyle));

			if (ta.hasValue(R.styleable.AbsSlider_trackColor)) {
				trackColor = ta.getColorStateList(R.styleable.AbsSlider_trackColor);
			}
			else {
				trackColor = generateDefaultTrackColorStateListFromTheme(context);
			}

			if (ta.hasValue(R.styleable.AbsSlider_tickColor)) {
				tickColor = ta.getColorStateList(R.styleable.AbsSlider_tickColor);
			}

			if (ta.hasValue(R.styleable.AbsSlider_thumb_color)) {
				thumbColor = ta.getColorStateList(R.styleable.AbsSlider_thumb_color);
			}
			else {
				thumbColor = ThumbView.generateDefaultColorStateListFromTheme(context);
			}

			thumbStyle = ThumbView.validateThumbStyle(ta.getInt(R.styleable.AbsSlider_thumb_style, thumbStyle));

			thumbTextColor = ta.getColor(R.styleable.AbsSlider_thumb_textColor, thumbTextColor);
			ta.recycle();
		}

		initTrack();
	}

	private static
	@SliderStyle
	int validateSliderStyle(int s) {
		return s == STYLE_DISCRETE ? s : STYLE_CONTINUOUS;
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		// Don't create any thumbs automatically if the user created his own.
		// Let's still listen to value changed.
		if (getChildCount() != 0) {
			thumbs = 0;
			for (int i = 0; i < getChildCount(); i++) {
				getChildAt(i).addOnValueChangedListener(this);
			}
		}
		else {
			for (int i = 0; i < thumbs; i++) {
				ThumbView thumb = new ThumbView(getContext(), null);
				thumb.setThumbStyle(thumbStyle);
				thumb.setImageTintList(thumbColor);
				thumb.setTextColor(thumbTextColor);
				thumb.addOnValueChangedListener(this);
				addView(thumb);
			}
		}
	}

	public static ColorStateList generateDefaultTrackColorStateListFromTheme(Context context) {
		int[][] states = new int[][] {
			SELECTED_STATE_SET,
			ENABLED_STATE_SET,
			StateSet.WILD_CARD
		};

		// This is an approximation of the track colors derived from Lollipop resources
		boolean isLightTheme = Utils.isLightTheme(context);
		int enabled = isLightTheme ? 0x66000000 : 0x85ffffff;

		int[] colors = new int[] {
			Utils.getThemeColor(context, android.R.attr.colorControlActivated),
			enabled,
			0x420000ff //TODO: get this from a theme attr?
		};

//		ContextCompat.getColor(context, R.color.track_color),

		return new ColorStateList(states, colors);
	}

	public int getMax() {
		return max;
	}

	public void setMax(int max) {
		this.max = max;
	}

	/////////////////////////////////////////////////////////////////////////
	// Layout
	/////////////////////////////////////////////////////////////////////////

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
		int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

		for (int i = 0; i < getChildCount(); i++) {
			View child = getChildAt(i);
			child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		for (int i = 0; i < getChildCount(); i++) {
			ThumbView child = getChildAt(i);
			getPointOnBar(mTmpPointF, child.getValue());
			mTmpPointF.x -= child.getMeasuredWidth() / 2f;
			mTmpPointF.y -= child.getMeasuredHeight() / 2f;
			child.layout(
				(int) mTmpPointF.x,
				(int) mTmpPointF.y,
				(int) mTmpPointF.x + child.getMeasuredWidth(),
				(int) mTmpPointF.y + child.getMeasuredHeight()
			);
		}
	}

	@Override
	protected LayoutParams generateDefaultLayoutParams() {
		return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
	}

	@Override
	protected LayoutParams generateLayoutParams(LayoutParams p) {
		return new MarginLayoutParams(p);
	}

	@Override
	public LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new MarginLayoutParams(getContext(), attrs);
	}

	@Override
	public ThumbView getChildAt(int index) {
		return (ThumbView) super.getChildAt(index);
	}

	@Override
	public void onValueChange(ThumbView thumb, float oldVal, float newVal) {
		getPointOnBar(mTmpPointF, newVal);
		float dx = mTmpPointF.x - thumb.getMeasuredWidth() / 2f - thumb.getLeft();
		float dy = mTmpPointF.y - thumb.getMeasuredHeight() / 2f - thumb.getTop();
		thumb.offsetLeftAndRight((int) dx);
		thumb.offsetTopAndBottom((int) dy);
		invalidate();
	}

	/////////////////////////////////////////////////////////////////////////
	// Taps
	/////////////////////////////////////////////////////////////////////////

	private int expanded = -1;

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		super.onTouchEvent(event);

		// If this View is not enabled, don't allow for touch interactions.
		if (!isEnabled()) {
			return false;
		}

		switch (event.getAction()) {

		case MotionEvent.ACTION_DOWN:
			float dmin = Float.MAX_VALUE;
			expanded = -1;
			for (int i = 0; i < getChildCount(); i++) {
				ThumbView thumb = getChildAt(i);
				if (!thumb.isClickable()) {
					continue;
				}
				float d = distance(thumb.getValue(), event.getX(), event.getY());
				if (d < dmin) {
					dmin = d;
					// 48dp touch region
					if (d <= getResources().getDimension(R.dimen.default_touch_radius)) {
						expanded = i;
					}
				}
			}
			if (expanded > -1) {
				ThumbView thumb = getChildAt(expanded);
				thumb.setPressed(true);
				cancelLongPress();
				attemptClaimDrag();
				onStartTrackingTouch();
			}
			break;

		case MotionEvent.ACTION_MOVE:
			if (expanded > -1) {
				float value = getNearestBarValue(event.getX(), event.getY());
				getChildAt(expanded).setValue(value);
			}
			break;

		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (expanded > -1) {
				ThumbView thumb = getChildAt(expanded);
				thumb.setPressed(false);
				onStopTrackingTouch();
				expanded = -1;
				if (sliderStyle == STYLE_DISCRETE) {
					float value = Math.round(thumb.getValue());
					ObjectAnimator anim = ObjectAnimator.ofFloat(thumb, "value", value).setDuration(100);
					anim.setInterpolator(new DecelerateInterpolator());
					anim.start();
				}
			}
			break;
		}

		return true;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		return true;
	}

	private float distance(float value, float x, float y) {
		getPointOnBar(mTmpPointF, value);
		float m = mTmpPointF.x - x;
		float n = mTmpPointF.y - y;
		return (float) Math.sqrt(m * m + n * n);
	}

	/**
	 * Tries to claim the user's drag motion, and requests disallowing any
	 * ancestors from stealing events in the drag.
	 * https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/widget/AbsSeekBar.java
	 */
	private void attemptClaimDrag() {
		ViewParent parent = getParent();
		if (parent != null) {
			parent.requestDisallowInterceptTouchEvent(true);
		}
	}

	/////////////////////////////////////////////////////////////////////////
	// Rendering
	/////////////////////////////////////////////////////////////////////////

	protected Paint mTrackOffPaint;
	protected Paint mTrackOnPaint;
	protected Paint mTickPaint;

	PointF mTmpPointF = new PointF();

	protected void initTrack() {
		Resources res = getResources();

		// Initialize the paint.
		mTrackOffPaint = new Paint();
		mTrackOffPaint.setAntiAlias(true);
		mTrackOffPaint.setStyle(Paint.Style.STROKE);
		mTrackOffPaint.setColor(trackColor.getColorForState(ENABLED_STATE_SET, trackColor.getDefaultColor()));
		mTrackOffPaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.default_track_width));

		mTickPaint = new Paint();
		mTickPaint.setAntiAlias(true);
		mTickPaint.setColor(tickColor.getColorForState(ENABLED_STATE_SET, tickColor.getDefaultColor()));

		// Initialize the paint, set values
		mTrackOnPaint = new Paint();
		mTrackOnPaint.setStrokeCap(Paint.Cap.ROUND);
		mTrackOnPaint.setStyle(Paint.Style.STROKE);
		mTrackOnPaint.setAntiAlias(true);
		mTrackOnPaint.setColor(trackColor.getColorForState(SELECTED_STATE_SET, trackColor.getDefaultColor()));
		mTrackOnPaint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.default_track_width));
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		drawBar(canvas, mTrackOffPaint);
		if (hasTicks) {
			drawTicks(canvas);
		}
		if (getChildCount() == 1) {
			drawConnectingLine(canvas, 0f, getChildAt(0).getValue(), mTrackOnPaint);
		}
		else if (getChildCount() == 2) {
			drawConnectingLine(canvas, getChildAt(0).getValue(), getChildAt(1).getValue(), mTrackOnPaint);
		}
	}

	/**
	 * Draws the tick marks on the bar.
	 *
	 * @param canvas Canvas to draw on; should be the Canvas passed into {#link
	 *               View#onDraw()}
	 */
	protected void drawTicks(Canvas canvas) {
		float radius = getResources().getDimension(R.dimen.default_tick_radius);

		for (int i = 0; i <= max; i++) {
			getPointOnBar(mTmpPointF, i);
			canvas.drawCircle(mTmpPointF.x, mTmpPointF.y, radius, mTickPaint);
		}
	}

	/////////////////////////////////////////////////////////////////////////
	// OnSliderChangeListener
	/////////////////////////////////////////////////////////////////////////

	/**
	 * A callback that notifies clients when the progress level has been
	 * changed. This includes changes that were initiated by the user through a
	 * touch gesture or arrow key/trackball as well as changes that were initiated
	 * programmatically.
	 */
	public interface OnSliderChangeListener {
		/**
		 * Notification that the user has started a touch gesture.
		 *
		 * @param slider The SeekBar in which the touch gesture began
		 */
		void onStartTrackingTouch(AbsSlider slider);

		/**
		 * Notification that the user has finished a touch gesture.
		 *
		 * @param slider The SeekBar in which the touch gesture began
		 */
		void onStopTrackingTouch(AbsSlider slider);
	}

	private OnSliderChangeListener mOnSeekBarChangeListener;

	/**
	 * Sets a listener to receive notifications of changes to the SeekBar's progress level. Also
	 * provides notifications of when the user starts and stops a touch gesture within the SeekBar.
	 *
	 * @param l The seek bar notification listener
	 */
	public void setOnSliderChangeListener(OnSliderChangeListener l) {
		mOnSeekBarChangeListener = l;
	}

	void onStartTrackingTouch() {
		if (mOnSeekBarChangeListener != null) {
			mOnSeekBarChangeListener.onStartTrackingTouch(this);
		}
	}

	void onStopTrackingTouch() {
		if (mOnSeekBarChangeListener != null) {
			mOnSeekBarChangeListener.onStopTrackingTouch(this);
		}
	}

	/////////////////////////////////////////////////////////////////////////
	// Abstract Methods
	/////////////////////////////////////////////////////////////////////////

	/**
	 * Draw the connecting line between the two thumbs in RangeBar.
	 *
	 * @param canvas the Canvas to draw on
	 * @param from   the lower bar value of the connecting line
	 * @param to     the upper bar value of the connecting line
	 */
	protected abstract void drawConnectingLine(Canvas canvas, float from, float to, Paint paint);

	/**
	 * Gets the value of the bar nearest to the passed point.
	 *
	 * @param x the x value to snap to the bar
	 * @param y the y value to snap to the bar
	 */
	protected abstract float getNearestBarValue(float x, float y);

	/**
	 * Gets the coordinates of the bar value.
	 */
	protected abstract void getPointOnBar(PointF out, float value);

	/**
	 * Draws the bar on the given Canvas.
	 *
	 * @param canvas Canvas to draw on; should be the Canvas passed into {#link
	 *               View#onDraw()}
	 */
	protected abstract void drawBar(Canvas canvas, Paint paint);
}
