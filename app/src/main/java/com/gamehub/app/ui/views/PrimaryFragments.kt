package com.gamehub.app.ui.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.gamehub.app.AppNavigator
import com.gamehub.app.BuildConfig
import com.gamehub.app.LootApplication
import com.gamehub.app.R
import com.gamehub.app.data.model.AppLanguage
import com.gamehub.app.data.model.LibraryStatus
import com.gamehub.app.data.model.ThemeMode
import com.gamehub.app.databinding.FragmentDiscoverBinding
import com.gamehub.app.databinding.FragmentLibraryBinding
import com.gamehub.app.databinding.FragmentProfileBinding
import com.gamehub.app.databinding.FragmentSettingsBinding
import com.gamehub.app.databinding.FragmentSplashBinding
import com.gamehub.app.databinding.FragmentWishlistBinding
import com.gamehub.app.ui.common.messageRes
import com.gamehub.app.ui.home.HomeUiState
import com.gamehub.app.ui.home.HomeViewModel
import com.gamehub.app.ui.library.LibraryUiState
import com.gamehub.app.ui.library.LibraryViewModel
import com.gamehub.app.ui.lootViewModelFactory
import com.gamehub.app.ui.navigation.Screen
import com.gamehub.app.ui.session.SessionViewModel
import com.gamehub.app.ui.settings.SettingsViewModel
import com.gamehub.app.ui.splash.SplashViewModel
import com.gamehub.app.ui.wishlist.WishlistTab
import com.gamehub.app.ui.wishlist.WishlistUiState
import com.gamehub.app.ui.wishlist.WishlistViewModel
import com.gamehub.app.utils.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashFragment : Fragment() {
    private var _binding: FragmentSplashBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: SplashViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        val app = requireActivity().application as LootApplication
        viewModel = ViewModelProvider(this, lootViewModelFactory(app.container))[SplashViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AppLogger.debug("Splash shown")
        viewLifecycleOwner.lifecycleScope.launch {
            val route = viewModel.resolveStartRoute()
            delay(1200L)
            (requireActivity() as AppNavigator).navigateTo(route, clearBackStack = true, addToBackStack = false)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

class DiscoverFragment : Fragment() {
    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: HomeViewModel
    private lateinit var sessionViewModel: SessionViewModel
    private lateinit var trendingAdapter: GameAdapter
    private lateinit var popularAdapter: GameAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        val app = requireActivity().application as LootApplication
        val factory = lootViewModelFactory(app.container)
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]
        sessionViewModel = ViewModelProvider(requireActivity(), factory)[SessionViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        trendingAdapter = GameAdapter(::openDetails)
        popularAdapter = GameAdapter(::openDetails)
        binding.discoverTrending.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.discoverPopular.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.discoverTrending.adapter = trendingAdapter
        binding.discoverPopular.adapter = popularAdapter
        binding.discoverSearch.setOnClickListener {
            (requireActivity() as AppNavigator).navigateTo(Screen.Search.route, clearBackStack = true, addToBackStack = false)
        }
        binding.discoverSurprise.setOnClickListener { (requireActivity() as AppNavigator).navigateTo(Screen.SurpriseMe.route) }
        binding.discoverRetry.setOnClickListener { viewModel.retry() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    sessionViewModel.currentUser.collect { user ->
                        binding.discoverUsername.text = user?.username.orEmpty()
                    }
                }
            }
        }
    }

    private fun render(state: HomeUiState) {
        trendingAdapter.submitList(state.trending)
        popularAdapter.submitList(state.popular)
        binding.discoverProgress.isVisible = state.isLoading
        binding.discoverError.isVisible = state.error != null
        binding.discoverError.text = state.error?.let { getString(it.messageRes()) }.orEmpty()
        binding.discoverRetry.isVisible = state.error != null
    }

    private fun openDetails(id: Int) =
        (requireActivity() as AppNavigator).navigateTo(Screen.GameDetails.createRoute(id))

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

class LibraryFragment : Fragment() {
    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: LibraryViewModel
    private lateinit var adapter: SavedGameAdapter
    private val filterIds = mutableMapOf<Int, LibraryStatus?>()
    private var renderingFilter = false
    private var hasResumedOnce = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        val app = requireActivity().application as LootApplication
        viewModel = ViewModelProvider(this, lootViewModelFactory(app.container))[LibraryViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SavedGameAdapter(::openDetails, viewModel::removeGame)
        binding.libraryGames.layoutManager = LinearLayoutManager(requireContext())
        binding.libraryGames.adapter = adapter
        addFilterButtons()
        binding.libraryRetry.setOnClickListener { viewModel.retry() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun addFilterButtons() {
        val allId = View.generateViewId()
        filterIds[allId] = null
        binding.libraryFilter.addView(filterButton(allId, R.string.library_all))
        LibraryStatus.entries.forEach { status ->
            val id = View.generateViewId()
            filterIds[id] = status
            binding.libraryFilter.addView(filterButton(id, status.labelRes()))
        }
        binding.libraryFilter.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked && !renderingFilter) viewModel.selectFilter(filterIds[checkedId])
        }
    }

    private fun filterButton(id: Int, label: Int) = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        this.id = id
        setText(label)
        isAllCaps = false
    }

    private fun render(state: LibraryUiState) {
        renderingFilter = true
        val selected = filterIds.entries.firstOrNull { it.value == state.selectedFilter }?.key
        if (selected != null && binding.libraryFilter.checkedButtonId != selected) binding.libraryFilter.check(selected)
        renderingFilter = false

        binding.libraryCount.text = getString(R.string.library_count, state.allGames.size)
        binding.libraryProgress.isVisible = state.isLoading
        binding.libraryError.isVisible = state.error != null
        binding.libraryError.text = state.error?.let { getString(it.messageRes()) }.orEmpty()
        binding.libraryRetry.isVisible = state.error != null
        binding.libraryEmpty.isVisible = !state.isLoading && state.error == null && state.visibleGames.isEmpty()
        adapter.submitList(state.visibleGames)
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) viewModel.refresh() else hasResumedOnce = true
    }

    private fun openDetails(id: Int) =
        (requireActivity() as AppNavigator).navigateTo(Screen.GameDetails.createRoute(id))

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

