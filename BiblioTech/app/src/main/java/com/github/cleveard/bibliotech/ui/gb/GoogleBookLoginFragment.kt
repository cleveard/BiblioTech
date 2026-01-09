package com.github.cleveard.bibliotech.ui.gb

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.github.cleveard.bibliotech.BookCredentials
import com.github.cleveard.bibliotech.MobileNavigationDirections
import com.github.cleveard.bibliotech.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Fragment used to login to Google Books
 * Use the [GoogleBookLoginFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class GoogleBookLoginFragment : DialogFragment() {
    enum class LoginResult {
        /** The login succeeded */
        SUCCEEDED,
        /** The login failed */
        FAILED,
        /** The login was skipped by the used */
        SKIPPED
    }

    /** View model for the fragment, used to launch the login process */
    class LoginViewModel: ViewModel()
    private val viewModel: LoginViewModel by viewModels()
    /** Saved State Handle used to return the login result */
    private val stateHandle: SavedStateHandle by lazy {
        findNavController().previousBackStackEntry!!.savedStateHandle
    }

    /**
     * @inheritDoc
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Assume we succeed
        stateHandle[kLoginResult] = LoginResult.SUCCEEDED
        // Build the dialog
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.login)
            .setMessage(R.string.login_message)                 // Message
            .setPositiveButton(R.string.yes, null)       // Yes response is handled in setOnShowListener
            .setNegativeButton(R.string.no) { dialog, _ ->  // No response cancels the dialog
                dialog.cancel()
            }
            .setCancelable(true)                                // Dialog is cancelable
            .create().apply {                                   // Create the dialog
                // Use the on show listener, to capture the yes click
                setOnShowListener {
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        // Launch the login process
                        viewModel.viewModelScope.launch {
                            (requireActivity() as? BookCredentials)?.let {auth ->
                                // Login and set the result
                                stateHandle[kLoginResult] = if (auth.login())
                                    LoginResult.SUCCEEDED
                                else
                                    LoginResult.FAILED
                                // Dismiss the dialog when the login is finished
                                dismiss()
                            }?: cancel()
                        }
                    }
                }
            }
    }

    override fun onCancel(dialog: DialogInterface) {
        // Canceled means the user skipped the login
        stateHandle[kLoginResult] = LoginResult.SKIPPED
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         * @return A new instance of fragment GoogleBookLoginFragment.
         */
        @JvmStatic
        fun newInstance() = GoogleBookLoginFragment()

        const val kLoginResult = "LOGIN_RESULT"

        /**
         * Login into google books for the fragment
         */
        suspend fun login(frag: Fragment) {
            return suspendCoroutine {continuation ->
                // Make sure initialization is finished
                frag.requireView().post {
                    // Get the navigation controller
                    val nav = frag.findNavController()
                    // Make sure we can login
                    (frag.requireActivity() as? BookCredentials)?.let {
                        // If we are authorized, then return success
                        if (it.isAuthorized) {
                            continuation.resume(Unit)
                            return@let
                        }

                        // If we aren't authorized, then navigate to the login fragment
                        nav.currentBackStackEntry?.let { backStack ->
                            val stateHandle = backStack.savedStateHandle
                            stateHandle.getLiveData<LoginResult>(kLoginResult)
                                .observe(backStack) { result ->
                                    when (result) {
                                        // Succeed doesn't need to do anything, the login fragment will return to this one
                                        LoginResult.SUCCEEDED -> { continuation.resume(Unit) }

                                        else -> {
                                            // Pop off including the current destination
                                            if (!nav.popBackStack(backStack.destination.id, true))
                                                nav.navigate(MobileNavigationDirections.filterBooks())
                                            throw CancellationException("Google book login failed")
                                        }
                                    }
                                }
                        } ?: run {
                            nav.navigate(MobileNavigationDirections.filterBooks())
                            throw CancellationException("Google book login failed")
                        }
                        // Navigate to the login fragment
                        nav.navigate(R.id.googleBookLoginFragment)
                    } ?: run {
                        nav.popBackStack()
                        throw CancellationException("Google book login failed")
                    }
                }
            }
        }
    }
}
