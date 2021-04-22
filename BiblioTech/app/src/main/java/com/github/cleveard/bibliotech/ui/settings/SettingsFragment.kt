package com.github.cleveard.bibliotech.ui.settings

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.NumberPicker
import androidx.preference.DialogPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.preference.PreferenceFragmentCompat
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookDatabase

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        findPreference<NumberPickerPreference>(BookDatabase.UNDO_LEVEL_KEY)?.let { undoLevels ->
            undoLevels.maxValue = BookDatabase.UNDO_LEVEL_MIN
            undoLevels.maxValue = BookDatabase.UNDO_LEVEL_MAX
            undoLevels.initialValue = BookDatabase.db.getUndoRedoDao().maxUndoLevels
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference?) {
        if (parentFragmentManager.findFragmentByTag(BookDatabase.UNDO_LEVEL_KEY) != null) {
            return
        }
        if (preference is NumberPickerPreference) {
            val dialog = NumberPickerPreferenceDialog.newInstance(preference.key,
                preference.initialValue, preference.minValue, preference.maxValue)
            // It appears that PreferenceFragmentCompat requires target fragment to be set
            @Suppress("DEPRECATION")
            dialog.setTargetFragment(this, 0)
            dialog.show(parentFragmentManager, BookDatabase.UNDO_LEVEL_KEY)
        } else
            super.onDisplayPreferenceDialog(preference)
    }
}

class NumberPickerPreference(context: Context?, attrs: AttributeSet?) :
    DialogPreference(context, attrs) {
    
    var initialValue: Int = 0
    var minValue: Int = 0
    var maxValue: Int = 0

    override fun getSummary(): CharSequence {
        return context.resources.getString(R.string.undo_level_summary, initialValue)
    }

    fun doPersistInt(value: Int) {
        initialValue = value
        super.persistInt(value)
        notifyChanged()
    }
}

class NumberPickerPreferenceDialog : PreferenceDialogFragmentCompat() {
    private lateinit var numberPicker: NumberPicker

    override fun onCreateDialogView(context: Context?): View {
        return NumberPicker(context).also { numberPicker = it  }
    }

    override fun onBindDialogView(view: View?) {
        super.onBindDialogView(view)
        numberPicker.minValue = requireArguments().getInt(NUM_MIN_VALUE)
        numberPicker.maxValue = requireArguments().getInt(NUM_MAX_VALUE)
        numberPicker.value = requireArguments().getInt(NUM_INIT_VALUE)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            numberPicker.clearFocus()
            val newValue: Int = numberPicker.value
            if (preference.callChangeListener(newValue)) {
                (preference as NumberPickerPreference).doPersistInt(newValue)
                preference.summary
            }
        }
    }

    companion object {
        private const val NUM_INIT_VALUE = "NUM_INIT_VALUE"
        private const val NUM_MIN_VALUE = "NUM_MIN_VALUE"
        private const val NUM_MAX_VALUE = "NUM_MAX_VALUE"

        fun newInstance(key: String, initialValue: Int, minValue: Int, maxValue: Int): NumberPickerPreferenceDialog {
            val fragment = NumberPickerPreferenceDialog()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            bundle.putInt(NUM_INIT_VALUE, initialValue)
            bundle.putInt(NUM_MIN_VALUE, minValue)
            bundle.putInt(NUM_MAX_VALUE, maxValue)
            fragment.arguments = bundle

            return fragment
        }
    }
}

