#!/usr/bin/env python3
"""Validate the online/offline store catalog before building an APK (no network)."""
import datetime
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {'whackahack.com', 'eeveeexpo.com'}
STATUSES = {'perfecto', 'jugable', 'con_fallas', 'no_abre', 'sin_probar'}


def check_web_url(value):
    if not isinstance(value, str) or any(c.isspace() for c in value):
        raise ValueError(f'Invalid URL: {value!r}')
    url = urlsplit(value)
    if url.scheme != 'https' or not url.hostname or url.username is not None or url.port not in (None, 443):
        raise ValueError(f'Expected a public HTTPS link: {value!r}')
    return url


def validate():
    online = (ROOT / 'catalog/catalog.json').read_bytes()
    if online != (ROOT / 'app/src/main/assets/essentia_catalog.json').read_bytes():
        raise ValueError('Online and bundled catalogs differ; update both copies.')
    catalog = json.loads(online)
    if catalog.get('version') != 2:
        raise ValueError('The media catalog must declare version 2.')
    datetime.date.fromisoformat(catalog['updated'])
    games = catalog['games']
    if not games:
        raise ValueError('The catalog cannot be empty.')
    ids = set()
    media_count = 0
    for game in games:
        identifier = game['id']
        if not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*', identifier) or identifier in ids:
            raise ValueError(f'Invalid or repeated game id: {identifier}')
        ids.add(identifier)
        if not game.get('title', '').strip() or game.get('status') not in STATUSES:
            raise ValueError(f'{identifier}: missing title or invalid compatibility status')
        if game.get('maker') not in ('RPG Maker XP', 'RPG Maker VX', 'RPG Maker VX Ace', 'VX', 'VX Ace'):
            raise ValueError(f'{identifier}: not an RPG Maker game')
        for field in ('page', 'download', 'source_page', 'cover', 'logo', 'trailer'):
            if game.get(field):
                check_web_url(game[field])
        source = game.get('source_page', '')
        if source and check_web_url(source).hostname.removeprefix('www.') not in SOURCES:
            raise ValueError(f'{identifier}: unknown community source')
        screens = game.get('screens', [])
        if not isinstance(screens, list) or len(screens) > 12 or len(set(screens)) != len(screens):
            raise ValueError(f'{identifier}: invalid screenshot gallery')
        for screenshot in screens:
            check_web_url(screenshot)
        if any(game.get(field) for field in ('cover', 'logo', 'screens', 'trailer')):
            if not source or not game.get('media_credit', '').strip():
                raise ValueError(f'{identifier}: media require a source page and creator credit')
            media_count += 1
    print(f'Catalog OK: {len(ids)} unique RPG Maker games, {media_count} credited media listings; offline copy matches.')


if __name__ == '__main__':
    try:
        validate()
    except (ValueError, KeyError, TypeError) as error:
        print(f'Catalog error: {error}', file=sys.stderr)
        sys.exit(1)
