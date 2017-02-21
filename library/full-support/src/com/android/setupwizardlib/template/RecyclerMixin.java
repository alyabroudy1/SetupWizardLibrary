/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.setupwizardlib.template;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.AttributeSet;
import android.view.View;

import com.android.setupwizardlib.DividerItemDecoration;
import com.android.setupwizardlib.R;
import com.android.setupwizardlib.TemplateLayout;
import com.android.setupwizardlib.items.ItemHierarchy;
import com.android.setupwizardlib.items.ItemInflater;
import com.android.setupwizardlib.items.RecyclerItemAdapter;
import com.android.setupwizardlib.util.DrawableLayoutDirectionHelper;
import com.android.setupwizardlib.view.HeaderRecyclerView;

/**
 * A {@link Mixin} for interacting with templates with recycler views. This mixin constructor takes
 * the instance of the recycler view to allow it to be instantiated dynamically, as in the case for
 * preference fragments.
 *
 * <p>Unlike typical mixins, this mixin is designed to be created in onTemplateInflated, which is
 * called by the super constructor, and then parse the XML attributes later in the constructor.
 */
public class RecyclerMixin implements Mixin {

    private TemplateLayout mTemplateLayout;

    @NonNull
    private final RecyclerView mRecyclerView;

    @Nullable
    private View mHeader;

    @NonNull
    private DividerItemDecoration mDividerDecoration;

    private Drawable mDefaultDivider;
    private Drawable mDivider;
    private int mDividerInset;

    /**
     * Creates the RecyclerMixin. Unlike typical mixins which are created in the constructor, this
     * mixin should be called in {@link TemplateLayout#onTemplateInflated()}, which is called by
     * the super constructor, because the recycler view and the header needs to be made available
     * before other mixins from the super class.
     *
     * @param layout The layout this mixin belongs to.
     */
    public RecyclerMixin(@NonNull TemplateLayout layout, @NonNull RecyclerView recyclerView) {
        mTemplateLayout = layout;

        mDividerDecoration = new DividerItemDecoration(mTemplateLayout.getContext());

        // The recycler view needs to be available
        mRecyclerView = recyclerView;
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mTemplateLayout.getContext()));

        if (recyclerView instanceof HeaderRecyclerView) {
            mHeader = ((HeaderRecyclerView) recyclerView).getHeader();
        }

        mRecyclerView.addItemDecoration(mDividerDecoration);
    }

    /**
     * Parse XML attributes and configures this mixin and the recycler view accordingly. This should
     * be called from the constructor of the layout.
     *
     * @param attrs The {@link AttributeSet} as passed into the constructor. Can be null if the
     *              layout was not created from XML.
     * @param defStyleAttr The default style attribute as passed into the layout constructor. Can be
     *                     0 if it is not needed.
     */
    public void parseAttributes(@Nullable AttributeSet attrs, int defStyleAttr) {
        final Context context = mTemplateLayout.getContext();
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SuwRecyclerMixin, defStyleAttr, 0);

        final int entries = a.getResourceId(R.styleable.SuwRecyclerMixin_android_entries, 0);
        if (entries != 0) {
            final ItemHierarchy inflated = new ItemInflater(context).inflate(entries);
            final RecyclerItemAdapter adapter = new RecyclerItemAdapter(inflated);
            adapter.setHasStableIds(a.getBoolean(
                    R.styleable.SuwRecyclerMixin_suwHasStableIds, false));
            setAdapter(adapter);
        }
        int dividerInset =
                a.getDimensionPixelSize(R.styleable.SuwRecyclerMixin_suwDividerInset, 0);
        setDividerInset(dividerInset);
        a.recycle();
    }

    /**
     * @return The recycler view contained in the layout, as marked by
     *         {@code @id/suw_recycler_view}. This will return {@code null} if the recycler view
     *         doesn't exist in the layout.
     */
    @SuppressWarnings("NullableProblems") // If clients guarantee that the template has a recycler
                                          // view, and call this after the template is inflated,
                                          // this will not return null.
    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    /**
     * Gets the header view of the recycler layout. This is useful for other mixins if they need to
     * access views within the header, usually via {@link TemplateLayout#findManagedViewById(int)}.
     */
    @SuppressWarnings("NullableProblems") // If clients guarantee that the template has a header,
                                          // this call will not return null.
    public View getHeader() {
        return mHeader;
    }

    /**
     * Recycler mixin needs to update the dividers if the layout direction has changed. This method
     * should be called when {@link View#onLayout(boolean, int, int, int, int)} of the template
     * is called.
     */
    public void onLayout() {
        if (mDivider == null) {
            // Update divider in case layout direction has just been resolved
            updateDivider();
        }
    }

    /**
     * Gets the adapter of the recycler view in this layout. If the adapter includes a header,
     * this method will unwrap it and return the underlying adapter.
     *
     * @return The adapter, or {@code null} if the recycler view has no adapter.
     */
    public Adapter getAdapter() {
        final RecyclerView.Adapter adapter = mRecyclerView.getAdapter();
        if (adapter instanceof HeaderRecyclerView.HeaderAdapter) {
            return ((HeaderRecyclerView.HeaderAdapter) adapter).getWrappedAdapter();
        }
        return adapter;
    }

    /**
     * Sets the adapter on the recycler view in this layout.
     */
    public void setAdapter(Adapter adapter) {
        mRecyclerView.setAdapter(adapter);
    }

    /**
     * Sets the start inset of the divider. This will use the default divider drawable set in the
     * theme and inset it {@code inset} pixels to the right (or left in RTL layouts).
     *
     * @param inset The number of pixels to inset on the "start" side of the list divider. Typically
     *              this will be either {@code @dimen/suw_items_glif_icon_divider_inset} or
     *              {@code @dimen/suw_items_glif_text_divider_inset}.
     */
    public void setDividerInset(int inset) {
        mDividerInset = inset;
        updateDivider();
    }

    /**
     * @return The number of pixels inset on the start side of the divider.
     */
    public int getDividerInset() {
        return mDividerInset;
    }

    private void updateDivider() {
        boolean shouldUpdate = true;
        if (Build.VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
            shouldUpdate = mTemplateLayout.isLayoutDirectionResolved();
        }
        if (shouldUpdate) {
            if (mDefaultDivider == null) {
                mDefaultDivider = mDividerDecoration.getDivider();
            }
            mDivider = DrawableLayoutDirectionHelper.createRelativeInsetDrawable(
                    mDefaultDivider,
                    mDividerInset /* start */,
                    0 /* top */,
                    0 /* end */,
                    0 /* bottom */,
                    mTemplateLayout);
            mDividerDecoration.setDivider(mDivider);
        }
    }

    /**
     * @return The drawable used as the divider.
     */
    public Drawable getDivider() {
        return mDivider;
    }

    /**
     * Sets the divider item decoration directly. This is a low level method which should be used
     * only if custom divider behavior is needed, for example if the divider should be shown /
     * hidden in some specific cases for view holders that cannot implement
     * {@link com.android.setupwizardlib.DividerItemDecoration.DividedViewHolder}.
     */
    public void setDividerItemDecoration(@NonNull DividerItemDecoration decoration) {
        mRecyclerView.removeItemDecoration(mDividerDecoration);
        mDividerDecoration = decoration;
        mRecyclerView.addItemDecoration(mDividerDecoration);
        updateDivider();
    }
}