import urllib.request
import json

url = "https://api.github.com/repos/google-ar/arcore-android-sdk/releases"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req) as res:
        data = json.loads(res.read().decode("utf-8"))
        for release in data[:3]:
            print("Release:", release['tag_name'])
            for asset in release['assets']:
                print(" -", asset['name'], asset['browser_download_url'])
except Exception as e:
    print("Error:", e)
