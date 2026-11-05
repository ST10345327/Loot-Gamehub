package com.gamehub.app.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gamehub.app.data.model.GameNotification
import com.gamehub.app.databinding.ItemNotificationBinding

class NotificationsAdapter(
    private val onClick: (GameNotification) -> Unit
) : ListAdapter<GameNotification, NotificationsAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick)

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(getItem(position))

    class Holder(
        private val binding: ItemNotificationBinding,
        private val onClick: (GameNotification) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GameNotification) = with(binding) {
            notificationTitle.text = item.title
            notificationBody.text = item.body
            notificationTime.text = item.createdAt
            notificationUnreadDot.isVisible = !item.isRead

            root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<GameNotification>() {
            override fun areItemsTheSame(oldItem: GameNotification, newItem: GameNotification) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: GameNotification, newItem: GameNotification) = oldItem == newItem
        }
    }
}
