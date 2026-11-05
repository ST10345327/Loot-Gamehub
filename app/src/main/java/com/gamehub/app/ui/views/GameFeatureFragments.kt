package com.gamehub.app.ui.views

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gamehub.app.LootApplication
import com.gamehub.app.R
import com.gamehub.app.data.model.Game
import com.gamehub.app.data.model.GenreFilter
import com.gamehub.app.data.model.GameDetails
import com.gamehub.app.data.model.LibraryStatus
import com.gamehub.app.data.model.PlatformFilter
import com.gamehub.app.data.model.ReleasePeriod
import com.gamehub.app.data.model.SearchFilters
import com.gamehub.app.data.model.SortOption
import com.gamehub.app.databinding.DialogComparePickerBinding
import com.gamehub.app.databinding.DialogScreenshotCarouselBinding
import com.gamehub.app.databinding.DialogSearchFiltersBinding
import com.gamehub.app.databinding.FragmentCompareBinding
import com.gamehub.app.databinding.FragmentGameDetailsBinding
import com.gamehub.app.databinding.FragmentSearchBinding
import com.gamehub.app.databinding.FragmentSurpriseBinding
import com.gamehub.app.domain.GameComparison
import com.gamehub.app.domain.ComparisonRow
import com.gamehub.app.domain.Winner
import com.gamehub.app.ui.common.messageRes
import com.gamehub.app.ui.compare.CompareViewModel
import com.gamehub.app.ui.details.GameDetailsUiState
import com.gamehub.app.ui.details.GameDetailsViewModel
import com.gamehub.app.ui.lootViewModelFactory
import com.gamehub.app.ui.navigation.Screen
import com.gamehub.app.ui.search.SearchUiState
import com.gamehub.app.ui.search.SearchViewModel
import com.gamehub.app.ui.surprise.SurpriseMeUiState
import com.gamehub.app.ui.surprise.SurpriseMeViewModel
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: SearchViewModel
    private lateinit var adapter: GameAdapter
    private var filterDialog: AlertDialog? = null
    private var filterBinding: DialogSearchFiltersBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        val app = requireActivity().application as LootApplication
        viewModel = ViewModelProvider(this, lootViewModelFactory(app.container))[SearchViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = GameAdapter { id ->
            (requireActivity() as com.gamehub.app.AppNavigator).navigateTo(Screen.GameDetails.createRoute(id))
        }
        binding.searchResults.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.searchResults.adapter = adapter
        binding.searchInput.doAfterTextChanged { viewModel.onQueryChange(it?.toString().orEmpty()) }
        binding.searchFilters.setOnClickListener { viewModel.openFilterSheet() }
        binding.searchRetry.setOnClickListener { viewModel.retry() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: SearchUiState) {
        if (binding.searchInput.text?.toString() != state.query) binding.searchInput.setText(state.query)
        binding.searchFilters.text = if (state.filters.activeCount == 0) getString(R.string.search_filters)
        else getString(R.string.search_filters_count, state.filters.activeCount)
        binding.searchProgress.isVisible = state.isLoading
        binding.searchError.isVisible = state.error != null
        binding.searchError.text = state.error?.let { getString(it.messageRes()) }.orEmpty()
        binding.searchRetry.isVisible = state.error != null && state.results.isEmpty()
        binding.searchEmpty.isVisible = state.hasSearched && !state.isLoading && state.error == null && state.results.isEmpty()
        val browse = state.query.isBlank() && state.filters.activeCount == 0
        binding.searchBrowseTitle.isVisible = browse
        binding.searchGenres.isVisible = browse
        renderGenres()
        adapter.submitList(state.results)

        if (state.showFilterSheet && filterDialog == null) showFilterDialog(state.filters)
        if (!state.showFilterSheet && filterDialog != null) {
            filterDialog?.setOnDismissListener(null)
            filterDialog?.dismiss()
            filterDialog = null
            filterBinding = null
        }
    }

    private fun renderGenres() {
        binding.searchGenres.removeAllViews()
        GenreFilter.entries.forEach { genre ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = genre.displayName
                isClickable = true
                isCheckable = false
                setOnClickListener { viewModel.onGenreSelected(genre) }
            }
            binding.searchGenres.addView(chip)
        }
    }

    private fun showFilterDialog(filters: SearchFilters) {
        val content = DialogSearchFiltersBinding.inflate(layoutInflater)
        filterBinding = content
        content.filterGenre.attachSpinner(
            listOf(getString(R.string.search_any)) + GenreFilter.entries.map { it.displayName },
            if (filters.genre == null) 0 else GenreFilter.entries.indexOf(filters.genre) + 1
        )
        content.filterPlatform.attachSpinner(
            listOf(getString(R.string.search_any)) + PlatformFilter.entries.map { it.displayName },
            if (filters.platform == null) 0 else PlatformFilter.entries.indexOf(filters.platform) + 1
        )
        content.filterPeriod.attachSpinner(
            listOf(
                getString(R.string.period_any),
                getString(R.string.period_last_year),
                getString(R.string.period_last_five_years),
                getString(R.string.period_classic)
            ),
            ReleasePeriod.entries.indexOf(filters.period)
        )
        content.filterSort.attachSpinner(
            listOf(
                getString(R.string.sort_relevance),
                getString(R.string.sort_rating),
                getString(R.string.sort_release_date),
                getString(R.string.sort_title)
            ),
            SortOption.entries.indexOf(filters.sort)
        )
        content.filterRating.max = 10
        content.filterRating.progress = filters.minRating
        content.filterRatingLabel.text = getString(R.string.search_min_rating, filters.minRating)
        content.filterRating.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                content.filterRatingLabel.text = getString(R.string.search_min_rating, progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })

        filterDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.search_filters)
            .setView(content.root)
            .setNegativeButton(R.string.common_cancel, null)
            .setNeutralButton(R.string.search_clear_filters, null)
            .setPositiveButton(R.string.search_apply, null)
            .create()
            .also { dialog ->
                dialog.setOnDismissListener { viewModel.closeFilterSheet() }
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    viewModel.setFilters(SearchFilters())
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val genreIndex = content.filterGenre.selectedItemPosition - 1
                    val platformIndex = content.filterPlatform.selectedItemPosition - 1
                    val filtersNow = SearchFilters(
                        genre = GenreFilter.entries.getOrNull(genreIndex),
                        platform = PlatformFilter.entries.getOrNull(platformIndex),
                        minRating = content.filterRating.progress,
                        period = ReleasePeriod.entries[content.filterPeriod.selectedItemPosition],
                        sort = SortOption.entries[content.filterSort.selectedItemPosition]
                    )
                    viewModel.setFilters(filtersNow)
                }
                dialog.window?.setLayout(
                    (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
    }

    override fun onDestroyView() {
        filterDialog?.setOnDismissListener(null)
        filterDialog?.dismiss()
        filterDialog = null
        filterBinding = null
        _binding = null
        super.onDestroyView()
    }
}

