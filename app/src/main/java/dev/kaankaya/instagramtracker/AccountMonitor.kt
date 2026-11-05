package dev.kaankaya.instagramtracker

import android.content.Context

/** One shared request reservation, bounded sequential turns, independent account vaults. */
internal class AccountMonitor(private val context: Context,private val gate: RequestGate,
    private val api: (RequestGate)->InstagramApi = {InstagramApi(it)},
    private val lists: (RequestGate)->FollowListSource = {AccountFollowListReader(it)},
    private val runtime: ReadRuntime=SystemReadRuntime) {
    private val catalog=AccountCatalog(context)
    fun hasWork(notifications: Boolean): Boolean = catalog.entries().any {entry->
        val saved=SessionVault(context,owner=entry.id).read()
        val scoped=gate.forAccount(entry.id)
        saved!=null && !scoped.requiresConnection() && !scoped.manualOnly() &&
            (saved.automation.enabled || scoped.backgroundOwnEnabled() || notifications && saved.people.any {it.watching})
    }
    fun cycle(cancelled: ()->Boolean, clients: (InstagramApi,FollowListSource)->Unit = {_,_->}) {
        val notices=ChangeNotifications(context);val started=runtime.elapsed();var reserved=false
        repeat(AutomaticEngine.MAX_PAGES) {
            if(cancelled() || Thread.currentThread().isInterrupted || gate.quickWatchActive() || gate.preparationActive() || gate.automationHeld() || gate.restrictionPending())return
            val selected=catalog.ordered().firstOrNull {entry->
                val saved=SessionVault(context,owner=entry.id).read() ?: return@firstOrNull false
                val scoped=gate.forAccount(entry.id)
                !scoped.requiresConnection() && !scoped.manualOnly() && scoped.budgetNextAt(runtime.now()) <= runtime.now() &&
                    (saved.automation.enabled || scoped.backgroundOwnEnabled() || notices.allowed()) &&
                    AutomaticQueue.next(saved,FollowListVault(context,owner=entry.id).archive(saved.session),runtime.now(),ownIntervalMillis=scoped.monitorIntervalMillis(),ownEnabled=scoped.backgroundOwnEnabled())!=null
            } ?: return
            val scoped=gate.forAccount(selected.id)
            if(!reserved) {if(!scoped.reserveAutomatic(runtime.now()))return;reserved=true}
            while(scoped.nextAllowedAt()>runtime.now()) {
                if(cancelled() || gate.restrictionPending() || gate.automationHeld() || gate.quickWatchActive() || gate.preparationActive() || runtime.elapsed()-started>=AutomaticEngine.ROUND_MS)return
                if(!runtime.pause(minOf(250L,scoped.nextAllowedAt()-runtime.now()),cancelled))return
            }
            if(runtime.elapsed()-started>=AutomaticEngine.ROUND_MS || cancelled())return
            val reader=api(scoped);val source=lists(scoped);clients(reader,source)
            try {
                AutomaticEngine(SessionVault(context,owner=selected.id),FollowListVault(context,owner=selected.id),scoped,
                    reader,source,{!cancelled() && (SessionVault(context,owner=selected.id).read()?.automation?.enabled==true || scoped.backgroundOwnEnabled() || notices.allowed())},
                    notices::post,notices::postList,runtime).reservedTurn(started,cancelled)
            }catch (_: ApiFailure) {
                // A failed account keeps its own pause. Shared restrictions stop the whole round.
            }finally {reader.cancel();source.cancel();catalog.worked(selected.id)}
        }
    }
}
