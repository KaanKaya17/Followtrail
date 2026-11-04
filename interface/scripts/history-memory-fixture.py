"""Production history owner with explicitly supplied documentation examples only."""
import json
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'desktop/backend'))
from history_pages import HistoryPages
owner=HistoryPages()
state=json.loads(sys.stdin.readline())
def emit(value):
    print(json.dumps(value,ensure_ascii=True,separators=(',',':')),flush=True)
emit(dict(type='ready',state=owner.publish(state)))
for line in sys.stdin:
    request=json.loads(line)
    operation,args=request['operation'],request.get('args',{})
    try:
        if operation=='history_page':result={'page':owner.page(args)}
        elif operation=='history_detail':result={'history':owner.detail(args)}
        elif operation=='read_notification':
            entry=next(e for e in state['notificationLog'] if e['id']==args['id'])
            entry['readAt']=1793631600
            result={'state':owner.publish(state)}
        else:raise ValueError('Unsupported documentation operation')
        emit(dict(type='control',id=request['id'],ok=True,**result))
    except Exception as exc:
        emit(dict(type='control',id=request['id'],ok=False,error={'message':str(exc)}))
