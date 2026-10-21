"""A parada nunca deve atingir um processo de outro projeto."""
import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts'))
from executar_bar import stop_managed_service, ROOT


class StartupTests(unittest.TestCase):
    def test_stop_checks_pid_and_project_before_terminating(self):
        with patch('executar_bar.subprocess.run') as run:
            stop_managed_service({'pid':12345})
        arguments=run.call_args.args[0]
        command=arguments[-1]
        self.assertIn('ProcessId = 12345',command)
        self.assertIn('BAR_EXPECTED_RUNTIME',command)
        self.assertIn('bar-api-0.1.0.jar',command)
        self.assertEqual(run.call_args.kwargs['env']['BAR_EXPECTED_RUNTIME'],str((ROOT/'.tools/runtime-api').resolve()))
        self.assertTrue(run.call_args.kwargs['check'])

    def test_stop_refuses_non_numeric_pid_without_executing_commands(self):
        with patch('executar_bar.subprocess.run') as run:
            with self.assertRaises(ValueError):stop_managed_service({'pid':'123; Stop-Process -Name java'})
        run.assert_not_called()


if __name__=='__main__':unittest.main(verbosity=2)
