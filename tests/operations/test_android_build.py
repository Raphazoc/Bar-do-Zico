import sys
from pathlib import Path
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts'))
from compilar_android import validate_address


class AndroidBuildTests(unittest.TestCase):
    def test_accepts_real_local_or_cloud_https_origin(self):
        for value in ('https://192.168.0.8:8443','https://192.168.0.8:18443','https://bar.example'):
            self.assertTrue(validate_address(value),value)

    def test_rejects_placeholders_credentials_paths_and_invalid_ports(self):
        for value in ('https://bar-api.example.azurecontainerapps.io','http://bar.example','https://u:p@bar.example',
                      'https://bar.example:0','https://bar.example:65536','https://bar.example/path',
                      'https://bar.example?','https://bar.example#','https://bar.example?secret=x','https://HOSTNAME_AZURE'):
            self.assertFalse(validate_address(value),value)


if __name__=='__main__':unittest.main(verbosity=2)
