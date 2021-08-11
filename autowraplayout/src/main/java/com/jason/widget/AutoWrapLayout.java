package com.jason.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

/**
 * Created by wuzhiqiang on 2/8/21
 * Email: wuzhiqiang@bigo.sg
 * Description: 此为自定义自动换行布局，可用于包裹多个不定宽高的子 View，并使其自动横向排列、自动调整换行
 */
public class AutoWrapLayout extends ViewGroup {

    /**
     * 布局中每两个子 View 之间的边距，该布局下，每个子 View 设置的 margin 值将会失效，统一为 spacing 值
     */
    private int mSpacing = 0;

    /* Override constructors start */
    public AutoWrapLayout(Context context) {
        this(context, null);
    }

    public AutoWrapLayout(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public AutoWrapLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(attrs);
    }
    /* Override constructors end */

    private void initView(AttributeSet attributeSet) {
        TypedArray a = getContext().obtainStyledAttributes(attributeSet, R.styleable.AutoWrapLayout);
        mSpacing = a.getDimensionPixelSize(R.styleable.AutoWrapLayout_spacing, 0);
        a.recycle();
    }

    /**
     * 重写 onMeasure 方法以实现换行后的宽高计算
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 当宽、高的测量模式为 EXACTLY 时，直接触发子 View 的测量即可
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            measureChildren(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // 当宽、高的测量模式为 AT_MOST 时，计算子 View 的换行排列结果，测试实际的宽高值
        int maxWidth = 0; // 完成布局所需的最大宽度
        int maxHeight = 0; // 完成布局所需的最大高度
        int childState = 0;

        int measureWidth = MeasureSpec.getSize(widthMeasureSpec);
        int currentLineWidth = 0; // 当前行宽
        int currentLineHeight = 0; // 当前行高
        int previousLinesHeight = 0; // 已累计的行高（除开当前行）
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            measureChild(child, widthMeasureSpec, heightMeasureSpec); // 先测量该子 View 的宽高
            if (currentLineWidth + child.getMeasuredWidth() > measureWidth - getPaddingLeft() - getPaddingRight()) {
                // 宽度不足时自动换行
                currentLineWidth = 0;
                previousLinesHeight += currentLineHeight;
                currentLineHeight = 0;
            }
            currentLineWidth += child.getMeasuredWidth() + mSpacing;
            currentLineHeight = Math.max(currentLineHeight, child.getMeasuredHeight() + mSpacing);
            maxWidth = Math.max(maxWidth, currentLineWidth - mSpacing);
            maxHeight = Math.max(maxHeight, previousLinesHeight + currentLineHeight - mSpacing);
            childState = combineMeasuredStates(childState, child.getMeasuredState());
        }
        // 最大宽高的计算加上 padding 值
        maxWidth += getPaddingLeft() + getPaddingRight();
        maxHeight += getPaddingTop() + getPaddingBottom();
        // 再做一次与最小建议宽高的比较
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        setMeasuredDimension(
                resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                resolveSizeAndState(maxHeight, heightMeasureSpec, childState << MEASURED_HEIGHT_STATE_SHIFT)
        );
    }

    /**
     * 重写 onLayout 方法，实现子 View 的自动换行排列，已支持 rtl 布局
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // 判断当前是否为 rtl 布局
        boolean isLayoutRtl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && getLayoutDirection() == LAYOUT_DIRECTION_RTL;

        int currentLineWidth = 0;
        int currentLineHeight = 0;
        int previousLinesHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            // 宽度不足时，自动换行，忽略 view 的 margin
            if (currentLineWidth + child.getMeasuredWidth() > getMeasuredWidth() - getPaddingLeft() - getPaddingRight()) {
                currentLineWidth = 0;
                previousLinesHeight += currentLineHeight;
                currentLineHeight = 0;
            }
            int top = getPaddingTop() + previousLinesHeight;
            if (isLayoutRtl) {
                int right = getMeasuredWidth() - getPaddingRight() - currentLineWidth;
                child.layout(right - child.getMeasuredWidth(), top, right, top + child.getMeasuredHeight());
            } else {
                int left = getPaddingLeft() + currentLineWidth;
                child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
            }
            currentLineWidth += child.getMeasuredWidth() + mSpacing;
            currentLineHeight = Math.max(currentLineHeight, child.getMeasuredHeight() + mSpacing);
        }
    }

    /**
     * 适用于 {@link AutoWrapLayout} 填充统一布局子 View 的适配器
     */
    public static abstract class BaseAdapter {

        /**
         * @return 子 View item 的个数
         */
        public abstract int getItemCount();

        /**
         * @param position 当前 item 的位置
         * @return 子 View item 的布局文件
         */
        @LayoutRes
        protected abstract int getLayoutId(int position);

        /**
         * 子 View item 视图已创建
         *
         * @param position 当前 item 的位置
         * @param view     当前 item 的 View 对象
         */
        protected abstract void onViewCreated(int position, @NonNull View view);
    }

    /**
     * 通过自定义 {@link BaseAdapter} 来填充数据
     *
     * @param adapter 自定义 Adapter
     */
    public void setAdapter(@NonNull BaseAdapter adapter) {
        removeAllViews();
        for (int position = 0; position < adapter.getItemCount(); position++) {
            int layoutId = adapter.getLayoutId(position);
            View view = LayoutInflater.from(getContext()).inflate(layoutId, null, false);
            if (view == null) {
                continue;
            }
            addView(view);
            adapter.onViewCreated(position, view);
        }
    }
}
