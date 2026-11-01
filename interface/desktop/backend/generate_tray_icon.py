"""Render the existing chart mark for Windows executable, window and tray."""
from pathlib import Path
from PIL import Image, ImageDraw
import json

root=Path(__file__).resolve().parents[2]
theme=json.loads((root.parent/'design/theme.json').read_text(encoding='utf-8'))
# Same brand mark as web/favicon.svg; no external artwork or network.
scale=4
image=Image.new('RGBA',(64*scale,64*scale),(0,0,0,0))
draw=ImageDraw.Draw(image)
draw.rounded_rectangle((0,0,64*scale-1,64*scale-1),radius=20*scale,fill=theme['colors']['violet'])
draw.line([(14*scale,44*scale),(29*scale,29*scale),(39*scale,37*scale),(52*scale,17*scale)],fill='white',width=4*scale,joint='curve')
draw.line([(40*scale,17*scale),(52*scale,17*scale),(52*scale,29*scale)],fill='white',width=4*scale,joint='curve')
target=root/'.cache/tray-icon.png'
target.parent.mkdir(parents=True,exist_ok=True)
image.resize((64,64),Image.Resampling.LANCZOS).save(target,format='PNG')
image.save(root/'.cache/app-icon.png',format='PNG')
image.save(root/'.cache/app-icon.ico',format='ICO',sizes=[(s,s) for s in (16,24,32,48,64,128,256)])
print('Tracker executable, window and tray icons rendered.')
