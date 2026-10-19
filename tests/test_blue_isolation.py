import importlib.util
from pathlib import Path
import unittest
spec=importlib.util.spec_from_file_location('blue',Path(__file__).resolve().parents[1]/'scripts/verify-blue-isolation.py')
blue=importlib.util.module_from_spec(spec);spec.loader.exec_module(blue)

class BlueIsolationTests(unittest.TestCase):
    def setUp(self):
        self.host=dict(package='com.lagradost.cloudstream3',uid=10123,dataDir='/data/user/0/com.lagradost.cloudstream3')
        self.other=dict(package='com.lagradost.cloudstream3.prerelease',uid=10456,dataDir='/data/user/0/com.lagradost.cloudstream3.prerelease')
        self.processes=[(10123,123,self.host['package']),(10456,456,self.other['package'])]
        self.packages={self.host['package']:10123,self.other['package']:10456}
    def test_distinct_running_apps(self):
        self.assertEqual(blue.validate(self.host,self.other,self.processes,self.packages)['hostUid'],10123)
    def test_shared_uid_unknown_package_wrong_data_or_missing_process_rejected(self):
        for field,value in [('uid',10456),('package','other.app'),('dataDir',self.other['dataDir'])]:
            with self.subTest(field=field),self.assertRaises(ValueError):
                blue.validate(dict(self.host,**{field:value}),self.other,self.processes,self.packages)
        with self.assertRaises(ValueError):blue.validate(self.host,self.other,self.processes[:1],self.packages)
        with self.assertRaises(ValueError):blue.validate(self.host,self.other,self.processes,dict(self.packages,alias=10123))
    def test_same_package_rejected(self):
        with self.assertRaises(ValueError):blue.validate(self.host,self.host,self.processes,self.packages)