class WishlistFragment : Fragment() {
    private var _binding: FragmentWishlistBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: WishlistViewModel
    private lateinit var adapter: SavedGameAdapter
    private var hasResumedOnce = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentWishlistBinding.inflate(inflater, container, false)
        val app = requireActivity().application as LootApplication
        viewModel = ViewModelProvider(this, lootViewModelFactory(app.container))[WishlistViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SavedGameAdapter(
            onClick = ::openDetails,
            onRemove = { id ->
                if (viewModel.uiState.value.tab == WishlistTab.WISHLIST) viewModel.removeWishlistGame(id)
                else viewModel.removeFavourite(id)
            },
            onMove = viewModel::moveToLibrary
        )
        binding.wishlistGames.layoutManager = LinearLayoutManager(requireContext())
        binding.wishlistGames.adapter = adapter
        binding.wishlistTabs.check(R.id.wishlist_tab_wishlist)
        binding.wishlistTabs.addOnButtonCheckedListener { _, id, checked ->
            if (checked) viewModel.selectTab(
                if (id == R.id.wishlist_tab_favourites) WishlistTab.FAVOURITES else WishlistTab.WISHLIST
            )
        }
        binding.wishlistRetry.setOnClickListener { viewModel.retry() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: WishlistUiState) {
        val tabId = if (state.tab == WishlistTab.FAVOURITES) R.id.wishlist_tab_favourites else R.id.wishlist_tab_wishlist
        if (binding.wishlistTabs.checkedButtonId != tabId) binding.wishlistTabs.check(tabId)
        binding.wishlistProgress.isVisible = state.isLoading
        binding.wishlistError.isVisible = state.error != null
        binding.wishlistError.text = state.error?.let { getString(it.messageRes()) }.orEmpty()
        binding.wishlistRetry.isVisible = state.error != null
        binding.wishlistEmpty.isVisible = !state.isLoading && state.error == null && state.visibleGames.isEmpty()
        binding.wishlistEmpty.setText(
            if (state.tab == WishlistTab.WISHLIST) R.string.wishlist_empty else R.string.favourites_empty
        )
        adapter.moveActionVisible = state.tab == WishlistTab.WISHLIST
        adapter.submitList(state.visibleGames)
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) viewModel.refresh() else hasResumedOnce = true
    }

