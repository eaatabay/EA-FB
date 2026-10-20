import importlib.util
import json
from pathlib import Path
import sys
import unittest
import zipfile
import hashlib
import zlib
import struct

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
spec=importlib.util.spec_from_file_location('universal_package',ROOT/'scripts/package-universal.py')
pack=importlib.util.module_from_spec(spec);spec.loader.exec_module(pack)

class UniversalPackageTests(unittest.TestCase):
    def setUp(self):
        self.data=b'controlled-package-fixture'
        self.manifest=dict(version=8601,name=pack.NAME,internalName=pack.ID,pluginClassName='com.eafb.EAPlugin')
    def test_exact_identity_bytes_and_new_update_path(self):
        pack.verify_metadata(self.data,self.manifest,[pack.entry(self.data)])
        self.assertIn('/dist-universal-8601-test/',pack.entry(self.data)['url'])
        self.assertNotIn('/dist-v83-test/',pack.entry(self.data)['url'])
    def test_tampering_old_identity_version_or_update_url_rejected(self):
        with self.assertRaises(ValueError):pack.verify_metadata(self.data+b'bad',self.manifest,[pack.entry(self.data)])
        for key,value in [('version',83),('internalName','EA-FB-V83-TEST'),('name','EA-FB V83 TEST')]:
            with self.subTest(key=key),self.assertRaises(ValueError):
                pack.verify_metadata(self.data,dict(self.manifest,**{key:value}),[pack.entry(self.data)])
        feed=pack.entry(self.data);feed['url']='https://example.com/old.cs3'
        with self.assertRaises(ValueError):pack.verify_metadata(self.data,self.manifest,[feed])
    def test_package_guard_rejects_real_ui_instruction_mutation(self):
        path=ROOT/pack.DIST/pack.FILE
        if not path.exists():self.skipTest('Run after packaging; mandatory in packaging CI step')
        with zipfile.ZipFile(path) as z:raw=bytearray(z.read('classes.dex'))
        dex=pack.Dex(raw)
        idx=next(i for i in dex.code if dex.methods[i]=='Lcom/eafb/DetailMetaRow;.renderFragment(Landroidx/fragment/app/Fragment;)V')
        offset=dex.code[idx][0]
        raw[offset+17]^=1
        raw[12:32]=hashlib.sha1(raw[32:]).digest()
        struct.pack_into('<I',raw,8,zlib.adler32(raw[12:])&0xffffffff)
        with self.assertRaisesRegex(ValueError,'Protected V83 runtime method changed'):
            pack.verify_runtime(bytes(raw))

if __name__=='__main__':unittest.main()
