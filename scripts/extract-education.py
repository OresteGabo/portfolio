from html.parser import HTMLParser
from pathlib import Path
import json
import re

class Extract(HTMLParser):
    def __init__(self):
        super().__init__()
        self.blocks = []
        self.current = None
        self.math_label = None
        self.url = None

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if tag == 'figure' and attributes.get('role') == 'math':
            self.math_label = attributes.get('aria-label')
        if tag in ('br', 'hr', 'img', 'input', 'wbr', 'meta', 'link'):
            if self.current is not None:
                self.current[1].append(' ')
            return
        if self.current is None and tag in ('h1', 'h2', 'h3', 'h4', 'p', 'li', 'summary', 'math', 'td', 'th', 'pre', 'a', 'figcaption'):
            self.current = [tag, [self.math_label] if tag == 'math' and self.math_label else [], 1]
            self.url = attributes.get('href') if tag == 'a' else None
        elif self.current is not None:
            self.current[2] += 1

    def handle_startendtag(self, tag, attrs):
        if tag in ('br', 'hr', 'img', 'input', 'wbr', 'meta', 'link'):
            self.handle_starttag(tag, attrs)
        else:
            self.handle_starttag(tag, attrs)
            self.handle_endtag(tag)

    def handle_endtag(self, tag):
        if tag in ('br', 'hr', 'img', 'input', 'wbr', 'meta', 'link'):
            return
        if self.current is not None:
            self.current[2] -= 1
            if self.current[2] <= 0:
                text = re.sub(r'\s+', ' ', ''.join(self.current[1])).strip()
                if text:
                    self.blocks.append({'kind': self.current[0], 'text': text, **({'url': self.url} if self.url and self.url.startswith('https://') else {})})
                if self.current[0] == 'math':
                    self.math_label = None
                self.current = None
                self.url = None

    def handle_data(self, data):
        if self.current is not None:
            if self.current[0] != 'math' or not self.math_label:
                self.current[1].append(data)
        elif data.strip():
            self.blocks.append({'kind': 'text', 'text': re.sub(r'\s+', ' ', data).strip()})

parser = Extract()
parser.feed(Path('/private/tmp/portfolio-education.html').read_text())
output = Path(__file__).resolve().parent.parent / 'app/shared/src/commonMain/composeResources/files/education.json'
output.write_text(json.dumps(parser.blocks, ensure_ascii=False, indent=2) + '\n')
print(f'Copied {len(parser.blocks)} native education blocks.')
