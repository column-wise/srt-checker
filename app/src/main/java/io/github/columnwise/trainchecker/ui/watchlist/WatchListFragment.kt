package io.github.columnwise.trainchecker.ui.watchlist

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.columnwise.trainchecker.databinding.FragmentWatchListBinding
import io.github.columnwise.trainchecker.service.TicketWatcherService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WatchListFragment : Fragment() {
    private var _b: FragmentWatchListBinding? = null
    private val b get() = _b!!
    private val vm: WatchListViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentWatchListBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        val adapter = WatchJobAdapter(
            onCancel = { job ->
                vm.cancel(job) { jobId ->
                    requireContext().startService(Intent(requireContext(), TicketWatcherService::class.java).apply {
                        action = TicketWatcherService.ACTION_STOP
                        putExtra(TicketWatcherService.EXTRA_JOB_ID, jobId)
                    })
                }
            },
            onDelete = { job -> vm.delete(job) }
        )
        b.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.jobs.collect { adapter.submitList(it) }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
