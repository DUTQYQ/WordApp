import mysql.connector
import random
import requests
import json
from flask import Flask, jsonify, request
import time
from datetime import datetime
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

# SiliconFlow API 配置
SILICONFLOW_API_KEY = 'sk-ymjhugwmwdzzhgqrkywetxkzykapmgbxhdvxujponlhgsgtf'
SILICONFLOW_API_URL = 'https://api.siliconflow.cn/v1/chat/completions'

# MySQL 连接函数
def get_db_connection():
    try:
        return mysql.connector.connect(
            host="localhost",
            user="root",
            password="qiuyuqian",
            database="cet6_vocabulary"
        )
    except mysql.connector.Error as e:
        print(f"Database connection failed: {str(e)}")
        raise

# 归档已使用的单词到 used_word 表
def archive_used_words():
    db = None
    cursor = None
    try:
        db = get_db_connection()
        cursor = db.cursor()
        today = datetime.now().date()
        cursor.execute("INSERT INTO used_word (word, translation, used_date) SELECT word, translation, %s FROM words", (today,))
        cursor.execute("DELETE FROM words")
        db.commit()
    except mysql.connector.Error as e:
        print(f"Error archiving words: {str(e)}")
        raise
    finally:
        if cursor:
            cursor.close()
        if db:
            db.close()

# 从 word 表中选取新的三个随机单词插入到 words 表
def select_new_words():
    db = None
    cursor = None
    try:
        db = get_db_connection()
        cursor = db.cursor()
        cursor.execute("SELECT word, translation FROM word ORDER BY RAND() LIMIT 3")
        new_words = cursor.fetchall()
        for w in new_words:
            cursor.execute("INSERT INTO words (word, translation) VALUES (%s, %s)", w)
        db.commit()
    except mysql.connector.Error as e:
        print(f"Error selecting new words: {str(e)}")
        raise
    finally:
        if cursor:
            cursor.close()
        if db:
            db.close()

# 获取当天的三个单词
def get_daily_words():
    db = None
    cursor = None
    try:
        db = get_db_connection()
        cursor = db.cursor()
        cursor.execute("SELECT word, translation FROM words")
        words = [{'word': row[0], 'translation': row[1]} for row in cursor.fetchall()]
        return words
    except mysql.connector.Error as e:
        print(f"Error fetching daily words: {str(e)}")
        return []
    finally:
        if cursor:
            cursor.close()
        if db:
            db.close()

# 配置带重试的 HTTP 客户端
def create_retry_session():
    session = requests.Session()
    retries = Retry(
        total=3,
        backoff_factor=1,
        status_forcelist=[429, 500, 502, 503, 504],
        allowed_methods=["POST"]
    )
    session.mount("https://", HTTPAdapter(max_retries=retries))
    return session

# 调用 SiliconFlow API 生成句子和翻译
def generate_sentence(words):
    word_list = [w['word'] for w in words]
    prompt = f"Generate a concise English sentence using these exact words: {', '.join(word_list)}. Do not explain, just provide the sentence."
    headers = {'Authorization': f'Bearer {SILICONFLOW_API_KEY}', 'Content-Type': 'application/json'}
    data = {
        'model': 'Qwen/QwQ-32B',
        'messages': [{'role': 'user', 'content': prompt}],
        'stream': False,
        'temperature': 0.7,
        'top_p': 0.7,
        'top_k': 50,
        'frequency_penalty': 0.5,
        'n': 1
    }
    session = create_retry_session()
    try:
        response = session.post(SILICONFLOW_API_URL, headers=headers, json=data, timeout=60)
        if response.status_code == 200:
            response_data = response.json()
            content = response_data['choices'][0]['message']['content'].strip()
            if content:
                translation = translate_sentence(content)
                return content, translation if translation != "Translation failed" else ""
            else:
                reasoning = response_data['choices'][0]['message'].get('reasoning_content', '')
                if reasoning:
                    sentences = reasoning.split('\n')
                    for s in reversed(sentences):
                        if s.strip() and all(word in s.lower() for word in word_list):
                            translation = translate_sentence(s.strip())
                            return s.strip(), translation if translation != "Translation failed" else ""
                default_sentence = "The curious student explored a benevolent world."
                return default_sentence, translate_sentence(default_sentence)
        else:
            print(f"API request failed: {response.status_code}")
            default_sentence = "The curious student explored a benevolent world."
            return default_sentence, translate_sentence(default_sentence)
    except requests.RequestException as e:
        print(f"API request error: {str(e)}")
        default_sentence = "The curious student explored a benevolent world."
        return default_sentence, translate_sentence(default_sentence)

