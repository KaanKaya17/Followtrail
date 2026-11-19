import json
import unittest

import worker_version


class WorkerVersionTests(unittest.TestCase):
    def test_resource_names_the_product_and_package_version(self):
        version = json.loads((worker_version.ROOT / 'package.json').read_text(encoding='utf-8'))['version']
        text = worker_version.render(version)
        self.assertIn("StringStruct('ProductName', 'Followtrail')", text)
        self.assertIn(f"StringStruct('ProductVersion', '{version}')", text)
        self.assertIn('filevers=' + str(tuple(map(int, version.split('.'))) + (0,)), text)

    def test_invalid_versions_are_refused(self):
        for value in ('0.2', '0.2.95-beta', '1.2.70000', "0.2.95'"):
            with self.assertRaises(ValueError):
                worker_version.render(value)


if __name__ == '__main__':
    unittest.main()
