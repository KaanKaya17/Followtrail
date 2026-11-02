import json
import tempfile
import unittest
from unittest.mock import patch
from engine import Engine
from network import parse_page, parse_profile
from test_backend import profile, page
from test_scan_progress import ClockWait, PacedClient


class ListPhotoTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-');self.addCleanup(self.home.cleanup)
        self.now=[1800000000.0]
        self.engine=Engine(self.home.name,clock=lambda:self.now[0],request_delay=lambda:8)
        self.engine.cancel=ClockWait(self.now)
        self.engine.record_profile(parse_profile(profile()),own=True)
        self.engine.data['session']={'cookies':{'ds_user_id':'100','sessionid':'SYNTHETIC'}}

    def test_scan_keeps_picture_in_encrypted_list_but_only_owned_observation_can_request_it(self):
        payload=page(['200']);url='https://scontent.cdninstagram.com/avatar.jpg'
        payload['users'][0]['profile_pic_url']=url
        client=PacedClient(self.engine,[profile(followers=1),payload])
        with patch.object(self.engine,'client',return_value=client):state=self.engine.run('scan',{'direction':'followers'})
        self.assertEqual(len(client.calls),2)
        scan=self.engine.data['lists']['100:followers']['snapshots'][0]
        self.assertEqual(scan['members']['200']['pictureUrl'],url)
        self.assertNotIn(url,json.dumps(state))
        args={'accountId':'100','ownerId':'100','direction':'followers','scanId':scan['id'],'id':'200'}
        self.assertEqual(self.engine.list_picture(args)[2],url)
        for key,value in [('accountId','300'),('ownerId','300'),('direction','following'),('scanId','forged'),('id','999')]:
            self.assertIsNone(self.engine.list_picture({**args,key:value}))
        reopened=Engine(self.home.name)
        self.assertEqual(reopened.list_picture(args)[2],url)
        changed=page(['201']);changed['users'][0]['profile_pic_url']=url
        with patch.object(self.engine,'client',return_value=PacedClient(self.engine,[profile(followers=1),changed])):
            after=self.engine.run('scan',{'direction':'followers'})
        self.assertNotIn(url,json.dumps(after))
        self.assertEqual(after['lists']['100:followers']['comparison']['removed'],['200'])

    def test_missing_or_untrusted_optional_picture_never_discards_valid_member(self):
        for value in [None,{},123,'http://cdninstagram.com/a.jpg','https://cdninstagram.com.evil.test/a.jpg',
                      'https://u:p@cdninstagram.com/a.jpg']:
            payload=page(['200']);payload['users'][0]['profile_pic_url']=value
            parsed=parse_page(payload)
            self.assertIn('200',parsed.members)
            self.assertNotIn('pictureUrl',parsed.members['200'])

    def test_old_visible_partial_can_use_newer_same_identity_photo_without_merging_members(self):
        url='https://cdninstagram.com/new.jpg'
        old={'id':'old','ownerId':'100','direction':'followers','complete':False,
             'members':{'200':{'id':'200','username':'qa.old','name':'QA'}}}
        new={'id':'new','members':{'200':{'id':'200','username':'qa.renamed','name':'QA','pictureUrl':url},
                                  '201':{'id':'201','username':'qa.other','name':'QA','pictureUrl':url}}}
        self.engine.data['lists']['100:followers']={'snapshots':[],'retainedPartial':old,'draft':new}
        args={'accountId':'100','ownerId':'100','direction':'followers','scanId':'old','id':'200'}
        self.assertEqual(self.engine.list_picture(args)[2],url)
        self.assertIsNone(self.engine.list_picture({**args,'id':'201'}))
        self.assertNotIn('pictureUrl',old['members']['200'])
        self.assertEqual(len(self.engine.snapshot()['lists']['100:followers']['retainedPartial']['members']),1)

if __name__=='__main__':unittest.main()
