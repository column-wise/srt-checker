package io.github.columnwise.trainchecker.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.github.columnwise.trainchecker.R
import io.github.columnwise.trainchecker.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHomeBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        if (childFragmentManager.findFragmentById(R.id.homeContainer) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.homeContainer, SrtFormFragment())
                .commit()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
