#!/usr/bin/env python3
"""
Generate manifest.json entries from mods/ directories.

Scans client-mods/mods/ and client-mods/custom-mods/ for .jar files,
extracts metadata from filenames, calculates hashes, and updates
the manifest.json with the discovered mods.
"""

import json
import os
import re
import hashlib
import sys
from pathlib import Path


def parse_mod_filename(filename: str) -> tuple[str, str]:
    """Extract display name and version from mod filename."""
    name = filename.replace('.jar', '')

    special_names = {
        'verkku-title': 'VerkkuCraft Title Screen',
        'voicechat': 'Simple Voice Chat',
        'travelersbackpack': "Traveler's Backpack",
    }

    for key, display_name in special_names.items():
        if name.lower().startswith(key):
            remaining = name[len(key):].lstrip('-_')
            version_match = re.search(r'(\d+\.\d+(?:\.\d+)*)', remaining)
            version = version_match.group(1) if version_match else ''
            return display_name, version

    parts = re.split(r'[-_]', name)
    skip_parts = {'fabric', 'forge', 'quilt', 'mc'}
    version_pattern = re.compile(r'^\d+\.\d+')

    main_parts = []
    version = ''

    for part in parts:
        if version_pattern.match(part):
            version = part
            break
        if part.lower() not in skip_parts and not part.startswith('mc'):
            main_parts.append(part)

    if len(main_parts) == 1 and ' ' in main_parts[0]:
        display_name = main_parts[0]
    else:
        display_name = ' '.join(main_parts).title()

    return display_name, version


