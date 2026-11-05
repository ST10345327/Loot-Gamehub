package com.gamehub.app.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gamehub.app.AppNavigator
import com.gamehub.app.MainActivity
import com.gamehub.app.R
import com.gamehub.app.databinding.FragmentOnboardingBinding
import com.gamehub.app.ui.navigation.Screen

class OnboardingFragment : Fragment() {
    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var page = 0

    private val pages by lazy {
        listOf(
            R.string.onboarding_title_1 to R.string.onboarding_body_1,
            R.string.onboarding_title_2 to R.string.onboarding_body_2,
            R.string.onboarding_title_3 to R.string.onboarding_body_3
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        page = savedInstanceState?.getInt(STATE_PAGE)?.coerceIn(0, pages.lastIndex) ?: 0
        binding.onboardingSkip.setOnClickListener { finishOnboarding() }
        binding.onboardingNext.setOnClickListener {
            if (page == pages.lastIndex) finishOnboarding() else {
                page++
                renderPage()
            }
        }
        renderPage()
    }

    private fun renderPage() {
        val (title, body) = pages[page]
        binding.onboardingTitle.setText(title)
        binding.onboardingBody.setText(body)
        binding.onboardingIndicator.text = (pages.indices).joinToString("  ") {
            if (it == page) "●" else "○"
        }
        binding.onboardingIndicator.contentDescription =
            getString(R.string.onboarding_page_description, page + 1, pages.size)
        binding.onboardingNext.setText(
            if (page == pages.lastIndex) R.string.onboarding_get_started else R.string.onboarding_next
        )
    }

    private fun finishOnboarding() {
        (requireActivity() as MainActivity).sessionViewModel().markOnboardingSeen()
        (requireActivity() as AppNavigator).navigateTo(
            Screen.Login.route,
            clearBackStack = true,
            addToBackStack = false
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_PAGE, page)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val STATE_PAGE = "onboarding_page"
    }
}
