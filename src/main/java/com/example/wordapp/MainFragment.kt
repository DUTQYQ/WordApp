package com.example.wordapp

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

class MainFragment : Fragment() {

    private lateinit var sentenceTextView: TextView
    private lateinit var word1TextView: TextView
    private lateinit var word2TextView: TextView
    private lateinit var word3TextView: TextView
    private lateinit var refreshButton: Button
    private lateinit var updateWordsButton: Button
    private val handler = Handler(Looper.getMainLooper())

    private var isSentenceTranslated = false
    private var originalSentence = ""
    private var translatedSentence = ""
    private val wordStates = mutableMapOf<TextView, Pair<String, String>>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_main, container, false)

        sentenceTextView = view.findViewById(R.id.sentenceTextView)
        word1TextView = view.findViewById(R.id.word1TextView)
        word2TextView = view.findViewById(R.id.word2TextView)
        word3TextView = view.findViewById(R.id.word3TextView)
        refreshButton = view.findViewById(R.id.refreshButton)
        updateWordsButton = view.findViewById(R.id.updateWordsButton)

        setupClickListeners()
        fetchDailyWords("", "", false, setButtonBack = false)

        return view
    }

    private fun setupClickListeners() {
        sentenceTextView.setOnClickListener {
            if (isSentenceTranslated) {
                sentenceTextView.text = originalSentence
                isSentenceTranslated = false
            } else {
                if (translatedSentence.isEmpty()) {
                    translateSentence(originalSentence)
                } else {
                    sentenceTextView.text = translatedSentence
                    isSentenceTranslated = true
                }
            }
        }

        word1TextView.setOnClickListener { toggleWordTranslation(word1TextView) }
        word2TextView.setOnClickListener { toggleWordTranslation(word2TextView) }
        word3TextView.setOnClickListener { toggleWordTranslation(word3TextView) }

        refreshButton.setOnClickListener {
            updateDailySentence()
        }

        updateWordsButton.setOnClickListener {
            updateWordsAndSentence()
        }
    }

    private fun toggleWordTranslation(textView: TextView) {
        val (word, translation) = wordStates[textView] ?: return
        textView.text = if (textView.text.toString() == word) translation else word
    }

    private fun fetchDailyWords(oldOriginalSentence: String, oldTranslatedSentence: String, wasTranslated: Boolean, setButtonBack: Boolean = false) {
        val url = "http://121.40.247.127/v1/words"
        val requestQueue = Volley.newRequestQueue(requireContext())

        (activity as? MainActivity)?.setViewPagerEnabled(false)

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                try {
                    originalSentence = response.getString("sentence")
                    translatedSentence = response.getString("sentence_translation") ?: ""
                    sentenceTextView.text = if (wasTranslated && translatedSentence.isNotEmpty()) translatedSentence else originalSentence
                    isSentenceTranslated = wasTranslated && translatedSentence.isNotEmpty()
                    Log.d("NETWORK", "Fetched sentence: $originalSentence, translation: $translatedSentence")

                    val wordsArray = response.getJSONArray("words")
                    wordStates.clear()
                    word1TextView.text = ""
                    word2TextView.text = ""
                    word3TextView.text = ""
                    if (wordsArray.length() > 0) {
                        for (i in 0 until min(wordsArray.length(), 3)) {
                            val word = wordsArray.getJSONObject(i)
                            val wordText = word.getString("word")
                            val translation = word.getString("translation")
                            when (i) {
                                0 -> {
                                    word1TextView.text = wordText
                                    wordStates[word1TextView] = Pair(wordText, translation)
                                }
                                1 -> {
                                    word2TextView.text = wordText
                                    wordStates[word2TextView] = Pair(wordText, translation)
                                }
                                2 -> {
                                    word3TextView.text = wordText
                                    wordStates[word3TextView] = Pair(wordText, translation)
                                }
                            }
                        }
                        storeLearnedWords(wordsArray)
                    } else {
                        Toast.makeText(requireContext(), "No words returned from server", Toast.LENGTH_SHORT).show()
                    }
                    if (setButtonBack) {
                        refreshButton.text = getString(R.string.refresh)
                        refreshButton.isEnabled = true
                        updateWordsButton.text = getString(R.string.update_words)
                        updateWordsButton.isEnabled = true
                    }
                } catch (e: Exception) {
                    Log.e("NETWORK", "Error processing response: ${e.message}", e)
                    Toast.makeText(requireContext(), "Failed to process data", Toast.LENGTH_SHORT).show()
                }
                (activity as? MainActivity)?.setViewPagerEnabled(true)
            },
            { error ->
                Log.e("NETWORK", "Volley error: ${error.message}", error)
                Log.e("NETWORK", "Error details: networkResponse=${error.networkResponse?.statusCode}, data=${error.networkResponse?.data?.toString(Charsets.UTF_8)}")
                Toast.makeText(requireContext(), "Failed to load data", Toast.LENGTH_SHORT).show()
                if (oldOriginalSentence.isNotEmpty()) {
                    sentenceTextView.text = if (wasTranslated) oldTranslatedSentence else oldOriginalSentence
                    isSentenceTranslated = wasTranslated
                    originalSentence = oldOriginalSentence
                    translatedSentence = oldTranslatedSentence
                } else {
                    sentenceTextView.text = "Failed to load data"
                }
                if (setButtonBack) {
                    refreshButton.text = getString(R.string.refresh)
                    refreshButton.isEnabled = true
                    updateWordsButton.text = getString(R.string.update_words)
                    updateWordsButton.isEnabled = true
                }
                (activity as? MainActivity)?.setViewPagerEnabled(true)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["User-Agent"] = "Mozilla/5.0"
                headers["Cache-Control"] = "no-cache"
                return headers
            }
        }.apply { setShouldCache(false) }

        jsonObjectRequest.retryPolicy = DefaultRetryPolicy(
            10000,
            1,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        requestQueue.add(jsonObjectRequest)
    }

    private fun storeLearnedWords(wordsArray: JSONArray) {
        val sharedPref = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val learnedWordsStr = sharedPref.getString("learned_words", "[]")
        val learnedWordsArray = try {
            JSONArray(learnedWordsStr)
        } catch (e: Exception) {
            Log.e("STORAGE", "Error parsing learned_words: ${e.message}", e)
            JSONArray()
        }

        Log.d("STORAGE", "Input wordsArray: $wordsArray")
        val newWordsAdded = mutableListOf<String>()
        for (i in 0 until wordsArray.length()) {
            try {
                val wordObj = wordsArray.getJSONObject(i)
                val word = wordObj.getString("word")
                val translation = wordObj.getString("translation")
                var exists = false
                for (j in 0 until learnedWordsArray.length()) {
                    val existingObj = learnedWordsArray.getJSONObject(j)
                    if (existingObj.getString("word").equals(word, ignoreCase = true)) {
                        exists = true
                        Log.d("STORAGE", "Word already exists: $word")
                        break
                    }
                }
                if (!exists && word.isNotBlank() && translation.isNotBlank()) {
                    val newObj = JSONObject().apply {
                        put("word", word)
                        put("translation", translation)
                    }
                    learnedWordsArray.put(newObj)
                    newWordsAdded.add("$word - $translation")
                    Log.d("STORAGE", "Stored word: $word, translation: $translation")
                }
            } catch (e: Exception) {
                Log.e("STORAGE", "Error processing word at index $i: ${e.message}", e)
            }
        }

        val finalJsonString = learnedWordsArray.toString()
        with(sharedPref.edit()) {
            putString("learned_words", finalJsonString)
            commit()
            Log.d("STORAGE", "Saved learned_words: $finalJsonString")
            if (newWordsAdded.isNotEmpty()) {
                Log.d("STORAGE", "New words added: $newWordsAdded")
            } else {
                Log.d("STORAGE", "No new words added")
            }
        }
    }

    private fun translateSentence(sentence: String) {
        val url = "http://121.40.247.127/v1/translate"
        val requestQueue = Volley.newRequestQueue(requireContext())
        val jsonObject = JSONObject().apply { put("sentence", sentence) }

        (activity as? MainActivity)?.setViewPagerEnabled(false)

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.POST, url, jsonObject,
            { response ->
                try {
                    translatedSentence = response.getString("translation")
                    if (translatedSentence != "Translation failed") {
                        sentenceTextView.text = translatedSentence
                        isSentenceTranslated = true
                    } else {
                        Toast.makeText(requireContext(), "Translation failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("NETWORK", "Error processing translation: ${e.message}", e)
                    Toast.makeText(requireContext(), "Failed to process translation", Toast.LENGTH_SHORT).show()
                }
                fetchDailyWords(originalSentence, translatedSentence, isSentenceTranslated, setButtonBack = false)
                (activity as? MainActivity)?.setViewPagerEnabled(true)
            },
            { error ->
                Log.e("NETWORK", "Translation error: ${error.message}", error)
                Log.e("NETWORK", "Error details: networkResponse=${error.networkResponse?.statusCode}, data=${error.networkResponse?.data?.toString(Charsets.UTF_8)}")
                Toast.makeText(requireContext(), "Failed to translate", Toast.LENGTH_SHORT).show()
                fetchDailyWords(originalSentence, translatedSentence, isSentenceTranslated, setButtonBack = false)
                (activity as? MainActivity)?.setViewPagerEnabled(true)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Cache-Control"] = "no-cache"
                return headers
            }
        }

        jsonObjectRequest.retryPolicy = DefaultRetryPolicy(
            10000,
            1,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        requestQueue.add(jsonObjectRequest)
    }

    private fun updateDailySentence() {
        val oldOriginalSentence = originalSentence
        val oldTranslatedSentence = translatedSentence
        val wasTranslated = isSentenceTranslated

        refreshButton.text = "Loading..."
        refreshButton.isEnabled = false
        (activity as? MainActivity)?.setViewPagerEnabled(false)

        val url = "http://121.40.247.127/v1/update_sentence_only"
        val requestQueue = Volley.newRequestQueue(requireContext())

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.POST, url, null,
            { response ->
                Log.d("NETWORK", "POST to update_sentence_only response: $response")
                fetchDailyWords(oldOriginalSentence, oldTranslatedSentence, wasTranslated, setButtonBack = true)
            },
            { error ->
                Log.e("NETWORK", "POST error: ${error.message}", error)
                Log.e("NETWORK", "Error details: networkResponse=${error.networkResponse?.statusCode}, data=${error.networkResponse?.data?.toString(Charsets.UTF_8)}")
                Toast.makeText(requireContext(), "Failed to update sentence", Toast.LENGTH_SHORT).show()
                sentenceTextView.text = if (wasTranslated) oldTranslatedSentence else oldOriginalSentence
                isSentenceTranslated = wasTranslated
                originalSentence = oldOriginalSentence
                translatedSentence = oldTranslatedSentence
                refreshButton.text = getString(R.string.refresh)
                refreshButton.isEnabled = true
                (activity as? MainActivity)?.setViewPagerEnabled(true)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Cache-Control"] = "no-cache"
                return headers
            }
        }

        jsonObjectRequest.retryPolicy = DefaultRetryPolicy(
            10000,
            1,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        requestQueue.add(jsonObjectRequest)
    }

    private fun updateWordsAndSentence() {
        val oldOriginalSentence = originalSentence
        val oldTranslatedSentence = translatedSentence
        val wasTranslated = isSentenceTranslated

        updateWordsButton.text = "Loading..."
        updateWordsButton.isEnabled = false
        refreshButton.isEnabled = false
        (activity as? MainActivity)?.setViewPagerEnabled(false)

        val url = "http://121.40.247.127/v1/update_sentence"
        val requestQueue = Volley.newRequestQueue(requireContext())

        val jsonObjectRequest = object : JsonObjectRequest(
            Request.Method.POST, url, null,
            { response ->
                Log.d("NETWORK", "POST to update_sentence response: $response")
                fetchDailyWords(oldOriginalSentence, oldTranslatedSentence, wasTranslated, setButtonBack = true)
            },
            { error ->
                Log.e("NETWORK", "POST error: ${error.message}", error)
                Log.e("NETWORK", "Error details: networkResponse=${error.networkResponse?.statusCode}, data=${error.networkResponse?.data?.toString(Charsets.UTF_8)}")
                Toast.makeText(requireContext(), "Failed to update sentence", Toast.LENGTH_SHORT).show()
                sentenceTextView.text = if (wasTranslated) oldTranslatedSentence else oldOriginalSentence
                isSentenceTranslated = wasTranslated
                originalSentence = oldOriginalSentence
                translatedSentence = oldTranslatedSentence
                refreshButton.text = getString(R.string.refresh)
                refreshButton.isEnabled = true
                updateWordsButton.text = getString(R.string.update_words)
                updateWordsButton.isEnabled = true
                (activity as? MainActivity)?.setViewPagerEnabled(true)
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Cache-Control"] = "no-cache"
                return headers
            }
        }

        jsonObjectRequest.retryPolicy = DefaultRetryPolicy(
            10000,
            1,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
        requestQueue.add(jsonObjectRequest)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
    }
}