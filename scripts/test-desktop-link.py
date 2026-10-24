"""Synthetic offline pairing tests. No saved sessions or Android devices are read."""
import base64
import importlib.util
import json
from pathlib import Path
import unittest
from Cryptodome.Cipher import AES, PKCS1_OAEP
from Cryptodome.Hash import SHA1, SHA256
from Cryptodome.PublicKey import RSA
from Cryptodome.Signature.pss import MGF1

spec = importlib.util.spec_from_file_location('link', Path(__file__).with_name('link-desktop.py'))
link = importlib.util.module_from_spec(spec)
spec.loader.exec_module(link)


class PairingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.key = RSA.generate(3072)

    def request(self):
        return {'version': 1, 'requestId': 'a' * 32, 'expiresAt': 1200,
                'publicKey': base64.b64encode(self.key.public_key().export_key(format='DER')).decode()}

    def decrypt(self, envelope, request_id=None):
        unwrap = PKCS1_OAEP.new(self.key, hashAlgo=SHA256, mgfunc=lambda x, n: MGF1(x, n, SHA1))
        key = unwrap.decrypt(base64.b64decode(envelope['wrappedKey']))
        aes = AES.new(key, AES.MODE_GCM, nonce=base64.b64decode(envelope['iv']))
        aes.update(('InstagramTracker/mobile-link/v1/' + (request_id or envelope['requestId'])).encode())
        data = base64.b64decode(envelope['ciphertext'])
        return json.loads(aes.decrypt_and_verify(data[:-16], data[-16:]))

    def test_envelope_contains_only_authenticated_ciphertext(self):
        data = {'sessionid': 'synthetic-sensitive-value', 'note': 'Türkçe'}
        sealed = link.seal(data, self.request(), 1000)
        self.assertNotIn(b'synthetic-sensitive-value', sealed)
        self.assertEqual(data, self.decrypt(json.loads(sealed)))
        other = link.seal(data, self.request(), 1000)
        self.assertNotEqual(sealed, other)

    def test_binding_and_tampering_are_authenticated(self):
        sealed = json.loads(link.seal({'test': 1}, self.request(), 1000))
        with self.assertRaises(ValueError): self.decrypt(sealed, 'b' * 32)
        value = bytearray(base64.b64decode(sealed['ciphertext'])); value[0] ^= 1
        sealed['ciphertext'] = base64.b64encode(value).decode()
        with self.assertRaises(ValueError): self.decrypt(sealed)

    def test_expired_invalid_or_private_recipient_key_is_rejected(self):
        for changes in [{'expiresAt': 1000}, {'expiresAt': 1800}, {'requestId': '../bad'},
                        {'publicKey': base64.b64encode(self.key.export_key(format='DER')).decode()}]:
            with self.assertRaises(ValueError): link.seal({}, self.request() | changes, 1000)

    def test_payload_projects_only_allowed_session_state_without_network(self):
        class Client:
            user_id = 123
            cookie_dict = {'sessionid': 'synthetic', 'ds_user_id': '123', 'unneeded': 'excluded'}
            def private_headers(self):
                return {'Authorization': 'Bearer IGT:2:synthetic', 'User-Agent': 'Instagram test', 'Secret-Unowned': 'excluded'}
            def get_usdid_settings(self): return None
        data = link.payload(Client(), 1200)
        self.assertEqual('123', data['owner'])
        self.assertEqual({'Authorization', 'User-Agent'}, set(data['headers']))
        self.assertEqual({'sessionid', 'ds_user_id'}, set(data['cookies']))
        self.assertNotIn('password', json.dumps(data)); self.assertEqual('', data['signingKey'])


if __name__ == '__main__': unittest.main()
