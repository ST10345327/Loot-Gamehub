package com.gamehub.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gamehub.app.AppNavigator
import com.gamehub.app.LootApplication
import com.gamehub.app.R
import com.gamehub.app.databinding.FragmentRegisterBinding
import com.gamehub.app.ui.common.messageRes
import com.gamehub.app.ui.lootViewModelFactory
import com.gamehub.app.ui.navigation.Screen
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: RegisterViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        val app = requireActivity().application as LootApplication
        viewModel = ViewModelProvider(this, lootViewModelFactory(app.container))[RegisterViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.registerUsername.doAfterTextChanged { viewModel.onUsernameChange(it?.toString().orEmpty()) }
        binding.registerEmail.doAfterTextChanged { viewModel.onEmailChange(it?.toString().orEmpty()) }
        binding.registerPassword.doAfterTextChanged { viewModel.onPasswordChange(it?.toString().orEmpty()) }
        binding.registerConfirm.doAfterTextChanged { viewModel.onConfirmPasswordChange(it?.toString().orEmpty()) }
        binding.registerSubmit.setOnClickListener { viewModel.register() }
        binding.registerLogin.setOnClickListener { parentFragmentManager.popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                    if (state.isRegistered) {
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

    private fun render(state: RegisterUiState) {
        if (binding.registerUsername.text?.toString() != state.username) binding.registerUsername.setText(state.username)
        if (binding.registerEmail.text?.toString() != state.email) binding.registerEmail.setText(state.email)
        if (binding.registerPassword.text?.toString() != state.password) binding.registerPassword.setText(state.password)
        if (binding.registerConfirm.text?.toString() != state.confirmPassword) binding.registerConfirm.setText(state.confirmPassword)

        binding.registerUsernameLayout.error = state.usernameError?.let { getString(it.messageRes()) }
        binding.registerEmailLayout.error = state.emailError?.let { getString(it.messageRes()) }
        binding.registerPasswordLayout.error = state.passwordError?.let { getString(it.messageRes()) }
        binding.registerConfirmLayout.error = state.confirmPasswordError?.let { getString(it.messageRes()) }
        binding.registerPasswordHint.isVisible = state.passwordError == null
        binding.registerError.isVisible = state.error != null
        binding.registerError.text = state.error?.let { getString(it.messageRes()) }.orEmpty()
        binding.registerProgress.isVisible = state.isLoading
        binding.registerSubmit.isEnabled = !state.isLoading
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
