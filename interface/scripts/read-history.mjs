// Inspect retained history through the public paged IPC, never a private vault.
export async function waitHistory(page) {
  await page.locator('.timeline-page[aria-busy="false"], .notifications-page[aria-busy="false"]').waitFor();
}
export async function readHistory(page,kind,state) {
  state ||= (await page.evaluate(()=>window.TrackerDesktop.command('state'))).state;
  const result=[];
  let offset=0;
  while(offset!==null) {
    const response=await page.evaluate(args=>window.TrackerDesktop.command('history_page',args),{
      accountId:state.activeAccountId || state.account?.id || '',revision:state.historyMeta.revision,
      kind,type:'all',personId:'',section:'all',read:'all',offset,
    });
    if(!response.ok)throw Error(response.error?.message || 'History page unavailable');
    result.push(...response.page.items);offset=response.page.nextOffset;
  }
  return result;
}
export async function historyDetail(page,kind,id) {
  const state=(await page.evaluate(()=>window.TrackerDesktop.command('state'))).state;
  const response=await page.evaluate(args=>window.TrackerDesktop.command('history_detail',args),{
    accountId:state.activeAccountId || state.account?.id || '',revision:state.historyMeta.revision,kind,id,
  });
  if(!response.ok)throw Error(response.error?.message || 'History detail unavailable');
  return response.history;
}
