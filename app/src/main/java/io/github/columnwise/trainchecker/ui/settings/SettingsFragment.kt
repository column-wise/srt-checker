package io.github.columnwise.trainchecker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import io.github.columnwise.trainchecker.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val vm: SettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.etSrtId.setText(vm.srtId.value)
        binding.etSrtPw.setText(vm.srtPw.value)
        binding.etKtxId.setText(vm.ktxId.value)
        binding.etKtxPw.setText(vm.ktxPw.value)
        binding.etInterval.setText(vm.pollInterval.value.toString())

        binding.btnSave.setOnClickListener {
            val interval = binding.etInterval.text.toString().toIntOrNull() ?: 15
            vm.save(
                srtId = binding.etSrtId.text.toString(),
                srtPw = binding.etSrtPw.text.toString(),
                ktxId = binding.etKtxId.text.toString(),
                ktxPw = binding.etKtxPw.text.toString(),
                interval = interval,
            )
            Snackbar.make(view, "저장됨", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
