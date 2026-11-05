package com.gamehub.app.ui.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gamehub.app.databinding.FragmentUnknownBinding

class UnknownDestinationFragment : Fragment() {
    private var _binding: FragmentUnknownBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentUnknownBinding.inflate(inflater, container, false)
        return requireNotNull(_binding).root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
