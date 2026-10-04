package com.btk.spm.ui.pantry;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.btk.spm.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Asks before an ingredient is deleted: "Delete tomato?", with Cancel and Delete (Issue 16).
 *
 * <p>A {@link DialogFragment}, so the {@code FragmentManager} puts it back after a rotation and the
 * question is still on screen (Issue 30); a plain dialog would vanish with the Activity. It deletes
 * nothing itself. Delete hands the item's id back to {@code PantryFragment} as a fragment result under
 * {@link #REQUEST_KEY}, and the Fragment, which owns the ViewModel, deletes it and offers the undo.
 */
public class DeleteIngredientDialog extends DialogFragment {

    /** The fragment result key Delete answers under. */
    public static final String REQUEST_KEY = "com.btk.spm.request.DELETE_INGREDIENT";

    /** The result's and the arguments' key for the item's id. */
    public static final String KEY_ITEM_ID = "com.btk.spm.arg.ITEM_ID";

    /** The arguments' key for the name shown in the title. */
    private static final String KEY_ITEM_NAME = "com.btk.spm.arg.ITEM_NAME";

    /** The tag it is shown under, so the Fragment can tell it is already showing. */
    public static final String TAG = "DeleteIngredientDialog";

    /**
     * Creates the dialog for one item. Only the id and the name travel, in the arguments, which the
     * {@code FragmentManager} saves across a rotation.
     *
     * @param itemId the item's {@code pantry_items.id}
     * @param name   the item's name, for the title
     * @return the dialog, ready to show
     */
    @NonNull
    public static DeleteIngredientDialog forItem(long itemId, @NonNull String name) {
        Bundle args = new Bundle();
        args.putLong(KEY_ITEM_ID, itemId);
        args.putString(KEY_ITEM_NAME, name);
        DeleteIngredientDialog dialog = new DeleteIngredientDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        long itemId = args.getLong(KEY_ITEM_ID);
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.delete_confirm_title, args.getString(KEY_ITEM_NAME)))
                .setMessage(R.string.delete_confirm_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    Bundle result = new Bundle();
                    result.putLong(KEY_ITEM_ID, itemId);
                    getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
                })
                .create();
    }
}
