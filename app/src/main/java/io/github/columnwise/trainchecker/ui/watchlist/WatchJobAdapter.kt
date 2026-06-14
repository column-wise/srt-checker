package io.github.columnwise.trainchecker.ui.watchlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.columnwise.trainchecker.data.model.WatchJob
import io.github.columnwise.trainchecker.data.model.WatchStatus
import io.github.columnwise.trainchecker.databinding.ItemWatchJobBinding

class WatchJobAdapter(
    private val onCancel: (WatchJob) -> Unit,
) : ListAdapter<WatchJob, WatchJobAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemWatchJobBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(job: WatchJob) {
            b.tvRoute.text = "${job.trainType} ${job.depStation} → ${job.arrStation}"
            b.tvDetail.text = "${job.date} ${job.timeFrom}~${job.timeTo.ifEmpty { "제한없음" }} ${job.seatType.name}"
            b.tvStatus.text = when (job.status) {
                WatchStatus.WATCHING -> "⏳ 감시 중"
                WatchStatus.SUCCESS -> "✅ 예약 완료 (${job.reservationNumber})"
                WatchStatus.FAILED -> "❌ 실패"
                WatchStatus.CANCELLED -> "🚫 취소됨"
            }
            b.root.setOnLongClickListener {
                if (job.status == WatchStatus.WATCHING) onCancel(job)
                true
            }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(
        ItemWatchJobBinding.inflate(LayoutInflater.from(p.context), p, false)
    )
    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WatchJob>() {
            override fun areItemsTheSame(a: WatchJob, b: WatchJob) = a.id == b.id
            override fun areContentsTheSame(a: WatchJob, b: WatchJob) = a == b
        }
    }
}
