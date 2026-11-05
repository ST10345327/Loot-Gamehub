package com.gamehub.app.ui.views

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.gamehub.app.R
import com.gamehub.app.data.model.Game
import com.gamehub.app.data.model.LibraryStatus
import com.gamehub.app.data.model.SavedGame
import com.gamehub.app.databinding.ItemCompareRowBinding
import com.gamehub.app.databinding.ItemGameBinding
import com.gamehub.app.databinding.ItemImageBinding
import com.gamehub.app.databinding.ItemSavedGameBinding
import com.gamehub.app.databinding.ItemScreenshotPageBinding
import com.gamehub.app.domain.GameComparison
import com.gamehub.app.domain.ComparisonRow
import com.gamehub.app.domain.ComparisonResult
import com.gamehub.app.domain.Winner

class GameAdapter(private val onClick: (Int) -> Unit) : ListAdapter<Game, GameAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick)

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        private val binding: ItemGameBinding,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(game: Game) = with(binding) {
            gameTitle.text = game.name
            gameSubtitle.text = listOfNotNull(game.releaseYear?.toString(), game.genres.firstOrNull(), game.rating?.let { "%.1f".format(it) })
                .joinToString(" · ")
            gameCover.contentDescription = root.context.getString(R.string.game_cover_description, game.name)
            gameCover.load(game.coverUrl) {
                placeholder(R.drawable.loot_appicon)
                error(R.drawable.loot_appicon)
            }
            root.setOnClickListener { onClick(game.id) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Game>() {
            override fun areItemsTheSame(old: Game, new: Game) = old.id == new.id
            override fun areContentsTheSame(old: Game, new: Game) = old == new
        }
    }
}

class SavedGameAdapter(
    private val onClick: (Int) -> Unit,
    private val onRemove: (Int) -> Unit,
    private val onMove: ((Int) -> Unit)? = null
) : ListAdapter<SavedGame, SavedGameAdapter.Holder>(DIFF) {
    var moveActionVisible: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemSavedGameBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick, onRemove, onMove)

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), moveActionVisible)

    class Holder(
        private val binding: ItemSavedGameBinding,
        private val onClick: (Int) -> Unit,
        private val onRemove: (Int) -> Unit,
        private val onMove: ((Int) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(game: SavedGame, moveActionVisible: Boolean) = with(binding) {
            savedName.text = game.name
            savedMetadata.text = listOfNotNull(game.releaseYear?.toString(), game.rating?.let { "%.1f".format(it) }, game.genre, game.platforms)
                .joinToString(" · ")
            savedStatus.text = game.status?.let { root.context.getString(it.labelRes()) }.orEmpty()
            savedCover.contentDescription = root.context.getString(R.string.game_cover_description, game.name)
            savedCover.load(game.coverUrl) {
                placeholder(R.drawable.loot_appicon)
                error(R.drawable.loot_appicon)
            }
            savedMove.visibility = if (onMove == null || !moveActionVisible) android.view.View.GONE else android.view.View.VISIBLE
            root.setOnClickListener { onClick(game.gameId) }
            savedRemove.setOnClickListener { onRemove(game.gameId) }
            savedMove.setOnClickListener { onMove?.invoke(game.gameId) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SavedGame>() {
            override fun areItemsTheSame(old: SavedGame, new: SavedGame) = old.gameId == new.gameId
            override fun areContentsTheSame(old: SavedGame, new: SavedGame) = old == new
        }
    }
}

class ImageAdapter(private val onImageClick: (Int) -> Unit) : RecyclerView.Adapter<ImageAdapter.Holder>() {
    private var images: List<String> = emptyList()

    fun submitList(values: List<String>) {
        images = values
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false), onImageClick)

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(images[position], position)
    override fun getItemCount() = images.size

    class Holder(
        private val binding: ItemImageBinding,
        private val onImageClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(url: String, position: Int) {
            binding.itemImage.load(url) {
                placeholder(R.drawable.loot_appicon)
                error(R.drawable.loot_appicon)
            }
            binding.root.setOnClickListener { onImageClick(position) }
        }
    }
}

class ScreenshotPagerAdapter(
    private val images: List<String>
) : RecyclerView.Adapter<ScreenshotPagerAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemScreenshotPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(images[position])
    override fun getItemCount() = images.size

    class Holder(private val binding: ItemScreenshotPageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(url: String) {
            binding.screenshotPageImage.load(url) {
                placeholder(R.drawable.loot_appicon)
                error(R.drawable.loot_appicon)
            }
        }
    }
}

class CompareRowsAdapter : RecyclerView.Adapter<CompareRowsAdapter.Holder>() {
    private var first: com.gamehub.app.data.model.GameDetails? = null
    private var second: com.gamehub.app.data.model.GameDetails? = null

    fun submit(firstGame: com.gamehub.app.data.model.GameDetails?, secondGame: com.gamehub.app.data.model.GameDetails?) {
        first = firstGame
        second = secondGame
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemCompareRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val a = first ?: return
        val b = second ?: return
        holder.bind(GameComparison.compare(a, b)[position])
    }

    override fun getItemCount(): Int =
        if (first != null && second != null) GameComparison.compare(first!!, second!!).size else 0

    class Holder(private val binding: ItemCompareRowBinding) : RecyclerView.ViewHolder(binding.root) {
        private val defaultFirstColor = binding.compareFirstValue.textColors.defaultColor
        private val defaultSecondColor = binding.compareSecondValue.textColors.defaultColor

        fun bind(row: ComparisonResult) = with(binding) {
            compareFirstValue.text = row.first ?: "—"
            compareSecondValue.text = row.second ?: "—"
            compareLabel.setText(row.row.labelRes())
            compareFirstValue.setTextColor(defaultFirstColor)
            compareSecondValue.setTextColor(defaultSecondColor)
            if (row.winner == Winner.FIRST) compareFirstValue.setTextColor(root.context.getColor(R.color.gamehub_red))
            if (row.winner == Winner.SECOND) compareSecondValue.setTextColor(root.context.getColor(R.color.gamehub_red))
        }
    }
}

private fun LibraryStatus.labelRes(): Int = when (this) {
    LibraryStatus.WANT_TO_PLAY -> R.string.library_want_to_play
    LibraryStatus.PLAYING -> R.string.library_playing
    LibraryStatus.COMPLETED -> R.string.library_completed
}

private fun ComparisonRow.labelRes(): Int = when (this) {
    ComparisonRow.RATING -> R.string.compare_row_rating
    ComparisonRow.RELEASE_DATE -> R.string.compare_row_release_date
    ComparisonRow.GENRE -> R.string.compare_row_genre
    ComparisonRow.PLATFORMS -> R.string.compare_row_platforms
    ComparisonRow.DEVELOPER -> R.string.compare_row_developer
    ComparisonRow.PUBLISHER -> R.string.compare_row_publisher
}
