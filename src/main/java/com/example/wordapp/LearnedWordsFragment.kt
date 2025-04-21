package com.example.wordapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import org.json.JSONArray

class LearnedWordsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_learned_words, container, false)
        val listView: ListView = view.findViewById(R.id.learnedWordsListView)
        updateWordList(listView)
        return view
    }

    override fun onResume() {
        super.onResume()
        val listView = view?.findViewById<ListView>(R.id.learnedWordsListView)
        listView?.let {
            updateWordList(it)
        }
    }

    private fun updateWordList(listView: ListView) {
        val sharedPref = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val learnedWordsStr = sharedPref.getString("learned_words", "[]")
        Log.d("STORAGE", "Retrieved learned_words: $learnedWordsStr")
        val learnedWordsArray = JSONArray(learnedWordsStr)
        val wordList = mutableListOf<String>()
        for (i in 0 until learnedWordsArray.length()) {
            val obj = learnedWordsArray.getJSONObject(i)
            val word = obj.getString("word")
            val translation = obj.getString("translation")
            wordList.add("$word - $translation")
            Log.d("STORAGE", "Displaying word: $word, translation: $translation")
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, wordList)
        listView.adapter = adapter
        if (wordList.isEmpty()) {
            Log.d("STORAGE", "Word list is empty")
        }
    }
}