# OLX Scraper – Backend

A lightweight Flask server that exposes the OLX Poland category tree (and
supporting stubs) for the Android app.

## Requirements

- Python 3.10+
- See `requirements.txt`

## Setup

```bash
cd backend
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

The server starts on **http://0.0.0.0:5000**.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Health check |
| `GET` | `/api/olx/categories` | Dynamic OLX Poland category tree (scrapes olx.pl with in-memory cache) |
| `GET` | `/api/olx/categories?refresh=true` | Same, but forces a cache refresh |
| `GET` | `/api/categories?marketplace=olx` | Flat category list (legacy) |
| `GET` | `/api/marketplaces` | List of supported marketplaces |
| `GET` | `/api/listings` | Listings stub |
| `GET` | `/api/search?q=&category=&marketplace=` | Search stub |
| `POST` | `/api/auth/login` | Auth stub |
| `POST` | `/api/auth/register` | Auth stub |

### `/api/olx/categories` response shape

```json
{
  "count": 13,
  "results": [
    {
      "key": "elektronika",
      "label": "Elektronika",
      "path": "/elektronika/",
      "subcategories": [
        {"key": "telefony", "label": "Telefony i akcesoria", "path": "/elektronika/telefony/"},
        {"key": "komputery", "label": "Komputery i laptopy", "path": "/elektronika/komputery/"}
      ]
    }
  ]
}
```

Categories are scraped from `https://www.olx.pl/sitemap/` and cached for
1 hour. If scraping fails (e.g. network unreachable) the server falls back to
a built-in minimal category list so the Android app always receives a valid
response.
