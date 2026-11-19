"""Bundle the checked-out website and its font. Never use the old iOS archive."""
from pathlib import Path
import json, re, shutil, urllib.request
from PIL import Image
import cairosvg, io
root = Path(__file__).resolve().parent
repo = root.parent
web = root / 'Offline'
web.mkdir(exist_ok=True)
html = (repo / 'index.html').read_text()
for name in ['apple-touch-icon.png', 'apple-touch-icon-120.png', 'apple-touch-icon-152.png', 'apple-touch-icon-167.png', 'favicon.ico', 'favicon.svg', 'news.txt', 'windows.txt', 'Android.txt']:
    shutil.copy2(repo / name, web / name)
css_url = 'https://fonts.googleapis.com/css2?family=Vazirmatn:wght@400;500;600;700&display=swap'
request = urllib.request.Request(css_url, headers={'User-Agent': 'Mozilla/5.0'})
css = urllib.request.urlopen(request, timeout=60).read().decode()
for i, url in enumerate(dict.fromkeys(re.findall(r'url\((https://[^)]+)\)', css))):
    name = f'font-{i}' + ('.woff2' if '.woff2' in url else '.ttf')
    (web / name).write_bytes(urllib.request.urlopen(url, timeout=60).read())
    css = css.replace(url, name)
assert 'url(https://' not in css
(web / 'fonts.css').write_text(css)
html = html.replace(css_url, 'fonts.css')
html = re.sub(r'<link[^>]+rel="preconnect"[^>]*>', '', html)
# Avoid missing favicon requests in the bundled page.
html = html.replace('Last%20icon.svg', 'favicon.svg')
html = re.sub(r'<link[^>]+href="favicon-(?:16|32).png"[^>]*>', '', html)
(web / 'index.html').write_text(html)
assets = root / 'Assets.xcassets'
icons = assets / 'AppIcon.appiconset'
icons.mkdir(parents=True, exist_ok=True)
(assets / 'Contents.json').write_text(json.dumps({'info': {'author': 'xcode', 'version': 1}}))
# Preserve the original icon proportions; crop the same 7 pixels from every edge.
original = Image.open(repo / 'apple-touch-icon.png').convert('RGBA')
assert original.size == (180, 180)
original = original.crop((7, 7, 173, 173))
background = Image.new('RGBA', original.size, (96, 96, 96, 255))
background.alpha_composite(original)
source = background.convert('RGB').resize((1024, 1024), Image.Resampling.LANCZOS)
entries = []
for idiom, sizes in [('iphone', [(20,2),(20,3),(29,2),(29,3),(40,2),(40,3),(60,2),(60,3)]), ('ipad', [(20,1),(20,2),(29,1),(29,2),(40,1),(40,2),(76,1),(76,2),(83.5,2)]), ('ios-marketing', [(1024,1)])]:
    for size, scale in sizes:
        filename = f'{idiom}-{size}-{scale}.png'
        pixels = round(size * scale)
        source.resize((pixels,pixels), Image.Resampling.LANCZOS).save(icons / filename)
        entries.append({'idiom':idiom,'size':f'{size}x{size}','scale':f'{scale}x','filename':filename})
(icons / 'Contents.json').write_text(json.dumps({'images':entries,'info':{'author':'xcode','version':1}}, indent=2))
print('Offline HTML, local fonts and app icons prepared.')
