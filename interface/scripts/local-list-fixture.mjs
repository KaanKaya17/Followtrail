// Real offline worker for renderer fixtures. The caller supplies a synthetic home.
import {spawn} from 'node:child_process';
import {createInterface} from 'node:readline';
import path from 'node:path';
export async function localListFixture(python,backend,home) {
  const child=spawn(python,['-X','utf8',path.join(backend,'worker.py'),'--data-dir',home,'--offline'],{windowsHide:true,stdio:['pipe','pipe','pipe']});
  child.stderr.resume();
  const pending=new Map();let counter=0;
  let ready,fail;
  const first=new Promise((resolve,reject)=>{ready=resolve;fail=reject;});
  const input=createInterface({input:child.stdout});
  input.on('line',line=>{
    const message=JSON.parse(line);
    if(message.type==='ready')ready(message.state);
    if(message.type==='fatal')fail(Error(message.message));
    const waiter=pending.get(message.id);
    if(waiter){pending.delete(message.id);waiter.resolve(message);}
  });
  child.on('error',fail);
  child.on('exit',()=>{fail(Error('Fixture worker exited'));for(const p of pending.values())p.reject(Error('Fixture worker exited'));pending.clear();});
  const timer=setTimeout(()=>{fail(Error('Fixture worker startup timeout'));child.kill();},15000);
  try {
    const state=await first;
    return {state,request:args=>new Promise((resolve,reject)=>{
      const id=++counter;pending.set(id,{resolve,reject});
      child.stdin.write(JSON.stringify({id,operation:'list_page',args})+'\n');
    }),close:()=>new Promise(resolve=>{
      if(child.exitCode!==null){input.close();resolve();return;}
      const stop=setTimeout(()=>child.kill(),5000);
      child.once('exit',()=>{clearTimeout(stop);input.close();resolve();});child.stdin.end();
    })};
  } catch(error) {child.kill();throw error;} finally {clearTimeout(timer);}
}
