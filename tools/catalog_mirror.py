"""Builds the catalog mirror: a compact copy of the Radio Browser catalog for networks that cut it off.

Usage: python catalog_mirror.py <stations.json> <out.tsv.gz>
  stations.json: https://de1.api.radio-browser.info/json/stations?hidebroken=true&limit=100000

Output: gzip, UTF-8, one line per station, fields separated by tabs (tabs and new lines removed from values).
The first line is a header: #libreradio-catalog <version> <time UTC> <count>
Fields, in order: see FIELDS. The app (CatalogMirror.kt) reads the same order.
"""
import gzip
import json
import sys
import time

VERSION = 1
FIELDS = ['stationuuid', 'name', 'url', 'url_resolved', 'homepage', 'favicon', 'tags', 'country',
          'countrycode', 'codec', 'bitrate', 'lastcheckok', 'lastcheckoktime', 'lastchangetime', 'clickcount']


def clean(value):
    if value is None:
        return ''
    return ' '.join(str(value).replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').split())


def main(src, out):
    with open(src, encoding='utf-8') as f:
        stations = json.load(f)
    rows = []
    for s in stations:
        if not s.get('url') or s.get('lastcheckok') != 1:
            continue
        rows.append('\t'.join(clean(s.get(k)) for k in FIELDS))
    if len(rows) < 10000:
        sys.exit(f'only {len(rows)} stations: the catalog answered with too little, not publishing')
    header = f'#libreradio-catalog {VERSION} {time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())} {len(rows)}'
    with gzip.open(out, 'wt', encoding='utf-8', compresslevel=9) as f:
        f.write(header + '\n')
        f.write('\n'.join(rows))
        f.write('\n')
    print(header)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
