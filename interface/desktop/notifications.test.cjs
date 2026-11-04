const test = require("node:test");
const assert = require("node:assert/strict");
const { EventEmitter } = require("node:events");
const { Backend } = require("./service.cjs");
const { changeNotification, showNotification, openAccountNotification } = require("./notifications.cjs");

test('list notification previews frozen names in four languages and respects muted list changes', () => {
 const membersAdded=['ada','deniz','cem'].map((username,i)=>({id:String(300+i),username,name:username}));
 const list={eventId:'list-qa',category:'lists',username:'qa.owner',label:'Liste taraması',after:{membersAdded,membersRemoved:[{id:'400',username:'eski.ad'}]}};
 const entry={id:'group',changes:[list],selectedChangeIds:['list-qa']};
 for(const language of ['tr','en','es','pt-BR']) {
  const notice=changeNotification(entry,language);
  assert.match(notice.body,/@ada, @deniz/);assert.match(notice.body,/@eski\.ad/);
  assert.doesNotMatch(notice.body,/@cem/);assert.equal(notice.logId,'group');
 }
 const muted=changeNotification({...entry,selectedChangeIds:[]});
 assert.doesNotMatch(muted.body,/@ada|@eski\.ad/);
 const compact={...list,after:{...list.after,added:103,removed:1,membersAdded:membersAdded.slice(0,2)}};
 assert.match(changeNotification({...entry,changes:[compact]}).body,/101 hesap daha/);
});

test('notification click switches to owning account before opening its log and never opens a foreign log while busy',async()=>{
 const calls=[],entry={id:'qa-log',ownerId:'100'};
 const backend={state:{activeAccountId:'400'},command:async(operation,args)=>{calls.push({operation,args});return {ok:true};}};
 const options={entry,backend,showWindow:()=>calls.push('show'),send:event=>calls.push(event),onBusy:()=>calls.push('busy')};
 await openAccountNotification(options);
 assert.deepEqual(calls,['show',{operation:'switch_account',args:{id:'100'}},{type:'open_notification',id:'qa-log'}]);
 calls.length=0;backend.command=async()=>({ok:false,error:{code:'busy'}});
 await openAccountNotification(options);
 assert.deepEqual(calls,['show','busy']);
 calls.length=0;backend.state.activeAccountId='100';
 await openAccountNotification(options);
 assert.deepEqual(calls,['show',{type:'open_notification',id:'qa-log'}]);
});

test('worker-projected notification retains owning account while renderer state belongs to another account',()=>{
 const service=new Backend({onEvent:()=>{}}),received=[];
 service.onNotifications=entries=>received.push(...entries);
 service.message({type:'ready',state:{activeAccountId:'400',notificationLog:[]}});
 const entry={id:'qa-group',ownerId:'100',ownerUsername:'qa.owner',desktopEligible:true};
 service.message({type:'result',state:{activeAccountId:'400',notificationLog:[]},notifications:[entry]});
 assert.deepEqual(received,[entry]);
 service.message({type:'result',state:{activeAccountId:'100',notificationLog:[entry]},notifications:[]});
 assert.equal(received.length,1);
});

test("one observation log is delivered once with suspicion priority and a log-detail target", () => {
  const received = [];
  const service = new Backend({ onEvent: () => {} });
  service.onNotifications = (entries) => {
    for (const entry of entries) received.push(changeNotification(entry));
  };
  const event = {
    eventId: "access1",
    personId: "200",
    category: "access",
    label: "Engellenme şüphesi",
    username: "qa.arda",
    title: "Engellenme şüphesi · @qa.arda. Hesap kapanmış da olabilir.",
    new: { status: "suspected" },
  };
  const entry = {
    id: "control1",
    desktopEligible: true,
    selectedChangeIds: ["access1", "count1"],
    changes: [
      event,
      {
        eventId: "count1",
        personId: "200",
        username: "qa.arda",
        category: "followers",
        label: "Takipçi sayısı",
        title: "Sayı değişti",
      },
    ],
  };
  service.message({
    type: "ready",
    state: { notificationLog: [] },
  });
  service.message({
    type: "result",
    notificationGroupId: "control1",
    state: {
      notificationLog: [entry],
    },
  });
  service.message({
    type: "result",
    notificationGroupId: "control1",
    state: {
      notificationLog: [entry],
    },
  });
  assert.equal(received.length, 1);
  assert.equal(received[0].title, "InstagramTracker · Engellenme şüphesi");
  assert.equal(received[0].logId, "control1");
  assert.equal(received[0].body.includes("şüphesi"), true);
  assert.equal(received[0].body.includes("engelledi"), false);
});

test("several selected fields in one control produce one summary; muted fields stay out of the Windows summary", () => {
  const selected = changeNotification({
    id: "control",
    selectedChangeIds: ["username", "bio"],
    changes: [
      {
        eventId: "username",
        username: "qa.arda",
        category: "username",
        label: "Kullanıcı adı",
      },
      {
        eventId: "bio",
        username: "qa.arda",
        category: "bio",
        label: "Biyografi",
      },
      {
        eventId: "followers",
        username: "qa.arda",
        category: "followers",
        label: "Takipçi sayısı",
      },
    ],
  });
  assert.equal(selected.body, "@qa.arda · 2 değişiklik. Ayrıntıları aç.");
  assert.equal(selected.logId, "control");
});

test("muted control logs and read-status changes do not replay desktop notifications", () => {
  let delivered = 0;
  const service = new Backend({ onEvent: () => {} });
  service.onNotifications = (entries) => (delivered += entries.length);
  const old = { id: "old", desktopEligible: true, readAt: null };
  const muted = { id: "muted", desktopEligible: false };
  service.message({ type: "ready", state: { notificationLog: [old] } });
  service.message({
    type: "result",
    notificationGroupId: "muted",
    state: { notificationLog: [muted, { ...old, readAt: 123 }] },
  });
  assert.equal(delivered, 0);
});

test("restoring historical eligible logs never sends desktop notifications for the import operation", () => {
  let delivered = 0;
  const service = new Backend({ onEvent: () => {} });
  service.onNotifications = (entries) => (delivered += entries.length);
  service.message({ type: "ready", state: { notificationLog: [] } });
  service.message({
    type: "result",
    notificationGroupId: "import-operation",
    state: {
      notificationLog: [{ id: "historical-group", desktopEligible: true }],
    },
  });
  assert.equal(delivered, 0);
});
test("native notification callbacks open the evidence and report delivery failure", () => {
  let notification,
    clicked = 0,
    failed = 0;
  class Notification extends EventEmitter {
    static isSupported() {
      return true;
    }
    constructor(options) {
      super();
      this.options = options;
      notification = this;
    }
    show() {
      this.shown = true;
    }
  }
  showNotification({
    Notification,
    title: "Engellenme şüphesi",
    body: "Kanıtları incele",
    onClick: () => clicked++,
    onFailure: () => failed++,
  });
  assert.equal(notification.shown, true);
  notification.emit("click");
  notification.emit("failed");
  assert.equal(clicked, 1);
  assert.equal(failed, 1);
});
