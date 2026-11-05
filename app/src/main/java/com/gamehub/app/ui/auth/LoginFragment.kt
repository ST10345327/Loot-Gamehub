package com.gamehub.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.credentials.CustomCredential
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuthException
import com.gamehub.app.AppNavigator
import com.gamehub.app.LootApplication
import com.gamehub.app.R
import com.gamehub.app.databinding.DialogForgotPasswordBinding
import com.gamehub.app.databinding.FragmentLoginBinding
import com.gamehub.app.ui.common.messageRes
import com.gamehub.app.ui.lootViewModelFactory
import com.gamehub.app.ui.navigation.Screen
import com.gamehub.app.utils.AppLogger
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: LoginViewModel
    private var forgotDialog: AlertDialog? = null
    private var forgotBinding: DialogForgotPasswordBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        val app = requireActivity().application as LootApplication
        viewModel = ViewModelProvider(this, lootViewModelFactory(app.container))[LoginViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.loginEmail.doAfterTextChanged { viewModel.onEmailChange(it?.toString().orEmpty()) }
        binding.loginPassword.doAfterTextChanged { viewModel.onPasswordChange(it?.toString().orEmpty()) }
        binding.loginSubmit.setOnClickListener { viewModel.login() }
        binding.loginGoogleSubmit.setOnClickListener { launchGoogleSignIn() }
        binding.loginRegister.setOnClickListener { (requireActivity() as AppNavigator).navigateTo(Screen.Register.route) }
        binding.loginForgotPassword.setOnClickListener { viewModel.openForgotPassword() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                    if (state.isLoggedIn) {
                        (requireActivity() as AppNavigator).navigateTo(
                            Screen.Home.route,
                            clearBackStack = true,
                            addToBackStack = false
                        )
                    }
                }
            }
        }
    }

    private fun render(state: LoginUiState) {
        if (binding.loginEmail.text?.toString() != state.email) binding.loginEmail.setText(state.email)
        if (binding.loginPassword.text?.toString() != state.password) binding.loginPassword.setText(state.password)
        binding.loginEmailLayout.error = state.emailError?.let { getString(it.messageRes()) }
        binding.loginPasswordLayout.error = state.passwordError?.let { getString(it.messageRes()) }
        binding.loginError.isVisible = state.error != null
        binding.loginError.text = state.error?.let { getString(it.messageRes()) }.orEmpty()
        binding.loginProgress.isVisible = state.isLoading
        binding.loginSubmit.isEnabled = !state.isLoading
        binding.loginGoogleSubmit.isEnabled = !state.isLoading
        binding.loginForgotPassword.isEnabled = !state.isLoading

        val forgot = state.forgotPassword
        if (forgot == null) {
            forgotDialog?.dismiss()
            forgotDialog = null
            forgotBinding = null
        } else {
            if (forgotDialog == null) showForgotDialog(forgot)
            renderForgotDialog(forgot)
        }
    }

    private fun launchGoogleSignIn() {
        AppLogger.debug("Google Sign-In button clicked")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val webClientId = resources.getIdentifier(
                    "default_web_client_id",
                    "string",
                    requireContext().packageName
                ).takeIf { it != 0 }?.let(::getString)
                if (webClientId.isNullOrBlank()) {
                    AppLogger.error("Google Sign-In is missing the Firebase web OAuth client ID")
                    Toast.makeText(
                        requireContext(),
                        "Google sign-in is not configured. Enable Google in Firebase Authentication and update google-services.json.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()

                val request = androidx.credentials.GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val credentialManager = androidx.credentials.CredentialManager.create(requireContext())
                val result = credentialManager.getCredential(requireContext(), request)
                val credential = result.credential

                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val googleIdToken = googleIdTokenCredential.idToken

                    val firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
                    val firebaseCredential = com.google.firebase.auth.GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                    val firebaseUser = authResult.user
                    val firebaseIdToken = firebaseUser?.getIdToken(true)?.await()?.token

                    if (!firebaseIdToken.isNullOrBlank()) {
                        viewModel.signInWithGoogle(firebaseIdToken)
                    } else {
                        AppLogger.error("Failed to retrieve Firebase ID token after Google sign-in")
                        Toast.makeText(requireContext(), "Google sign-in failed: Missing Firebase token", Toast.LENGTH_LONG).show()
                    }
                } else {
                    throw IllegalStateException("Google account credential was not returned")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseAuthException) {
                AppLogger.error("Firebase rejected Google sign-in (code: ${e.errorCode})", e)
                Toast.makeText(
                    requireContext(),
                    "Google sign-in failed. Check that Google is enabled in Firebase Authentication and that this app's SHA-1/SHA-256 fingerprints are registered.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                AppLogger.error("Google sign-in credential retrieval or authentication failed", e)
                Toast.makeText(
                    requireContext(),
                    "Google sign-in error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showForgotDialog(state: ForgotPasswordState) {
        val content = DialogForgotPasswordBinding.inflate(layoutInflater)
        forgotBinding = content
        content.forgotEmail.doAfterTextChanged { viewModel.onForgotEmailChange(it?.toString().orEmpty()) }
        forgotDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.forgot_title)
            .setView(content.root)
            .setPositiveButton(R.string.forgot_send, null)
            .setNegativeButton(R.string.common_cancel) { _, _ -> viewModel.dismissForgotPassword() }
            .create()
            .also { dialog ->
                dialog.setOnDismissListener {
                    if (viewModel.uiState.value.forgotPassword != null) viewModel.dismissForgotPassword()
                    forgotDialog = null
                    forgotBinding = null
                }
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { viewModel.sendResetLink() }
            }
        renderForgotDialog(state)
    }

    private fun renderForgotDialog(state: ForgotPasswordState) {
        val content = forgotBinding ?: return
        if (content.forgotEmail.text?.toString() != state.email) content.forgotEmail.setText(state.email)
        content.forgotEmailLayout.error = state.emailError?.let { getString(it.messageRes()) }
        content.forgotError.isVisible = state.error != null
        content.forgotError.text = state.error?.let { getString(it.messageRes()) }.orEmpty()
        content.forgotProgress.isVisible = state.isSending
        content.forgotBody.setText(if (state.sent) R.string.forgot_done else R.string.forgot_body)
        content.forgotEmailLayout.isVisible = !state.sent
        forgotDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isVisible = !state.sent
            isEnabled = !state.isSending
        }
        forgotDialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.setText(
            if (state.sent) R.string.common_close else R.string.common_cancel
        )
    }

    override fun onDestroyView() {
        forgotDialog?.setOnDismissListener(null)
        forgotDialog?.dismiss()
        forgotDialog = null
        forgotBinding = null
        _binding = null
        super.onDestroyView()
    }
}
