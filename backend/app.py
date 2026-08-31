"""
OLX Scraper backend server.

Provides:
  GET /api/health
  GET /api/marketplaces
  GET /api/categories?marketplace=<key>
  GET /api/olx/categories          <-- dynamic OLX Poland categories from olx.pl
  GET /api/listings
  GET /api/listings/<id>
  GET /api/search?q=&category=&marketplace=
  POST /api/auth/login
  POST /api/auth/register
"""

import re
import time
import logging
import threading
from urllib.parse import urlparse

import requests
from bs4 import BeautifulSoup
from flask import Flask, jsonify, request
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# OLX categories scraper
# ---------------------------------------------------------------------------

_CATEGORIES_CACHE: dict = {"data": None, "fetched_at": 0}
_CACHE_TTL_SECONDS = 3600  # 1 hour
_CACHE_LOCK = threading.Lock()

_OLX_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Linux; Android 10; Mobile) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0 Mobile Safari/537.36"
    ),
    "Accept-Language": "pl-PL,pl;q=0.9",
}


def _to_olx_path(href: str) -> str | None:
    """
    Return the path component of an OLX href, or None if it is not a valid
    OLX category link.

    Accepts either:
      - absolute URLs: https://www.olx.pl/elektronika/
      - root-relative paths: /elektronika/
    Returns None for links pointing to other domains or non-category paths.
    """
    parsed = urlparse(href)
    # Absolute URL – ensure it is for the expected OLX host
    if parsed.scheme and parsed.netloc and parsed.netloc != "www.olx.pl":
        return None
    path = parsed.path or "/"
    if not path.startswith("/"):
        return None
    return path


def _slug(text: str) -> str:
    """Convert a label to a URL-friendly key."""
    slug = text.strip().lower()
    slug = re.sub(r"[ąàá]", "a", slug)
    slug = re.sub(r"ć", "c", slug)
    slug = re.sub(r"ę", "e", slug)
    slug = re.sub(r"ł", "l", slug)
    slug = re.sub(r"ń", "n", slug)
    slug = re.sub(r"ó", "o", slug)
    slug = re.sub(r"ś", "s", slug)
    slug = re.sub(r"[źż]", "z", slug)
    slug = re.sub(r"[^a-z0-9]+", "-", slug)
    return slug.strip("-")


def _parse_categories_from_sitemap() -> list[dict]:
    """
    Fetch https://www.olx.pl/sitemap/ and extract the category tree.

    The sitemap page contains an unordered list of top-level category links,
    each of which may contain nested lists of sub-category links.

    Returns a list of category dicts:
        {
            "key": str,
            "label": str,
            "path": str,          # relative URL path, e.g. /elektronika/
            "subcategories": [
                {"key": str, "label": str, "path": str}
            ]
        }
    """
    url = "https://www.olx.pl/sitemap/"
    try:
        resp = requests.get(url, headers=_OLX_HEADERS, timeout=10)
        resp.raise_for_status()
    except requests.RequestException as exc:
        logger.error("Failed to fetch OLX sitemap: %s", exc)
        return []

    soup = BeautifulSoup(resp.text, "html.parser")

    categories: list[dict] = []

    # The sitemap page wraps categories in <ul> lists inside a content div.
    # We look for all top-level <li> elements that contain a direct <a> link
    # pointing to an olx.pl category path (e.g. /elektronika/).
    for li in soup.select("ul > li"):
        # Skip nested <li> elements - process only top-level ones
        if li.find_parent("li"):
            continue

        a_tag = li.find("a", recursive=False)
        if a_tag is None:
            # Try one level deeper (some pages wrap with a div)
            a_tag = li.find("a")
        if a_tag is None:
            continue

        href = a_tag.get("href", "")
        label = a_tag.get_text(strip=True)

        if not label or not href:
            continue

        # Normalise the href to a root-relative path
        path = _to_olx_path(href)
        if path is None:
            continue

        # Skip the home page and non-category links
        if path in ("/", "/sitemap/") or len(path) <= 1:
            continue

        # Build subcategories from nested <li> elements
        subcategories: list[dict] = []
        nested_ul = li.find("ul")
        if nested_ul:
            for sub_li in nested_ul.find_all("li", recursive=False):
                sub_a = sub_li.find("a")
                if not sub_a:
                    continue
                sub_href = sub_a.get("href", "")
                sub_label = sub_a.get_text(strip=True)
                if not sub_label or not sub_href:
                    continue
                sub_path = _to_olx_path(sub_href)
                if sub_path is None:
                    continue
                subcategories.append(
                    {
                        "key": _slug(sub_label),
                        "label": sub_label,
                        "path": sub_path,
                    }
                )

        categories.append(
            {
                "key": _slug(label),
                "label": label,
                "path": path,
                "subcategories": subcategories,
            }
        )

    return categories