# 调用 SiliconFlow API 翻译句子
def translate_sentence(sentence):
    prompt = f"Translate this English sentence to Chinese: {sentence}"
    headers = {'Authorization': f'Bearer {SILICONFLOW_API_KEY}', 'Content-Type': 'application/json'}
    data = {
        'model': 'Qwen/QwQ-32B',
        'messages': [{'role': 'user', 'content': prompt}],
        'stream': False,
        'temperature': 0.7,
        'top_p': 0.7,
        'top_k': 50,
        'frequency_penalty': 0.5,
        'n': 1
    }
    session = create_retry_session()
    try:
        response = session.post(SILICONFLOW_API_URL, headers=headers, json=data, timeout=60)
        if response.status_code == 200:
            response_data = response.json()
            return response_data['choices'][0]['message']['content'].strip()
        else:
            print(f"Translation API failed: {response.status_code}")
            return "Translation failed"
    except requests.RequestException as e:
        print(f"Translation request error: {str(e)}")
        return "Translation failed"

# 检查并更新每日句子
def update_daily_sentence(force=False):
    try:
        if not force:
            db = get_db_connection()
            cursor = db.cursor()
            cursor.execute("SELECT created_at FROM daily_sentences ORDER BY created_at DESC LIMIT 1")
            last_record = cursor.fetchone()
            today = datetime.now().date()
            if last_record and last_record[0].date() == today:
                return None
            cursor.close()
            db.close()
        # 归档并选取新单词
        archive_used_words()
        select_new_words()
        # 获取新单词
        words = get_daily_words()
        if not words or len(words) < 3:
            raise Exception("Failed to fetch three daily words")
        sentence, translation = generate_sentence(words)
        if not sentence:
            raise Exception("Failed to generate sentence")
        db = get_db_connection()
        cursor = db.cursor()
        cursor.execute(
            "INSERT INTO daily_sentences (sentence, sentence_translation, word1, word1_translation, word2, word2_translation, word3, word3_translation) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s, %s)",
            (sentence, translation or "", 
             words[0]['word'], words[0]['translation'],
             words[1]['word'], words[1]['translation'],
             words[2]['word'], words[2]['translation'])
        )
        db.commit()
        cursor.execute("SELECT created_at FROM daily_sentences ORDER BY created_at DESC LIMIT 1")
        created_at = cursor.fetchone()[0]
        print(f"Updated daily sentence: {sentence} -> {translation}")
        return created_at.isoformat()
    except Exception as e:
        print(f"Error in update_daily_sentence: {str(e)}")
        raise
    finally:
        if 'cursor' in locals():
            cursor.close()
        if 'db' in locals():
            db.close()

# Flask API
app = Flask(__name__)

@app.route('/v1/words', methods=['GET'])
def get_daily_sentence():
    db = None
    cursor = None
    try:
        db = get_db_connection()
        cursor = db.cursor()
        cursor.execute("""
            SELECT sentence, sentence_translation,
                   word1, word1_translation,
                   word2, word2_translation,
                   word3, word3_translation
            FROM daily_sentences
            ORDER BY created_at DESC LIMIT 1
        """)
        result = cursor.fetchone()
        if result:
            response = {
                "sentence": result[0],
                "sentence_translation": result[1] or "",
                "words": [
                    {"word": result[2], "translation": result[3]},
                    {"word": result[4], "translation": result[5]},
                    {"word": result[6], "translation": result[7]}
                ]
            }
            return jsonify(response)
        return jsonify({"sentence": "", "sentence_translation": "", "words": [], "error": "No valid sentence found"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        if cursor:
            cursor.close()
        if db:
            db.close()

@app.route('/v1/translate', methods=['POST'])
def translate():
    print(f"Received POST to /v1/translate: {request.json}")
    data = request.json
    sentence = data.get('sentence', '')
    if not sentence:
        return jsonify({"error": "No sentence provided"}), 400
    translation = translate_sentence(sentence)
    if translation == "Translation failed":
        return jsonify({"error": "Translation failed"}), 500
    return jsonify({"translation": translation})

@app.route('/v1/update_sentence', methods=['POST'])
def update_sentence():
    print(f"Received POST to /v1/update_sentence at {datetime.now().isoformat()}")
    try:
        created_at = update_daily_sentence(force=True)
        if created_at:
            return jsonify({"message": "Daily sentence updated", "created_at": created_at})
        return jsonify({"message": "No update needed"}), 200
    except Exception as e:
        print(f"Error updating sentence: {str(e)}")
        return jsonify({"error": "Failed to update sentence"}), 500

@app.route('/v1/status', methods=['GET'])
def get_sentence_status():
    db = None
    cursor = None
    try:
        db = get_db_connection()
        cursor = db.cursor()
        cursor.execute("SELECT created_at FROM daily_sentences ORDER BY created_at DESC LIMIT 1")
        result = cursor.fetchone()
        if result:
            return jsonify({"created_at": result[0].isoformat(), "status": "ready"})
        return jsonify({"status": "empty"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        if cursor:
            cursor.close()
        if db:
            db.close()

if __name__ == "__main__":
    print("Starting app...")
    print("WARNING: Running Flask in development mode. For production, use Gunicorn.")
    try:
        update_daily_sentence()
        print("Running Flask server on http://0.0.0.0:80...")
        app.run(host='0.0.0.0', port=80, debug=False)
    except Exception as e:
        print(f"Failed to start Flask server: {str(e)}")