def calculate_sha256(filepath: str) -> str:
    """Calculate SHA256 hash of a file."""
    sha256_hash = hashlib.sha256()
    with open(filepath, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()


def scan_mods_directory(folder_path: str, is_custom: bool = False) -> list[dict]:
    """Scan a mods folder and return manifest entries.

    For third-party mods (is_custom=False): scans root-level .jar files only.
    For custom mods (is_custom=True): scans build/libs/*.jar inside each subdirectory.
    Always excludes gradle-wrapper.jar.
    """
    mods = []
    folder = Path(folder_path)

    if not folder.exists():
        print(f"Warning: Directory {folder_path} does not exist")
        return mods

    if is_custom:
        # Custom mods: scan build/libs/ inside each subdirectory
        for mod_dir in sorted(folder.iterdir()):
            if not mod_dir.is_dir():
                continue
            build_libs = mod_dir / "build" / "libs"
            if not build_libs.exists():
                print(f"  Skipping {mod_dir.name} (no build/libs/)")
                continue
            for f in sorted(build_libs.glob("*.jar")):
                if f.name == 'gradle-wrapper.jar' or f.name.endswith('-sources.jar'):
                    continue
                name, version = parse_mod_filename(f.name)
                sha256 = calculate_sha256(str(f))
                size = f.stat().st_size
                mod_entry = {
                    'name': name,
                    'version': version,
                    'fileName': f.name,
                    'sha256': sha256,
                    'fileSize': size,
                    'isCustom': True
                }
                mods.append(mod_entry)
                print(f"  Found custom: {name} v{version} ({f.name})")
    else:
        # Third-party mods: root-level .jar files only
        for f in sorted(folder.glob("*.jar")):
            if f.name == 'gradle-wrapper.jar':
                continue
            name, version = parse_mod_filename(f.name)
            sha256 = calculate_sha256(str(f))
            size = f.stat().st_size
            mod_entry = {
                'name': name,
                'version': version,
                'fileName': f.name,
                'sha256': sha256,
                'fileSize': size,
                'isCustom': False
            }
            mods.append(mod_entry)
            print(f"  Found: {name} v{version} ({f.name})")

    return mods


def scan_plugins_directory(folder_path: str) -> list[dict]:
    """Scan custom-plugins folder and return manifest entries."""
    plugins = []
    folder = Path(folder_path)

    if not folder.exists():
        print(f"Warning: Directory {folder_path} does not exist")
        return plugins

    for plugin_dir in sorted(folder.iterdir()):
        if not plugin_dir.is_dir():
            continue

        build_libs = plugin_dir / "build" / "libs"
        if build_libs.exists():
            for jar in build_libs.glob("*.jar"):
                name = plugin_dir.name
                version_match = re.search(r'-(\d+\.\d+(?:\.\d+)*)', jar.name)
                version = version_match.group(1) if version_match else '1.0.0'
                sha256 = calculate_sha256(str(jar))
                size = jar.stat().st_size

                plugins.append({
                    'name': name,
                    'fileName': jar.name,
                    'version': version,
                    'sha256': sha256,
                    'fileSize': size
                })
                print(f"  Found plugin: {name} v{version} ({jar.name})")

    return plugins


def generate_download_url(filename: str, tag: str, repo: str = "Tkr0n/Vevent") -> str:
    """Generate GitHub release download URL."""
    return f"https://github.com/{repo}/releases/download/{tag}/{filename}"


def update_manifest(
    manifest_path: str,
    client_mods: list[dict],
    plugins: list[dict],
    tag: str,
    repo: str = "Tkr0n/Vevent"
) -> None:
    """Update manifest.json with scanned mods and plugins."""
    with open(manifest_path, 'r', encoding='utf-8') as f:
        manifest = json.load(f)

    existing_mods = {m['fileName']: m for m in manifest.get('clientMods', [])}

    # Remove stale entries that no longer exist on disk
    current_filenames = {m['fileName'] for m in client_mods}
    manifest['clientMods'] = [
        m for m in manifest.get('clientMods', [])
        if m['fileName'] in current_filenames
    ]
    existing_mods = {m['fileName']: m for m in manifest['clientMods']}

    for mod in client_mods:
        if mod['fileName'] in existing_mods:
            existing = existing_mods[mod['fileName']]
            existing['version'] = mod['version']
            existing['sha256'] = mod['sha256']
            existing['fileSize'] = mod['fileSize']
            existing['downloadUrl'] = generate_download_url(mod['fileName'], tag, repo)
        else:
            new_entry = {
                'name': mod['name'],
                'modrinthProjectId': '',
                'version': mod['version'],
                'fileName': mod['fileName'],
                'downloadUrl': generate_download_url(mod['fileName'], tag, repo),
                'sha256': mod['sha256'],
                'fileSize': mod['fileSize']
            }
            manifest.setdefault('clientMods', []).append(new_entry)

    existing_plugins = {p['fileName']: p for p in manifest.get('plugins', [])}

    for plugin in plugins:
        if plugin['fileName'] not in existing_plugins:
            new_entry = {
                'name': plugin['name'],
                'fileName': plugin['fileName'],
                'downloadUrl': generate_download_url(plugin['fileName'], tag, repo),
                'version': plugin['version'],
                'fileSize': plugin['fileSize'],
                'sha256': plugin['sha256']
            }
            manifest.setdefault('plugins', []).append(new_entry)

    with open(manifest_path, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)

    print(f"\nManifest updated: {manifest_path}")


def main():
    tag = os.environ.get('GITHUB_REF_NAME', 'v0.0.0')
    repo = os.environ.get('GITHUB_REPOSITORY', 'Tkr0n/Vevent')

    if len(sys.argv) > 1:
        tag = sys.argv[1]
    if len(sys.argv) > 2:
        repo = sys.argv[2]

    print(f"Generating manifest for {repo} @ {tag}\n")

    print("Scanning client-mods/mods/ (third-party):")
    third_party_mods = scan_mods_directory('client-mods/mods', is_custom=False)

    print("\nScanning client-mods/custom-mods/ (custom):")
    custom_mods = scan_mods_directory('client-mods/custom-mods', is_custom=True)

    print("\nScanning server-plugin/custom-plugins/ (plugins):")
    plugins = scan_plugins_directory('server-plugin/custom-plugins')

    all_mods = third_party_mods + custom_mods

    print(f"\nTotal: {len(all_mods)} client mods, {len(plugins)} plugins")
    update_manifest('manifest.json', all_mods, plugins, tag, repo)


if __name__ == '__main__':
    main()
