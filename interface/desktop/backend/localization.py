"""Local display language; source evidence and account settings stay unchanged."""
import json
from pathlib import Path
import re
import sys

LANGUAGES = ('tr', 'en')
_path = (Path(sys._MEIPASS) / 'locales/en.json' if getattr(sys, 'frozen', False)
         else Path(__file__).resolve().parents[2] / 'locales/en.json')
_english = json.loads(_path.read_text(encoding='utf-8'))


def initialize(data):
    if isinstance(data.get('settings'), dict):
        data['settings'].setdefault('language', 'tr')


def translate(message, language='tr', values=()):
    text = _english.get(message, message) if language == 'en' else message
    if isinstance(text, dict):
        count = values[text.get('count', 0)] if values else None
        text = text['one'] if count in (1, '1') else text['other']
    return re.sub(r'\{(\d+)\}', lambda m: str(values[int(m[1])]) if int(m[1]) < len(values) else m[0], text) if values else text


def error(value, language):
    return {**value, 'message': translate(value['message'], language)} if value else value
