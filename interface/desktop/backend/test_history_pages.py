import unittest
from history_pages import HistoryPages
from network import AppFailure


class HistoryPagesTest(unittest.TestCase):
    def setUp(self):
        self.owner=HistoryPages()
        self.events=[dict(id=str(i),type='profile',personId='200',old={'followers':i},new={'followers':i+1}) for i in range(205)]
        change=dict(eventId='change',personId='200',username='qa',category='lists',before=None,
                    after=dict(added=103,removed=0,membersAdded=[dict(id=str(i),username='qa.'+str(i)) for i in range(103)],membersRemoved=[]))
        self.logs=[dict(id='log',readAt=None,changes=[change],selectedChangeIds=['change'])]
        self.state=dict(account={'id':'100'},events=self.events,notificationLog=self.logs)
        self.wire=self.owner.publish(self.state)

    def query(self):
        return dict(accountId='100',revision=self.wire['historyMeta']['revision'],kind='events',type='all',personId='',section='all',read='all',offset=0)

    def test_snapshot_is_small_but_paging_and_detail_keep_all_retained_evidence(self):
        self.assertEqual(len(self.wire['events']),5)
        self.assertEqual(self.wire['notificationLog'],[])
        query=self.query();rows=[]
        while True:
            page=self.owner.page(query);self.assertLessEqual(len(page['items']),100);rows+=page['items']
            if page['nextOffset'] is None:break
            query['offset']=page['nextOffset']
        self.assertEqual(rows,self.events)
        query=self.query();query['kind']='notifications'
        self.assertEqual(len(self.owner.page(query)['items'][0]['changes'][0]['after']['membersAdded']),2)
        detail=self.owner.detail(dict(accountId='100',revision=self.wire['historyMeta']['revision'],kind='notification',id='log'))
        self.assertEqual(len(detail['changes'][0]['after']['membersAdded']),103)
        self.assertEqual(self.wire['historyMeta']['notificationCounts']['unread']['lists'],1)

    def test_publication_is_stable_until_content_changes_and_read_markers_are_frozen(self):
        self.assertEqual(self.owner.publish(self.state)['historyMeta']['revision'],self.wire['historyMeta']['revision'])
        self.logs[0]['readAt']=123
        args=dict(accountId='100',revision=self.wire['historyMeta']['revision'],kind='notification',id='log')
        self.assertIsNone(self.owner.detail(args)['readAt'])
        self.owner.publish(self.state)
        with self.assertRaises(AppFailure):self.owner.detail(args)
        with self.assertRaises(AppFailure):self.owner.page(self.query())

    def test_foreign_accounts_malformed_offsets_and_restored_sources_fail_closed(self):
        for field,value in [('accountId','200'),('offset',-1),('offset',True),('kind','unknown')]:
            args=self.query();args[field]=value
            with self.assertRaises(AppFailure):self.owner.page(args)
        restored={**self.state,'events':[dict(e) for e in self.events]}
        self.assertNotEqual(self.owner.publish(restored)['historyMeta']['revision'],self.wire['historyMeta']['revision'])

    def test_visibility_checks_stay_in_both_appropriate_filters(self):
        self.state['events']=[dict(id='access',type='check',topic='access',personId='200',old=None,new={})]
        self.wire=self.owner.publish(self.state)
        for kind in ('access','check'):
            query=self.query();query.update(type=kind,section='changes')
            self.assertEqual(self.owner.page(query)['total'],1)

    def test_fresh_install_uses_an_explicit_empty_account_namespace(self):
        state=dict(account=None,events=[],notificationLog=[])
        view=self.owner.publish(state)
        query=self.query();query.update(accountId='',revision=view['historyMeta']['revision'])
        self.assertEqual(self.owner.page(query)['total'],0)
        query['kind']='notifications'
        self.assertEqual(self.owner.page(query)['total'],0)
