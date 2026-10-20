"""Contratos de agendamento, exclusão mútua e integridade das cópias, sem banco real."""
import importlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from datetime import datetime,timedelta,timezone
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
import backup_service as service
import backup_automatico as automatic


class BackupTests(unittest.TestCase):
    def setUp(self):
        (ROOT/'tmp').mkdir(exist_ok=True)
        self.temporary=tempfile.TemporaryDirectory(prefix='backup-tests-',dir=ROOT/'tmp')
        self.path=Path(self.temporary.name).resolve()
        self.override=patch.object(service,'DIRECTORY',self.path);self.override.start()
    def tearDown(self):
        self.override.stop()
        self.assertTrue(self.path.is_relative_to((ROOT/'tmp').resolve()))
        self.temporary.cleanup()
    def test_periodic_due_and_clock_change(self):
        config={'enabled':True,'intervalMinutes':60}
        current=datetime.now(timezone.utc)
        self.assertTrue(automatic.due({},config))
        self.assertFalse(automatic.due({'lastSuccessAt':current.isoformat()},config))
        self.assertTrue(automatic.due({'lastSuccessAt':(current-timedelta(hours=2)).isoformat()},config))
        self.assertTrue(automatic.due({'lastSuccessAt':(current+timedelta(days=1)).isoformat()},config))
        self.assertFalse(automatic.due({},dict(config,enabled=False)))
    def test_lock_prevents_overlapping_backup(self):
        with service.lock():
            with self.assertRaises(RuntimeError):
                with service.lock():pass
    def test_modified_archive_is_refused_before_restore(self):
        target=self.path/'gestao-bar-test.dump';target.write_bytes(b'backup-original')
        service.atomic_json(target.with_suffix('.manifest.json'),{'version':1,'file':target.name,'sha256':service.checksum(target),'rowCounts':{'users':1}})
        target.write_bytes(b'backup-alterado')
        with patch.object(service,'restore_check') as restore:
            with self.assertRaisesRegex(RuntimeError,'alterado'):service.verify_saved(target)
            restore.assert_not_called()
    def test_valid_archive_passes_original_snapshot_counts(self):
        target=self.path/'gestao-bar-test.dump';target.write_bytes(b'backup-original')
        counts={'users':1,'products':3}
        service.atomic_json(target.with_suffix('.manifest.json'),{'version':1,'file':target.name,'sha256':service.checksum(target),'rowCounts':counts})
        with patch.object(service,'connection_settings',return_value={'dbname':'test'}),patch.object(service,'restore_check',return_value={'matchedSnapshot':True}) as restore:
            self.assertTrue(service.verify_saved(target)['matchedSnapshot'])
            self.assertEqual(restore.call_args.kwargs['expected'],counts)
    def test_atomic_status_is_complete_json(self):
        path=self.path/'status.json'
        service.atomic_json(path,{'state':'RUNNING'})
        service.atomic_json(path,{'state':'OK','verified':True})
        self.assertEqual(service.read_json(path),{'state':'OK','verified':True})
        self.assertEqual(list(self.path.glob('*.tmp')),[])


if __name__=='__main__':unittest.main(verbosity=2)