def _get_olx_categories(force_refresh: bool = False) -> list[dict]:
    """Return cached or freshly fetched OLX Poland categories."""
    now = time.time()
    with _CACHE_LOCK:
        if (
            not force_refresh
            and _CATEGORIES_CACHE["data"] is not None
            and now - _CATEGORIES_CACHE["fetched_at"] < _CACHE_TTL_SECONDS
        ):
            return _CATEGORIES_CACHE["data"]

    # Perform the potentially slow HTTP request outside the lock so other
    # threads are not blocked while waiting for the network.
    categories = _parse_categories_from_sitemap()

    # Fall back to a minimal hard-coded list if scraping failed
    if not categories:
        logger.warning("Scraping returned no categories; using built-in fallback.")
        categories = _builtin_fallback_categories()

    with _CACHE_LOCK:
        _CATEGORIES_CACHE["data"] = categories
        _CATEGORIES_CACHE["fetched_at"] = time.time()

    return categories


def _builtin_fallback_categories() -> list[dict]:
    """Minimal hard-coded OLX Poland category list used as a last-resort fallback."""
    return [
        {"key": "elektronika", "label": "Elektronika", "path": "/elektronika/", "subcategories": [
            {"key": "telefony", "label": "Telefony i akcesoria", "path": "/elektronika/telefony/"},
            {"key": "komputery", "label": "Komputery i laptopy", "path": "/elektronika/komputery/"},
            {"key": "rtv", "label": "RTV i AGD", "path": "/elektronika/rtv-agd/"},
        ]},
        {"key": "motoryzacja", "label": "Motoryzacja", "path": "/motoryzacja/", "subcategories": [
            {"key": "samochody", "label": "Samochody osobowe", "path": "/motoryzacja/samochody/"},
            {"key": "motocykle", "label": "Motocykle i skutery", "path": "/motoryzacja/motocykle-i-quady/"},
        ]},
        {"key": "nieruchomosci", "label": "Nieruchomości", "path": "/nieruchomosci/", "subcategories": [
            {"key": "mieszkania-sprzedaz", "label": "Mieszkania – sprzedaż", "path": "/nieruchomosci/mieszkania/sprzedaz/"},
            {"key": "mieszkania-wynajem", "label": "Mieszkania – wynajem", "path": "/nieruchomosci/mieszkania/wynajem/"},
        ]},
        {"key": "dom-i-ogrod", "label": "Dom i Ogród", "path": "/dom-ogrod/", "subcategories": []},
        {"key": "moda", "label": "Moda", "path": "/moda/", "subcategories": []},
        {"key": "dzieci", "label": "Dla Dzieci", "path": "/dla-dzieci/", "subcategories": []},
        {"key": "sport", "label": "Sport i Hobby", "path": "/sport-hobby/", "subcategories": []},
        {"key": "muzyka", "label": "Muzyka i Edukacja", "path": "/muzyka-edukacja/", "subcategories": []},
        {"key": "zwierzeta", "label": "Zwierzęta", "path": "/zwierzeta/", "subcategories": []},
        {"key": "rolnictwo", "label": "Rolnictwo", "path": "/rolnictwo/", "subcategories": []},
        {"key": "praca", "label": "Praca", "path": "/praca/", "subcategories": []},
        {"key": "uslugi", "label": "Usługi", "path": "/uslugi/", "subcategories": []},
        {"key": "inne", "label": "Inne", "path": "/inne/", "subcategories": []},
    ]


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.route("/api/health")
def health():
    return jsonify({"status": "ok", "service": "olx-scraper-backend"})


@app.route("/api/olx/categories")
def olx_categories():
    """
    Dynamically fetch and return the OLX Poland category tree.

    Query params:
      refresh=true   – force a cache refresh

    Response shape:
        {
            "count": <int>,
            "results": [
                {
                    "key": <str>,
                    "label": <str>,
                    "path": <str>,
                    "subcategories": [
                        {"key": <str>, "label": <str>, "path": <str>}
                    ]
                }
            ]
        }
    """
    force = request.args.get("refresh", "").lower() == "true"
    categories = _get_olx_categories(force_refresh=force)
    return jsonify({"count": len(categories), "results": categories})


@app.route("/api/marketplaces")
def marketplaces():
    results = [
        {"key": "olx", "name": "OLX"},
        {"key": "otodom", "name": "Otodom"},
    ]
    return jsonify({"count": len(results), "results": results})


@app.route("/api/categories")
def categories():
    """
    Legacy endpoint – returns flat category list for a given marketplace.
    For marketplace=olx the categories are fetched dynamically from olx.pl.
    """
    marketplace = request.args.get("marketplace", "olx")
    if marketplace == "olx":
        raw = _get_olx_categories()
        results = [{"key": c["key"], "label": c["label"], "path": c["path"]} for c in raw]
    else:
        results = []
    return jsonify({"count": len(results), "results": results})


@app.route("/api/listings")
def listings():
    return jsonify({"count": 0, "results": []})


@app.route("/api/listings/<int:listing_id>")
def listing(listing_id: int):
    return jsonify({"error": "not found"}), 404


@app.route("/api/search")
def search():
    return jsonify({"count": 0, "results": []})


@app.route("/api/auth/login", methods=["POST"])
def login():
    return jsonify({"error": "not implemented"}), 501


@app.route("/api/auth/register", methods=["POST"])
def register():
    return jsonify({"error": "not implemented"}), 501


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
