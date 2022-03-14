package com.github.cleveard.bibliotech.ui.settings

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.NumberPicker
import android.widget.TextView
import androidx.preference.DialogPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.preference.PreferenceFragmentCompat
import com.github.cleveard.bibliotech.R
import com.github.cleveard.bibliotech.db.BookDatabase
import com.github.cleveard.bibliotech.db.BookRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.*

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        findPreference<NumberPickerPreference>(BookDatabase.UNDO_LEVEL_KEY)?.let { undoLevels ->
            undoLevels.maxValue = BookDatabase.UNDO_LEVEL_MIN
            undoLevels.maxValue = BookDatabase.UNDO_LEVEL_MAX
            undoLevels.initialValue = BookDatabase.db.getUndoRedoDao().maxUndoLevels
            undoLevels.summaryString = requireContext().resources.getString(R.string.undo_level_summary)
        }
        findPreference<ButtonPreference>(BookDatabase.UNDO_CLEAR_KEY)?.let { undoClear ->
            undoClear.askOk = requireContext().resources.getString(R.string.can_clear_undo)
            undoClear.summaryString = requireContext().resources.getString(R.string.undo_clear_summary)
            undoClear.onClickOK = {
                MainScope().launch {
                    BookRepository.repo.clearUndo()
                }
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference.key) {
            BookDatabase.UNDO_LEVEL_KEY -> {
                if (parentFragmentManager.findFragmentByTag(BookDatabase.UNDO_LEVEL_KEY) == null) {
                    preference as NumberPickerPreference
                    val dialog = NumberPickerPreferenceDialog.newInstance(
                        preference.key,
                        preference.initialValue, preference.minValue, preference.maxValue
                    )
                    // It appears that PreferenceFragmentCompat requires target fragment to be set
                    @Suppress("DEPRECATION")
                    dialog.setTargetFragment(this, 0)
                    dialog.show(parentFragmentManager, BookDatabase.UNDO_LEVEL_KEY)
                }
            }
            BookDatabase.UNDO_CLEAR_KEY -> {
                if (parentFragmentManager.findFragmentByTag(BookDatabase.UNDO_CLEAR_KEY) == null) {
                    preference as ButtonPreference
                    val dialog = ButtonPreferenceDialog.newInstance(
                        preference.key,
                        preference.askOk
                    )
                    // It appears that PreferenceFragmentCompat requires target fragment to be set
                    @Suppress("DEPRECATION")
                    dialog.setTargetFragment(this, 0)
                    dialog.show(parentFragmentManager, BookDatabase.UNDO_CLEAR_KEY)
                }
            }
            else -> super.onDisplayPreferenceDialog(preference)
        }
    }
}

class NumberPickerPreference(context: Context, attrs: AttributeSet?) :
    DialogPreference(context, attrs) {
    
    var initialValue: Int = 0
    var minValue: Int = 0
    var maxValue: Int = 0
    var summaryString: String = "%1\$d"

    override fun getSummary(): CharSequence {
        return Formatter().format(summaryString, initialValue, minValue, maxValue).toString()
    }

    fun doPersistInt(value: Int) {
        initialValue = value
        super.persistInt(value)
        notifyChanged()
    }
}

class NumberPickerPreferenceDialog : PreferenceDialogFragmentCompat() {
    private lateinit var numberPicker: NumberPicker

    override fun onCreateDialogView(context: Context): View {
        return NumberPicker(context).also { numberPicker = it }
    }

    override fun onBindDialogView(view: View) {
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

class ButtonPreference(context: Context, attrs: AttributeSet?) :
    DialogPreference(context, attrs) {

    var askOk: String = ""
    var summaryString: String = ""
    var onClickOK: () -> Unit = {}

    override fun getSummary(): CharSequence = summaryString
}

class ButtonPreferenceDialog : PreferenceDialogFragmentCompat() {
    private lateinit var textView: TextView

    override fun onCreateDialogView(context: Context): View {
        return TextView(context).also { textView = it }
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        textView.gravity = Gravity.CENTER
        textView.text = requireArguments().getString(ASK_IF_OK)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            textView.clearFocus()
            (preference as ButtonPreference).onClickOK()
        }
    }

    companion object {
        private const val ASK_IF_OK = "ASK_IF_OK"

        fun newInstance(key: String, askOk: String): ButtonPreferenceDialog {
            val fragment = ButtonPreferenceDialog()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            bundle.putString(ASK_IF_OK, askOk)
            fragment.arguments = bundle

            return fragment
        }
    }
}

