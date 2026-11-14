/* Keep unchanged DOM nodes, focus and controller-owned content during state updates. */
(function(root) {
  'use strict';
  const owned=new Set(['list-rows','list-exception-rows','list-pagination','daily-chart-root','overview-operation','notifications-operation','archive-dashboard-root']);
  const key=node=>node.nodeType!==1?'':node.id?'id:'+node.id:node.hasAttribute('data-id')?
    [node.tagName,node.dataset.action,node.dataset.id,node.dataset.value].join(':'):
    node.hasAttribute('data-day')?'day:'+node.dataset.day:'';
  function compatible(a,b) {return a && a.nodeType===b.nodeType && a.nodeName===b.nodeName && key(a)===key(b);}
  function patch(node,next) {
    if(node.nodeType!==1) {if(node.nodeValue!==next.nodeValue)node.nodeValue=next.nodeValue;return;}
    for(const attr of [...node.attributes])if(!next.hasAttribute(attr.name) && !attr.name.startsWith('data-loaded') && attr.name!=='data-context')node.removeAttribute(attr.name);
    for(const attr of next.attributes)if(node.getAttribute(attr.name)!==attr.value)node.setAttribute(attr.name,attr.value);
    if(!owned.has(node.id))children(node,next);
    if(node.tagName==='INPUT') {
      if(node.value!==next.value)node.value=next.value;
      if(node.checked!==next.checked)node.checked=next.checked;
    } else if(['TEXTAREA','SELECT'].includes(node.tagName) && node.value!==next.value)node.value=next.value;
  }
  function children(parent,next) {
    const keyed=new Map([...parent.childNodes].filter(node=>key(node)).map(node=>[key(node),node]));
    let cursor=parent.firstChild;
    for(const fresh of [...next.childNodes]) {
      let current=key(fresh)?keyed.get(key(fresh)):cursor;
      if(!compatible(current,fresh))current=null;
      if(!current) {current=fresh.cloneNode(true);parent.insertBefore(current,cursor);}
      else {if(current!==cursor)parent.insertBefore(current,cursor);patch(current,fresh);}
      cursor=current.nextSibling;
    }
    while(cursor){const nextNode=cursor.nextSibling;cursor.remove();cursor=nextNode;}
  }
  function update(parent,markup) {
    const template=document.createElement('template');template.innerHTML=markup;
    const active=document.activeElement,focused=parent.contains(active);
    const selection=focused && typeof active.selectionStart==='number'?[active.selectionStart,active.selectionEnd]:null;
    children(parent,template.content);
    if(focused && active.isConnected) {
      if(document.activeElement!==active)active.focus({preventScroll:true});
      if(selection && active.value.length>=selection[1])active.setSelectionRange(...selection);
    }
  }
  root.TrackerDOM=Object.freeze({update});
})(typeof window==='object'?window:globalThis);
