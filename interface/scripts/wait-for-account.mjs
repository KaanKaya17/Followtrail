import assert from 'node:assert/strict';

// Await each real IPC response in Node before evaluating readiness.
export async function waitForState(page,predicate) {
  const deadline=Date.now()+15000;
  while(Date.now()<deadline) {
    const response=await page.evaluate(()=>window.TrackerDesktop.command('state'));
    if(predicate(response))return response;
    await new Promise(resolve=>setTimeout(resolve,25));
  }
  throw Error('Timed out waiting for the synthetic backend state');
}
export async function waitForAccount(page,id) {
  const response=await waitForState(page,value=>value.state?.activeAccountId===id && !value.busy);
  assert.equal(response.state.account?.id,id);
  return response.state;
}
