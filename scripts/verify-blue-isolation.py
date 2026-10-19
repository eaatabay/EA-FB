#!/usr/bin/env python3
"""Inspect two already-running apps. --arm writes ONLY the chosen blue app's external file."""
import argparse
import json
import re
import subprocess

KNOWN = {'com.lagradost.cloudstream3', 'com.lagradost.cloudstream3.prerelease',
         'com.lagradost.cloudstream3.debug', 'com.lagradost.cloudstream3.prerelease.debug'}
MARKER = 'ea-fb-blue-isolation-8502.json'

def validate(host, other, processes, packages):
    for app in (host, other):
        name, uid = app['package'], app['uid']
        if name not in KNOWN or not 10000 <= uid <= 99999:
            raise ValueError('Unknown package or non-primary application UID')
        if app['dataDir'] not in (f'/data/user/0/{name}', f'/data/data/{name}'):
            raise ValueError('Unexpected private data directory')
        if sorted(p for p, u in packages.items() if u == uid) != [name]:
            raise ValueError('Shared or unverifiable UID')
        if not any(u == uid and n == name for u, pid, n in processes):
            raise ValueError('Both apps must already be running in separate named processes')
    if host['package'] == other['package'] or host['uid'] == other['uid'] or host['dataDir'] == other['dataDir']:
        raise ValueError('Applications are not isolated')
    return dict(schema=1, hostPackage=host['package'], otherPackage=other['package'],
                hostUid=host['uid'], otherUid=other['uid'],
                hostDataDir=host['dataDir'], otherDataDir=other['dataDir'])

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--blue', required=True, choices=sorted(KNOWN))
    p.add_argument('--other', required=True, choices=sorted(KNOWN))
    p.add_argument('--serial', required=True, help='Explicit adb device serial; never select an arbitrary device')
    p.add_argument('--arm', action='store_true', help='Write verified opt-in to BLUE app only; does not uninstall plugins')
    a = p.parse_args()
    def adb(*args, data=None):
        return subprocess.check_output(['adb', '-s', a.serial, *args], input=data, text=True).strip()
    if adb('shell', 'am', 'get-current-user') != '0':
        raise ValueError('This verifier is for Android primary user 0 only')
    foreground = adb('shell', 'dumpsys', 'activity', 'activities')
    if not any(a.blue + '/' in line and ('mResumedActivity' in line or 'topResumedActivity' in line) for line in foreground.splitlines()):
        raise ValueError('Keep the visually confirmed BLUE application in foreground; package does not match --blue')
    raw = adb('shell', 'cmd', 'package', 'list', 'packages', '-U', '--user', '0')
    packages = {m[0]: int(m[1]) for m in re.findall(r'package:([^\s]+)\s+uid:(\d+)', raw)}
    def app(name):
        dump = adb('shell', 'dumpsys', 'package', name)
        directory = re.search(r'^\s*dataDir=(\S+)\s*$', dump, re.M)
        if name not in packages or directory is None:
            raise ValueError('Package UID/dataDir unavailable: ' + name)
        if re.search(r'^\s*sharedUser(?:Id)?=', dump, re.M):
            raise ValueError('Shared-user package rejected')
        return dict(package=name, uid=packages[name], dataDir=directory[1])
    processes = []
    for line in adb('shell', 'ps', '-A', '-o', 'UID,PID,NAME').splitlines()[1:]:
        fields = line.split()
        if len(fields) == 3 and fields[0].isdigit() and fields[1].isdigit():
            processes.append((int(fields[0]), int(fields[1]), fields[2]))
    proof = validate(app(a.blue), app(a.other), processes, packages)
    print(json.dumps(dict(proof=proof, processEvidence=[row for row in processes if row[2] in (a.blue,a.other)],
                         deviceVerified=True, playbackVerified=False), indent=2))
    if a.arm:
        # All variable path components have been checked against the fixed package allowlist.
        target = f'/sdcard/Android/data/{a.blue}/files'
        adb('shell', 'mkdir', '-p', target)
        encoded = json.dumps(proof, separators=(',', ':'))
        adb('shell', f'cat > {target}/{MARKER}', data=encoded)
        actual = json.loads(adb('shell', 'cat', f'{target}/{MARKER}'))
        if actual != proof:
            raise ValueError('Written proof did not round-trip')
        print('ARMED BLUE ONLY. Runtime must still verify OS identities and exclusive EA-FB inventory.')

if __name__ == '__main__':
    main()
