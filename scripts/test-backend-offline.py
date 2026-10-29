"""Run backend tests with both requests/curl real network paths disabled."""
from pathlib import Path
import socket
import sys
import unittest
from unittest.mock import patch

import instagrapi.transports

root = Path(__file__).resolve().parent.parent
suite_path = root / 'interface/desktop/backend'
sys.path.insert(0, str(suite_path))
with patch.object(instagrapi.transports._CurlH2Adapter, 'send', side_effect=AssertionError('Offline test attempted curl HTTP')), \
        patch.object(socket.socket, 'connect', side_effect=AssertionError('Offline test attempted a real connection')), \
        patch.object(socket, 'getaddrinfo', side_effect=AssertionError('Offline test attempted DNS')):
    suite = unittest.defaultTestLoader.discover(str(suite_path), pattern='test_*.py')
    result = unittest.TextTestRunner(verbosity=1).run(suite)
sys.exit(not result.wasSuccessful())
