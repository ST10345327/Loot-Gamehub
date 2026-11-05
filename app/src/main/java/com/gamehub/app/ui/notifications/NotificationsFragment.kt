package com.gamehub.app.ui.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.gamehub.app.AppNavigator
import com.gamehub.app.LootApplication
import com.gamehub.app.databinding.FragmentNotificationsBinding
import com.gamehub.app.ui.common.messageRes
import com.gamehub.app.ui.navigation.Screen
import com.gamehub.app.utils.AppLogger
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: NotificationsViewModel
    private lateinit var adapter: NotificationsAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            AppLogger.debug("Notification permission granted")
        } else {
            AppLogger.warn("Notification permission denied")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkNotificationPermission()

        val appContainer = (requireActivity().application as LootApplication).container
        viewModel = NotificationsViewModel(appContainer.notificationRepository)

        adapter = NotificationsAdapter { notification ->
            viewModel.markAsRead(notification.id)
            if (notification.gameId != null) {
                (requireActivity() as AppNavigator).navigateTo(Screen.GameDetails.createRoute(notification.gameId))
            }
        }

        binding.notificationsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.notificationsRecycler.adapter = adapter

        binding.notificationsMarkAllRead.setOnClickListener {
            viewModel.markAllAsRead()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun render(state: NotificationsUiState) {
        binding.notificationsProgress.isVisible = state.isLoading
        binding.notificationsError.isVisible = state.error != null && state.notifications.isEmpty()
        if (state.error != null) {
            binding.notificationsError.text = getString(state.error.messageRes())
        }
        binding.notificationsEmpty.isVisible = !state.isLoading && state.error == null && state.notifications.isEmpty()
        binding.notificationsRecycler.isVisible = state.notifications.isNotEmpty()

        adapter.submitList(state.notifications)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
