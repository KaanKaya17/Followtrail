(() => {
  try {
    if (typeof require !== 'function') return {available:false};
    const doc=require('__OPERATION__.graphql');
    if (!doc || doc.params?.id!=='__DOCUMENT__' || doc.params?.name!=='__OPERATION__' || doc.params?.operationKind!=='query') return {available:false};
    const args=doc.operation?.argumentDefinitions||[],selections=doc.operation?.selections||[];
    const valid=x=>typeof x.name==='string' && /^[A-Za-z_][A-Za-z0-9_]{0,63}$/.test(x.name);
    const fields=selections.filter(x=>x.kind==='LinkedField'||x.kind==='ScalarField');
    const names=items=>Array.from(new Set(items.slice(0,8).filter(valid).map(x=>x.name)));
    return {document:doc.params.id,operation:doc.params.name,kind:doc.params.operationKind,
      incomplete:args.length>8||selections.length>8||fields.length!==selections.length||!args.every(valid)||!fields.every(valid),
      arguments:names(args),rootFields:names(fields)};
  } catch (_) {return {available:false};}
})();