class GameDetailsFragment : Fragment() {
    private var _binding: FragmentGameDetailsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: GameDetailsViewModel
    private lateinit var screenshotsAdapter: ImageAdapter
    private lateinit var similarAdapter: GameAdapter
    private var screenshots: List<String> = emptyList()
    private val statusButtons = mutableMapOf<Int, LibraryStatus>()
    private var renderingStatus = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentGameDetailsBinding.inflate(inflater, container, false)
        val app = requireActivity().application as LootApplication
        val id = requireArguments().getString(Screen.ARG_ROUTE)
            ?.substringAfter("game/")?.toIntOrNull() ?: error("Missing game id route")
        viewModel = ViewModelProvider(
            this,
            GameDetailsViewModel.factory(id, app.container.gameRepository, app.container.libraryRepository)
        )[GameDetailsViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        screenshotsAdapter = ImageAdapter { index ->
            showScreenshotCarousel(screenshots, index)
        }
        similarAdapter = GameAdapter { id ->
            (requireActivity() as com.gamehub.app.AppNavigator).navigateTo(Screen.GameDetails.createRoute(id))
        }
        binding.detailsScreenshots.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.detailsScreenshots.adapter = screenshotsAdapter
        binding.detailsSimilar.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.detailsSimilar.adapter = similarAdapter
        binding.detailsBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.detailsRetry.setOnClickListener { viewModel.retry() }
        binding.detailsFavourite.setOnClickListener { viewModel.toggleFavourite() }
        binding.detailsWishlist.setOnClickListener { viewModel.toggleWishlist() }
        binding.detailsCompare.setOnClickListener {
            val id = requireArguments().getString(Screen.ARG_ROUTE)
                ?.substringAfter("game/")?.toIntOrNull() ?: return@setOnClickListener
            (requireActivity() as com.gamehub.app.AppNavigator).navigateTo(Screen.Compare.createRoute(id))
        }
        addStatusButtons()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun addStatusButtons() {
        LibraryStatus.entries.forEach { status ->
            val id = View.generateViewId()
            statusButtons[id] = status
            val btn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                this.id = id
                setText(status.labelRes())
                isCheckable = true
                isAllCaps = false
                textSize = 12f
                maxLines = 2
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            binding.detailsLibraryStatus.addView(btn)
        }
        binding.detailsLibraryStatus.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked && !renderingStatus) statusButtons[checkedId]?.let(viewModel::setLibraryStatus)
        }
        binding.detailsRemoveLibrary.setOnClickListener { viewModel.setLibraryStatus(null) }
    }

    private fun render(state: GameDetailsUiState) {
        binding.detailsProgress.isVisible = state.isLoading
        binding.detailsScroll.isVisible = !state.isLoading && state.details != null
        binding.detailsError.isVisible = state.error != null || state.statusError != null
        binding.detailsError.text = (state.statusError ?: state.error)?.let { getString(it.messageRes()) }.orEmpty()
        binding.detailsRetry.isVisible = state.details == null || state.statusError != null
        val details = state.details
        if (details != null) {
            binding.detailsTitle.text = details.game.name
            binding.detailsCover.contentDescription = getString(R.string.game_cover_description, details.game.name)
            binding.detailsCover.load(details.game.coverUrl) {
                placeholder(R.drawable.loot_appicon)
                error(R.drawable.loot_appicon)
            }
            binding.detailsMetadata.text = listOfNotNull(
                details.game.releaseYear?.toString(),
                details.game.rating?.let { "%.1f".format(java.util.Locale.ROOT, it) },
                details.game.genres.joinToString(", ").ifBlank { null },
                details.game.platforms.joinToString(", ").ifBlank { null }
            ).joinToString("  •  ")
            binding.detailsDescription.text = details.description.orEmpty()
            binding.detailsDeveloper.text = details.developers.takeIf { it.isNotEmpty() }
                ?.let { getString(R.string.details_developer) + ": " + it.joinToString(", ") }.orEmpty()
            binding.detailsPublisher.text = details.publishers.takeIf { it.isNotEmpty() }
                ?.let { getString(R.string.details_publisher) + ": " + it.joinToString(", ") }.orEmpty()
            binding.detailsScreenshotsTitle.isVisible = details.screenshots.isNotEmpty()
            binding.detailsScreenshots.isVisible = details.screenshots.isNotEmpty()
            screenshots = details.screenshots
            binding.detailsSimilarTitle.isVisible = details.similarGames.isNotEmpty()
            binding.detailsSimilar.isVisible = details.similarGames.isNotEmpty()
            screenshotsAdapter.submitList(details.screenshots)
            similarAdapter.submitList(details.similarGames.map {
                Game(it.id, it.name, it.coverUrl, null, null, null, emptyList(), emptyList())
            })
        }
        binding.detailsActions.isVisible = details != null
        binding.detailsFavourite.setText(if (state.status.isFavourite) R.string.details_remove_favourite else R.string.details_add_favourite)
        binding.detailsWishlist.setText(if (state.status.isInWishlist) R.string.details_remove_wishlist else R.string.details_add_wishlist)
        binding.detailsFavourite.isEnabled = state.statusError == null
        binding.detailsWishlist.isEnabled = state.statusError == null
        binding.detailsCompare.isEnabled = true
        binding.detailsRemoveLibrary.isEnabled = state.statusError == null
        renderingStatus = true
        val selected = statusButtons.entries.firstOrNull { it.value == state.status.libraryStatus }?.key
        if (selected != null && binding.detailsLibraryStatus.checkedButtonId != selected) {
            binding.detailsLibraryStatus.check(selected)
        } else if (selected == null && binding.detailsLibraryStatus.checkedButtonId != View.NO_ID) {
            binding.detailsLibraryStatus.clearChecked()
        }
        renderingStatus = false
    }

    private fun showScreenshotCarousel(images: List<String>, initialPosition: Int) {
        if (images.isEmpty() || initialPosition !in images.indices) return

        val preview = DialogScreenshotCarouselBinding.inflate(layoutInflater)
        preview.screenshotPager.adapter = ScreenshotPagerAdapter(images)
        preview.screenshotPager.setCurrentItem(initialPosition, false)

        Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(preview.root)
            setCanceledOnTouchOutside(true)
            window?.setBackgroundDrawableResource(android.R.color.black)
            show()
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

class CompareFragment : Fragment() {
    private var _binding: FragmentCompareBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: CompareViewModel
    private lateinit var rowsAdapter: CompareRowsAdapter
    private var pickerDialog: AlertDialog? = null
    private var pickerBinding: DialogComparePickerBinding? = null
    private var pickerAdapter: GameAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentCompareBinding.inflate(inflater, container, false)
        val app = requireActivity().application as LootApplication
        val id = requireArguments().getString(Screen.ARG_ROUTE)
            ?.substringAfter("compare/")?.toIntOrNull() ?: error("Missing comparison game id")
        viewModel = ViewModelProvider(this, CompareViewModel.factory(id, app.container.gameRepository))[CompareViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rowsAdapter = CompareRowsAdapter()
        binding.compareRows.layoutManager = LinearLayoutManager(requireContext())
        binding.compareRows.adapter = rowsAdapter
        binding.compareBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.comparePick.setOnClickListener { viewModel.openPicker() }
        binding.compareClear.setOnClickListener { viewModel.clearSecondGame() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: com.gamehub.app.ui.compare.CompareUiState) {
        bindCompareGame(binding.compareFirstCover, binding.compareFirstName, state.firstGame)
        bindCompareGame(binding.compareSecondCover, binding.compareSecondName, state.secondGame)
        binding.compareFirstProgress.isVisible = state.isLoadingFirst
        binding.compareSecondProgress.isVisible = state.isLoadingSecond
        binding.comparePick.isVisible = state.secondGame == null && !state.isLoadingSecond
        binding.compareClear.isVisible = state.secondGame != null
        binding.compareError.isVisible = state.error != null
        binding.compareError.text = state.error?.let { getString(it.messageRes()) }.orEmpty()
        rowsAdapter.submit(state.firstGame, state.secondGame)

        if (state.isPickerOpen && pickerDialog == null) showPicker()
        if (!state.isPickerOpen && pickerDialog != null) {
            pickerDialog?.setOnDismissListener(null)
            pickerDialog?.dismiss()
            pickerDialog = null
            pickerBinding = null
            pickerAdapter = null
        }
        pickerBinding?.let { content ->
            content.comparePickerProgress.isVisible = state.isPickerLoading
            pickerAdapter?.submitList(state.pickerResults)
            if (content.comparePickerQuery.text?.toString() != state.pickerQuery) {
                content.comparePickerQuery.setText(state.pickerQuery)
            }
        }
    }

    private fun bindCompareGame(
        image: android.widget.ImageView,
        title: android.widget.TextView,
        details: GameDetails?
    ) {
        title.text = details?.game?.name.orEmpty()
        if (details != null) {
            image.load(details.game.coverUrl) {
                placeholder(R.drawable.loot_appicon)
                error(R.drawable.loot_appicon)
            }
        } else {
            image.setImageResource(R.drawable.loot_appicon)
        }
    }

    private fun showPicker() {
        val content = DialogComparePickerBinding.inflate(layoutInflater)
        pickerBinding = content
        pickerAdapter = GameAdapter { id ->
            val selected = viewModel.uiState.value.pickerResults.firstOrNull { it.id == id } ?: return@GameAdapter
            viewModel.selectSecondGame(selected)
        }
        content.comparePickerResults.layoutManager = LinearLayoutManager(requireContext())
        content.comparePickerResults.adapter = pickerAdapter
        content.comparePickerQuery.doAfterTextChanged { viewModel.onPickerQueryChange(it?.toString().orEmpty()) }
        pickerDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.compare_pick_game)
            .setView(content.root)
            .setNegativeButton(R.string.common_close, null)
            .create()
            .also { dialog ->
                dialog.setOnDismissListener { viewModel.closePicker() }
                dialog.show()
                dialog.window?.setLayout(
                    (resources.displayMetrics.widthPixels * 0.94f).toInt(),
                    (resources.displayMetrics.heightPixels * 0.78f).toInt()
                )
            }
    }

    override fun onDestroyView() {
        pickerDialog?.setOnDismissListener(null)
        pickerDialog?.dismiss()
        pickerDialog = null
        pickerBinding = null
        pickerAdapter = null
        _binding = null
        super.onDestroyView()
    }
}

class SurpriseFragment : Fragment() {
    private var _binding: FragmentSurpriseBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: SurpriseMeViewModel
    private var renderingFilters = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSurpriseBinding.inflate(inflater, container, false)
        val app = requireActivity().application as LootApplication
        viewModel = ViewModelProvider(this, lootViewModelFactory(app.container))[SurpriseMeViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.surpriseBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.surpriseAnother.setOnClickListener { viewModel.roll() }
        binding.surpriseDetails.setOnClickListener {
            viewModel.uiState.value.game?.let {
                (requireActivity() as com.gamehub.app.AppNavigator).navigateTo(Screen.GameDetails.createRoute(it.id))
            }
        }
        binding.surpriseGenre.attachSpinner(listOf(getString(R.string.search_any)) + GenreFilter.entries.map { it.displayName }, 0)
        binding.surprisePlatform.attachSpinner(listOf(getString(R.string.search_any)) + PlatformFilter.entries.map { it.displayName }, 0)
        binding.surpriseGenre.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!renderingFilters) viewModel.onGenreSelected(GenreFilter.entries.getOrNull(position - 1))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.surprisePlatform.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!renderingFilters) viewModel.onPlatformSelected(PlatformFilter.entries.getOrNull(position - 1))
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: SurpriseMeUiState) {
        renderingFilters = true
        binding.surpriseGenre.setSelection(state.genre?.let { GenreFilter.entries.indexOf(it) + 1 } ?: 0)
        binding.surprisePlatform.setSelection(state.platform?.let { PlatformFilter.entries.indexOf(it) + 1 } ?: 0)
        renderingFilters = false
        binding.surpriseProgress.isVisible = state.isLoading
        binding.surpriseContent.isVisible = !state.isLoading && state.game != null
        binding.surpriseError.isVisible = !state.isLoading && state.error != null
        binding.surpriseError.text = state.error?.let { getString(it.messageRes()) }.orEmpty()
        state.game?.let { game ->
            binding.surpriseCover.contentDescription = getString(R.string.game_cover_description, game.name)
            binding.surpriseCover.load(game.coverUrl) {
                placeholder(R.drawable.loot_appicon)
                error(R.drawable.loot_appicon)
            }
            binding.surpriseName.text = game.name
            binding.surpriseMetadata.text = listOfNotNull(game.releaseYear?.toString(), game.genres.firstOrNull()).joinToString(" · ")
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private fun android.widget.Spinner.attachSpinner(items: List<String>, selection: Int) {
    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, items).also {
        it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }
    setSelection(selection)
}

private fun ComparisonRow.labelRes(): Int = when (this) {
    ComparisonRow.RATING -> R.string.compare_row_rating
    ComparisonRow.RELEASE_DATE -> R.string.compare_row_release_date
    ComparisonRow.GENRE -> R.string.compare_row_genre
    ComparisonRow.PLATFORMS -> R.string.compare_row_platforms
    ComparisonRow.DEVELOPER -> R.string.compare_row_developer
    ComparisonRow.PUBLISHER -> R.string.compare_row_publisher
}

private fun LibraryStatus.labelRes(): Int = when (this) {
    LibraryStatus.WANT_TO_PLAY -> R.string.library_want_to_play
    LibraryStatus.PLAYING -> R.string.library_playing
    LibraryStatus.COMPLETED -> R.string.library_completed
}