    private fun openDetails(id: Int) =
        (requireActivity() as AppNavigator).navigateTo(Screen.GameDetails.createRoute(id))

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var sessionViewModel: SessionViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        val app = requireActivity().application as LootApplication
        sessionViewModel = ViewModelProvider(requireActivity(), lootViewModelFactory(app.container))[SessionViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.profileNotifications.setOnClickListener { (requireActivity() as AppNavigator).navigateTo(Screen.Notifications.route) }
        binding.profileSettings.setOnClickListener { (requireActivity() as AppNavigator).navigateTo(Screen.Settings.route) }
        binding.profileSignOut.setOnClickListener { sessionViewModel.signOut() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionViewModel.currentUser.collect { user ->
                    binding.profileUsername.text = user?.username.orEmpty()
                    binding.profileEmail.text = user?.email.orEmpty()
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: SettingsViewModel
    private var renderingSettings = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val app = requireActivity().application as LootApplication
        viewModel = ViewModelProvider(requireActivity(), lootViewModelFactory(app.container))[SettingsViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.settingsVersion.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
        binding.settingsTheme.setOnCheckedChangeListener { _, checked ->
            if (!renderingSettings) when (checked) {
                R.id.theme_system -> viewModel.setThemeMode(ThemeMode.SYSTEM)
                R.id.theme_light -> viewModel.setThemeMode(ThemeMode.LIGHT)
                R.id.theme_dark -> viewModel.setThemeMode(ThemeMode.DARK)
            }
        }
        binding.settingsLanguage.setOnCheckedChangeListener { _, checked ->
            if (!renderingSettings) when (checked) {
                R.id.language_english -> viewModel.setLanguage(AppLanguage.ENGLISH)
                R.id.language_tn -> viewModel.setLanguage(AppLanguage.SETSWANA)
                R.id.language_zu -> viewModel.setLanguage(AppLanguage.ISIZULU)
            }
        }
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (!renderingSettings) viewModel.setNotificationsEnabled(isChecked)
        }
        binding.switchWishlistAlerts.setOnCheckedChangeListener { _, isChecked ->
            if (!renderingSettings) viewModel.setWishlistAlerts(isChecked)
        }
        binding.switchReleaseAlerts.setOnCheckedChangeListener { _, isChecked ->
            if (!renderingSettings) viewModel.setReleaseAlerts(isChecked)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { settings ->
                    renderingSettings = true
                    binding.settingsTheme.check(
                        when (settings.themeMode) {
                            ThemeMode.SYSTEM -> R.id.theme_system
                            ThemeMode.LIGHT -> R.id.theme_light
                            ThemeMode.DARK -> R.id.theme_dark
                        }
                    )
                    binding.settingsLanguage.check(
                        when (settings.language) {
                            AppLanguage.ENGLISH -> R.id.language_english
                            AppLanguage.SETSWANA -> R.id.language_tn
                            AppLanguage.ISIZULU -> R.id.language_zu
                        }
                    )
                    binding.switchNotifications.isChecked = settings.notificationsEnabled
                    binding.switchWishlistAlerts.isChecked = settings.wishlistAlerts
                    binding.switchReleaseAlerts.isChecked = settings.releaseAlerts
                    renderingSettings = false
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private fun LibraryStatus.labelRes(): Int = when (this) {
    LibraryStatus.WANT_TO_PLAY -> R.string.library_want_to_play
    LibraryStatus.PLAYING -> R.string.library_playing
    LibraryStatus.COMPLETED -> R.string.library_completed
}
