package io.github.columnwise.trainchecker.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import io.github.columnwise.trainchecker.data.api.srt.SRT_STATION_CODE
import io.github.columnwise.trainchecker.data.model.SeatType
import io.github.columnwise.trainchecker.data.model.TrainType
import io.github.columnwise.trainchecker.databinding.FragmentSrtFormBinding
import io.github.columnwise.trainchecker.service.TicketWatcherService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SrtFormFragment : Fragment() {
    private var _b: FragmentSrtFormBinding? = null
    private val b get() = _b!!
    private val vm: HomeViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSrtFormBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        val stations = SRT_STATION_CODE.keys.toList()
        val stationAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, stations)
        b.actvDep.setAdapter(stationAdapter)
        b.actvArr.setAdapter(stationAdapter)

        val seatTypes = listOf("일반실" to SeatType.GENERAL, "특실" to SeatType.SPECIAL, "상관없음" to SeatType.ANY)
        b.actvSeatType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, seatTypes.map { it.first }))
        b.actvSeatType.setText("일반실", false)

        b.btnStart.setOnClickListener {
            val dep = b.actvDep.text.toString()
            val arr = b.actvArr.text.toString()
            val date = b.etDate.text.toString()
            val timeFrom = b.etTimeFrom.text.toString()
            if (dep.isEmpty() || arr.isEmpty() || date.length != 8 || timeFrom.length != 4) {
                Toast.makeText(requireContext(), "모든 필드를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val seatIdx = seatTypes.indexOfFirst { it.first == b.actvSeatType.text.toString() }
            val seatType = if (seatIdx >= 0) seatTypes[seatIdx].second else SeatType.GENERAL
            vm.startWatch(TrainType.SRT, dep, arr, date, timeFrom, b.etTimeTo.text.toString(), seatType) { jobId ->
                val svcIntent = Intent(requireContext(), TicketWatcherService::class.java).apply {
                    action = TicketWatcherService.ACTION_START
                    putExtra(TicketWatcherService.EXTRA_JOB_ID, jobId)
                }
                requireContext().startForegroundService(svcIntent)
                Toast.makeText(requireContext(), "감시 시작됨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
