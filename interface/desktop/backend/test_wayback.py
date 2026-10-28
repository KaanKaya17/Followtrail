"""Offline archive lifecycle, encryption, rolling history and failure regressions."""
import base64
import copy
from io import BytesIO
from pathlib import Path
import tempfile
import unittest
from PIL import Image
from engine import Engine, validate_archive
from network import parse_profile
from storage import StorageFailure, protect
import json
import wayback

def photo(color):
    stream=BytesIO();Image.new('RGB',(16,16),color).save(stream,format='JPEG')
    return wayback.PREFIX+base64.b64encode(stream.getvalue()).decode()

class WaybackTests(unittest.TestCase):
    def setUp(self):
        self.home=tempfile.TemporaryDirectory(prefix='TrackerQA-');self.addCleanup(self.home.cleanup)
        self.at=1800000000;self.e=Engine(self.home.name,clock=lambda:self.at)
        self.e.record_profile(self.profile(),True);self.e.save()
    def profile(self,bio='Old bio',picture='one',avatar=None):
        p=parse_profile({'user':{'pk':'100','username':'qa.owner','follower_count':2,'following_count':1,'biography':bio,'profile_pic_id':picture}})
        if avatar:p['avatar']=avatar
        return p
    def enable(self):self.e.run('wayback',{'id':'100','enabled':True})
    def test_opt_in_baseline_rotation_and_encrypted_restart(self):
        self.assertEqual(self.e.data['wayback']['profiles'],{})
        self.enable()
        for i in range(380):
            self.at+=300;self.e.record_profile(self.profile('New bio' if i>=1 else 'Old bio'),True)
        self.e.save();new=Engine(self.home.name)
        self.assertEqual(len(new.data['history']['100']),366)
        entries=new.data['wayback']['profiles']['100']['entries']
        self.assertEqual([x['bio'] for x in entries],['Old bio','New bio'])
        self.assertNotIn(b'Old bio',Path(new.vault.path).read_bytes())
    def test_dedup_avatar_reversion_and_failed_read(self):
        self.enable();self.at+=1
        a=photo('red');b=photo('blue')
        self.e.record_profile(self.profile(avatar=a),True)
        self.at+=1;self.e.record_profile(self.profile(picture='two',avatar=b),True)
        self.at+=1;self.e.record_profile(self.profile(picture='three'),True)
        self.assertIsNone(wayback.view(self.e.data,'100')['entries'][-1]['image'])
        self.at+=1;self.e.record_profile(self.profile(avatar=a),True)
        self.assertEqual(len(self.e.data['wayback']['images']),2)
        validate_archive(self.e.data)
    def test_unknown_bio_does_not_erase_and_empty_is_observed(self):
        self.enable();self.at+=1;self.e.record_profile(self.profile(None),True)
        self.assertEqual(len(wayback.view(self.e.data,'100')['entries']),1)
        self.at+=1;self.e.record_profile(self.profile(''),True)
        self.assertEqual(wayback.view(self.e.data,'100')['entries'][-1]['bio'],'')
    def test_pause_clear_and_backup_round_trip(self):
        self.enable();self.at+=1;self.e.record_profile(self.profile(avatar=photo('red')),True)
        path=Path(self.home.name)/'test.trackerbackup';self.e.export_backup(path)
        self.e.run('wayback',{'id':'100','enabled':False})
        count=len(wayback.view(self.e.data,'100')['entries'])
        self.at+=1;self.e.record_profile(self.profile('Paused change'),True)
        self.assertEqual(len(wayback.view(self.e.data,'100')['entries']),count)
        self.e.run('wayback',{'id':'100','clear':True})
        self.assertFalse(wayback.view(self.e.data,'100')['enabled']);self.assertEqual(self.e.data['wayback']['images'],{})
        previous=json.loads(protect(self.e.vault.backup.read_bytes(),decrypt=True))
        self.assertEqual(previous['wayback']['images'],{})
        self.assertNotIn('100',previous['wayback']['profiles'])
        self.e.import_backup(path)
        self.assertTrue(wayback.view(self.e.data,'100')['enabled'])
        self.assertEqual(len(self.e.data['wayback']['images']),1)
    def test_entry_limit_preserves_oldest_instead_of_evicting(self):
        self.enable()
        for i in range(wayback.MAX_ENTRIES+3):
            self.at+=1;self.e.record_profile(self.profile(str(i)),True)
        v=wayback.view(self.e.data,'100');self.assertTrue(v['full']);self.assertEqual(len(v['entries']),wayback.MAX_ENTRIES)
        self.assertEqual(v['entries'][0]['bio'],'Old bio')
    def test_tampered_image_rejected_and_old_vault_migrates(self):
        legacy=copy.deepcopy(self.e.data);legacy.pop('wayback');self.e.vault.save(legacy)
        new=Engine(self.home.name);self.assertEqual(new.data['wayback']['profiles'],{})
        self.enable();self.at+=1;self.e.record_profile(self.profile(avatar=photo('red')),True)
        key=next(iter(self.e.data['wayback']['images']));self.e.data['wayback']['images'][key]=photo('blue')
        with self.assertRaises(ValueError):validate_archive(self.e.data)
    def test_disk_failure_rolls_back_preference(self):
        from unittest.mock import patch
        with patch.object(self.e.vault,'save',side_effect=StorageFailure('synthetic')):
            with self.assertRaises(Exception):self.enable()
        self.assertEqual(self.e.data['wayback']['profiles'],{})
    def test_thumbnail_details_are_only_returned_on_archive_requests(self):
        self.enable();self.at+=1;self.e.record_profile(self.profile(avatar=photo('red')),True)
        detail=self.e.run('wayback',{'id':'100'})
        self.assertEqual(len(detail['waybackDetail']['images']),1)
        ordinary=self.e.run('state',{})
        self.assertIsNone(ordinary['waybackDetail'])
        self.assertEqual(ordinary['wayback']['100']['images'],1)
    def test_archives_of_removed_people_cannot_overfill_the_vault(self):
        from network import AppFailure
        self.enable()
        sample=copy.deepcopy(self.e.data['wayback']['profiles']['100'])
        for i in range(1000,1200):self.e.data['wayback']['profiles'][str(i)]=copy.deepcopy(sample)
        p=self.profile();p.update(id='200',username='qa.other');self.e.record_profile(p)
        with self.assertRaises(AppFailure):self.e.run('wayback',{'id':'200','enabled':True})
        validate_archive(self.e.data)
        self.e.run('wayback',{'id':'100','clear':True})
        self.e.run('wayback',{'id':'200','enabled':True})
        self.assertTrue(wayback.view(Engine(self.home.name).data,'200')['enabled'])

if __name__=='__main__':unittest.main()
