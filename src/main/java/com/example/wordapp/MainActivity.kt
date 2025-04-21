package com.example.wordapp

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        startButton = findViewById(R.id.startButton)
        startButton.setOnClickListener {
            setContentView(R.layout.activity_main)
            initializeViewPager()
        }
    }

    private fun initializeViewPager() {
        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = ViewPagerAdapter(this)
        viewPager.setPageTransformer(DepthPageTransformer())
    }

    fun setViewPagerEnabled(enabled: Boolean) {
        viewPager.isUserInputEnabled = enabled
    }
}

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MainFragment()
            1 -> LearnedWordsFragment()
            else -> MainFragment()
        }
    }
}

class DepthPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: android.view.View, position: Float) {
        val scale = if (position < 0) 1f + position * 0.1f else 1f - position * 0.1f
        val translationX = if (position > 0) page.width * -position * 0.1f else page.width * position * 0.1f
        page.scaleX = scale
        page.scaleY = scale
        page.translationX = translationX
        page.alpha = 1f - kotlin.math.abs(position) * 0.5f
    }
}