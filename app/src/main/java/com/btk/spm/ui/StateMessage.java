package com.btk.spm.ui;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.btk.spm.databinding.ViewStateMessageBinding;

/**
 * Fills the shared {@code view_state_message} layout for one of the three non-happy states, so the
 * Pantry tab, the Recipes tab and the recipe detail screen show loading, empty and error the same
 * way (Issue 30).
 *
 * <p>Each method sets every part of the layout, shown or hidden, so a state never inherits a part
 * of the one before it: a loading spinner cannot sit under an error message, and an empty state
 * cannot keep the button of another.
 */
public final class StateMessage {

    /** No string: the part it would fill is hidden. */
    public static final int NONE = 0;

    private StateMessage() {
        // Static helpers only; never instantiated
    }

    /**
     * Shows the progress indicator and nothing else. The indicator waits before it appears, so work
     * that finishes quickly shows no spinner.
     *
     * @param views         the included layout
     * @param description   what TalkBack says about the indicator, such as "Matching recipes to your pantry"
     */
    public static void showLoading(@NonNull ViewStateMessageBinding views, @StringRes int description) {
        views.getRoot().setVisibility(View.VISIBLE);
        views.stateIllustration.setVisibility(View.GONE);
        views.stateHeadline.setVisibility(View.GONE);
        views.stateBody.setVisibility(View.GONE);
        views.stateAction.setVisibility(View.GONE);
        views.stateProgress.setContentDescription(views.getRoot().getContext().getString(description));
        views.stateProgress.show();
    }

    /**
     * Shows the illustration, a headline, an optional body and an optional button: an empty or an
     * error state.
     *
     * @param views    the included layout
     * @param headline what is missing or went wrong
     * @param body     a second sentence, or {@link #NONE}
     * @param action   the button's label, or {@link #NONE} for no button
     * @param onAction what the button does; ignored without a button
     */
    public static void showMessage(@NonNull ViewStateMessageBinding views, @StringRes int headline,
                                   @StringRes int body, @StringRes int action, @Nullable View.OnClickListener onAction) {
        views.getRoot().setVisibility(View.VISIBLE);
        views.stateProgress.hide();
        views.stateIllustration.setVisibility(View.VISIBLE);
        views.stateHeadline.setVisibility(View.VISIBLE);
        views.stateHeadline.setText(headline);
        views.stateBody.setVisibility(body == NONE ? View.GONE : View.VISIBLE);
        if (body != NONE) {
            views.stateBody.setText(body);
        }
        views.stateAction.setVisibility(action == NONE ? View.GONE : View.VISIBLE);
        if (action != NONE) {
            views.stateAction.setText(action);
            views.stateAction.setOnClickListener(onAction);
        }
    }

    /**
     * Hides the whole layout, for when the screen has content to show.
     *
     * @param views the included layout
     */
    public static void hide(@NonNull ViewStateMessageBinding views) {
        views.stateProgress.hide();
        views.getRoot().setVisibility(View.GONE);
    }
}
