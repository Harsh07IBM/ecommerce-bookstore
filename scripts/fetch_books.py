#!/usr/bin/env python3
"""
fetch_books.py — Seed data acquisition for FEAT-01 (Browse Book Catalogue).

WHAT THIS SCRIPT DOES (in plain English):
    1. Asks the Open Library website (a free online book database) for lists
       of books across 8 subjects — fiction, technology, history, business,
       self-help, science, biography, philosophy.
    2. For each book, asks Open Library separately for its description.
    3. Invents a rupee price and a stock quantity for each book — real
       book APIs never provide these because the seller sets them.
    4. Saves everything to `data/seed/books.json`, which the Spring Boot
       app will read at startup to fill its database.

WHY THIS SCRIPT EXISTS (rather than having Spring Boot call Open Library at runtime):
    Fetching book data is a one-time job — we want a fixed catalogue, not
    one that shuffles every time the app restarts. Doing it here, offline,
    means:
      - The Spring Boot app has no runtime dependency on any external website.
      - The catalogue is stable and inspectable (just open books.json).
      - Anyone can re-run this script to refresh the catalogue.

WHY OPEN LIBRARY (not Google Books):
    We originally chose Google Books but its anonymous API rate-limits the
    first request from many IPs (HTTP 429). Open Library has no such
    anonymous quota and is equally free and open. The trade-off: Open
    Library's search results don't include descriptions, so we make a
    second small API call per book to fetch its description.

HOW TO RUN IT:
    Once:  python scripts/fetch_books.py
    Takes about a minute (mostly waiting for the ~120 network calls).

REQUIREMENTS:
    Python 3.10 or newer. No `pip install` needed — everything used here
    ships with Python out of the box.
"""

# ------------------------------------------------------------------
# IMPORTS
# ------------------------------------------------------------------
# These are all "standard library" modules — they come with Python, so
# you don't need to install anything. Explaining what each is for:

import json               # Turn Python dicts/lists into JSON text, and back
import random             # Generate the (fake) prices and stock counts
import sys                # Print errors to stderr, exit with a status code
import time               # Pause between network calls (be polite to the API)
import urllib.error       # Catch HTTP errors like 429 (rate-limit)
import urllib.parse       # Safely build URLs with query parameters
import urllib.request     # Make HTTP GET calls
from pathlib import Path  # Cross-platform file-path handling


# ==================================================================
# CONFIGURATION
#   Values you might want to tune to change the script's behaviour.
# ==================================================================

# The 8 subjects we ask Open Library for. Each subject becomes the
# `category` field on the books it returns. If you want more variety in
# the catalogue, add more subject strings here — but Open Library's
# subject search accepts specific labels, not arbitrary keywords.
SUBJECTS = [
    "fiction",
    "technology",
    "history",
    "business",
    "self-help",
    "science",
    "biography",
    "philosophy",
]

# How many books to ask for per subject. 8 * 15 = 120 raw results, from
# which we expect to keep 90-115 after filtering + deduplication —
# comfortably above the spec's minimum of 50.
BOOKS_PER_SUBJECT = 15

# The two Open Library endpoints we call:
#   /search.json         → returns a page of books matching a subject
#   /works/{key}.json    → returns the description for one book
SEARCH_URL = "https://openlibrary.org/search.json"
WORK_URL_TEMPLATE = "https://openlibrary.org{work_key}.json"

# A "User-Agent" is a bit of text a program sends to a web server to
# identify itself. Some servers rate-limit or block programs that don't
# send one, so we always send a real-looking string.
USER_AGENT = "ecommerce-bookstore-seed/1.0 (learning-project; contact: local-dev)"

# Where the output JSON is written.
#   Path(__file__)          → the path of this Python file
#   .resolve()              → turn it into an absolute path
#   .parent.parent          → step up two folders (from scripts/ to repo root)
#   / "data" / "seed" / ... → then walk down into the seed folder
# Building the path this way means the script works no matter which
# folder you happen to be standing in when you run it.
OUTPUT_PATH = (
    Path(__file__).resolve().parent.parent / "data" / "seed" / "books.json"
)

# Seeding Python's random number generator with a fixed value (42) makes
# the "random" prices and stock counts we generate deterministic — the
# same on every run. That way re-running the script doesn't churn
# `books.json` with meaningless changes, and its git diff stays empty.
random.seed(42)


# ==================================================================
# NETWORKING
#   Helpers for making HTTP calls to Open Library.
# ==================================================================

def http_get_json(url: str, retries: int = 2) -> dict:
    """
    Download the contents of `url` and parse them as JSON.

    If the server responds with "429 Too Many Requests", waits a few
    seconds and tries again — up to `retries` times. Any other HTTP error
    (or 429 after we've used all our retries) is raised so the caller can
    decide what to do about it.
    """
    # urllib.request.Request lets us attach headers to the HTTP request.
    # We attach a User-Agent so Open Library knows who we are.
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})

    for attempt in range(retries + 1):
        try:
            # urlopen sends the request.
            # `with ... as response:` makes sure the network connection is
            # closed cleanly even if an error happens partway through.
            with urllib.request.urlopen(request, timeout=30) as response:
                body = response.read().decode("utf-8")
                return json.loads(body)

        except urllib.error.HTTPError as exc:
            # HTTP status 429 = "you're calling too fast, slow down".
            # We pause and try again, but only a limited number of times.
            if exc.code == 429 and attempt < retries:
                wait = 5 * (attempt + 1)   # 5s, then 10s, then 15s...
                print(f"(429 rate-limited, waiting {wait}s) ",
                      end="", flush=True)
                time.sleep(wait)
                continue
            # Any other HTTP error, or 429 after exhausting retries,
            # bubbles up to the caller.
            raise
    return {}


def fetch_subject_books(subject: str) -> list[dict]:
    """
    Ask Open Library for BOOKS_PER_SUBJECT English-language books in the
    given subject. Return the raw list of "docs" — each doc is a dict
    describing one book.
    """
    # `params` are the ?key=value bits at the end of a URL. Building them
    # as a dict and letting urlencode assemble them handles URL-escaping
    # correctly (spaces, punctuation, etc.).
    params = {
        "subject":  subject,
        "limit":    BOOKS_PER_SUBJECT,
        "language": "eng",
        # `fields` tells the search API to only send the columns we care
        # about — smaller responses are faster and cheaper.
        "fields":   "key,title,author_name,isbn,publisher,"
                    "first_publish_year,number_of_pages_median,cover_i",
    }
    url = f"{SEARCH_URL}?{urllib.parse.urlencode(params)}"
    payload = http_get_json(url)

    # The API returns { "docs": [...books...], "numFound": N, ... }.
    # `.get("docs", [])` returns the docs list if present, or an empty
    # list otherwise — defensive so a malformed response doesn't crash us.
    return payload.get("docs", [])


def fetch_description(work_key: str) -> str | None:
    """
    Ask Open Library for one book's description.

    `work_key` looks like "/works/OL12345W" — Open Library's internal ID
    for the "work" (the abstract book, distinct from a specific edition).

    Returns the description as a string, or None if the API had none or
    the call errored. The caller falls back to a synthesized description
    when None comes back.

    Aside: `str | None` in the return type is Python's way of saying
    "returns either a string or nothing". It's documentation — Python
    doesn't enforce it at runtime.
    """
    url = WORK_URL_TEMPLATE.format(work_key=work_key)
    try:
        payload = http_get_json(url, retries=1)
    except Exception:
        # Any network problem → we simply have no description. That's OK;
        # the caller has a fallback.
        return None

    desc = payload.get("description")

    # Open Library's "description" field is inconsistent:
    #   - Sometimes it's a plain string: "This book is about..."
    #   - Sometimes it's an object:      { "type": "/type/text", "value": "This book..." }
    # We handle both.
    if isinstance(desc, dict):
        desc = desc.get("value")

    if isinstance(desc, str) and desc.strip():
        return desc.strip()
    return None


# ==================================================================
# FIELD EXTRACTION
#   Small helpers that pull specific fields out of raw API responses.
# ==================================================================

def pick_isbn(isbns: list[str]) -> str | None:
    """
    A book usually has multiple ISBNs — one for the hardcover, one for
    the paperback, plus 10-digit and 13-digit variants. We prefer the
    modern 13-digit format and fall back to 10-digit.

    Returns None if we can't find a plausible ISBN in the list.
    """
    # First pass: look for an ISBN-13 (13 digits, all numeric).
    for candidate in isbns:
        clean = (candidate or "").strip().replace("-", "")
        if len(clean) == 13 and clean.isdigit():
            return clean

    # Second pass: fall back to ISBN-10. The last character of an ISBN-10
    # is a check digit that may be the letter "X" (representing 10), not
    # a normal digit — so we allow that specifically.
    for candidate in isbns:
        clean = (candidate or "").strip().replace("-", "").upper()
        if len(clean) == 10:
            first_nine, last = clean[:-1], clean[-1]
            if first_nine.isdigit() and (last.isdigit() or last == "X"):
                return clean
    return None


def build_cover_url(cover_id: int) -> str:
    """
    Open Library serves book covers at a predictable URL pattern.
    Given `cover_id`, the URL is:
        https://covers.openlibrary.org/b/id/<id>-M.jpg
    The "M" at the end selects Medium size. Other options are "S" (small)
    and "L" (large).
    """
    return f"https://covers.openlibrary.org/b/id/{cover_id}-M.jpg"


def build_fallback_description(book: dict) -> str:
    """
    Some books on Open Library have no description at all. Rather than
    leave the field empty (which the DB won't allow), we build a short
    factual description from the fields we DO have.

    It stays honest — no invented claims about plot or style, just the
    facts we know.
    """
    authors = ", ".join(book["authors"][:2])
    parts = [f"A {book['category'].lower()} book by {authors}"]
    if book.get("publisher"):
        parts.append(f", published by {book['publisher']}")
    if book.get("publishedDate"):
        parts.append(f" in {book['publishedDate']}")
    parts.append(".")
    if book.get("pageCount"):
        parts.append(f" {book['pageCount']} pages.")
    return "".join(parts)


def generate_price(page_count: int | None) -> float:
    """
    Invent a rupee price. Real book APIs never expose prices because the
    seller sets them, so we make one up that's roughly proportional to
    the book's length.

    Rules:
      * ₹199 base plus ₹1.50 per page.
      * Capped at ₹899 so no book gets unreasonably expensive.
      * If the page count is unknown, pick a random price in ₹250–₹750.
    """
    if page_count and page_count > 0:
        price = min(199 + page_count * 1.5, 899)
    else:
        price = random.uniform(250, 750)
    # `round(x, 2)` = two decimal places, e.g. 549.5 → 549.5, 549.567 → 549.57
    return round(price, 2)


def generate_stock_quantity() -> int:
    """
    Invent a stock quantity — how many copies are "on hand" in our
    fictional warehouse.

    Rules:
      * 10% of books get set to 0 (out of stock). This exercises the
        Out-of-Stock display path in the UI.
      * The rest get a random count in 5–50 units.
    """
    if random.random() < 0.10:
        return 0
    return random.randint(5, 50)


# ==================================================================
# TRANSFORMATION
#   Turns one raw Open Library "doc" into our own Book shape.
# ==================================================================

def transform_doc(doc: dict, subject: str) -> dict | None:
    """
    Convert one Open Library search result into our Book JSON shape.

    Returns None if any required field is missing — the caller silently
    skips those. "Required" matches what the design's §6 marked as
    NOT NULL: title, at least one author, ISBN, cover.
    """
    isbn     = pick_isbn(doc.get("isbn") or [])
    title    = (doc.get("title") or "").strip()
    authors  = doc.get("author_name") or []
    cover_id = doc.get("cover_i")

    # If any of the four required fields are missing, skip the book.
    if not (isbn and title and authors and cover_id):
        return None

    # `first_publish_year` is an integer like 2008. We store it as a
    # string ("2008") because in the wider design some books have
    # month/day precision and some have year-only — string is the
    # simplest format that handles both.
    year = doc.get("first_publish_year")
    published_date = str(year) if year else None

    # `publisher` is a list — some works were reprinted by many
    # publishers. Take the first one as "the" publisher for our display.
    publishers = doc.get("publisher") or []
    publisher = publishers[0].strip() if publishers else None

    # Page count of 0 is nonsense for a real book — treat it as unknown.
    page_count = doc.get("number_of_pages_median")
    if not page_count or page_count <= 0:
        page_count = None

    # Note: `description` is set to None here — we fill it in later, in
    # a second pass, once we've deduplicated. Doing it now would waste
    # description-fetches on books we're about to throw away as dups.
    return {
        "isbn":          isbn,
        "title":         title,
        # Some Open Library records list 100+ contributors (translators,
        # editors, illustrators, etc.). Cap at 5 so the UI stays sane.
        "authors":       authors[:5],
        "description":   None,
        "coverImageUrl": build_cover_url(cover_id),
        "publisher":     publisher,
        "publishedDate": published_date,
        "pageCount":     page_count,
        "language":      "en",             # we filtered search by language=eng
        "category":      subject.title(),  # "fiction" → "Fiction"
        "price":         generate_price(page_count),
        "stockQuantity": generate_stock_quantity(),
        # Internal field. The underscore prefix is a Python convention
        # meaning "this is private / not really part of the object's
        # public interface". We remove it before writing the final JSON.
        "_work_key":     doc.get("key"),
    }


# ==================================================================
# MAIN
#   The entry point that ties everything together.
# ==================================================================

def main() -> None:
    # ----- Phase A: Fetch books from each subject search -----
    #
    # We collect books into a flat list and a "seen" set so we can
    # deduplicate on-the-fly. A book that shows up in both "fiction" and
    # "self-help" (e.g. an inspirational novel) should be kept once.
    books: list[dict] = []
    seen_isbns: set[str] = set()

    for subject in SUBJECTS:
        print(f"Fetching '{subject}'...", end=" ", flush=True)
        try:
            docs = fetch_subject_books(subject)
        except Exception as exc:
            # If ONE subject fails (e.g. transient network glitch), don't
            # abort — carry on with the remaining subjects. We'll only
            # bail out if ALL subjects failed.
            print(f"failed: {exc}")
            continue

        added = 0
        for doc in docs:
            book = transform_doc(doc, subject)
            if book is None:
                # Skipped because a required field was missing.
                continue
            if book["isbn"] in seen_isbns:
                # Skipped because we already picked up this book from
                # another subject.
                continue
            seen_isbns.add(book["isbn"])
            books.append(book)
            added += 1

        print(f"got {len(docs)} raw, kept {added} new")
        # Half a second between subject searches — polite to the API.
        time.sleep(0.5)

    print(f"\nBooks after transform + dedup: {len(books)}")

    # If EVERY subject failed (network dead, API blocked, etc.), we end
    # up with zero books. Rather than write an empty file, exit with a
    # non-zero status so any calling script or CI notices the failure.
    if not books:
        print("\nERROR: no books collected. Aborting.", file=sys.stderr)
        sys.exit(1)

    # ----- Phase B: Fetch each book's description separately -----
    #
    # Open Library's search results don't include descriptions, so we
    # make one extra call per book to /works/{key}.json. This is the slow
    # part — ~113 calls at 200ms between them = ~23 seconds.
    print(f"\nFetching descriptions from /works ({len(books)} calls)...")
    real_desc_count = 0
    for i, book in enumerate(books, 1):
        # Progress heartbeat every 10 books so the user knows we're alive.
        if i % 10 == 0 or i == len(books):
            print(f"  {i}/{len(books)}")

        # Pull out (and remove) the internal `_work_key` field we stashed
        # during transform. `.pop()` returns the value and removes the key.
        work_key = book.pop("_work_key", None)
        description = fetch_description(work_key) if work_key else None

        if description:
            real_desc_count += 1
        else:
            # No real description available → build a synthesized one.
            description = build_fallback_description(book)
        book["description"] = description

        time.sleep(0.2)   # polite pause between /works calls

    print(f"  {real_desc_count}/{len(books)} books got a real Open Library "
          f"description; the rest use a synthesized fallback.")

    # ----- Phase C: Final tweaks and sanity checks -----

    # Spec §8 criterion 5 requires that at least one book be out of stock.
    # Our 10% random rule usually produces several, but just in case
    # random didn't hit any, force the last book's stock to 0.
    if not any(b["stockQuantity"] == 0 for b in books):
        books[-1]["stockQuantity"] = 0
        print("Forced last book to stockQuantity=0 for spec §8 criterion 5")

    # Print a few useful stats before writing.
    categories = {b["category"] for b in books}
    print(f"\nDistinct categories: {len(categories)}")
    print(f"Categories: {sorted(categories)}")

    if len(books) < 50:
        print(f"\nWARNING: only {len(books)} books (spec requires >= 50)")
    if len(categories) < 5:
        print(f"\nWARNING: only {len(categories)} categories (spec requires >= 5)")

    # ----- Phase D: Write the JSON file -----

    # Make sure the target folder exists (creates data/seed/ if missing).
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)

    # `indent=2`         = pretty-printed with 2-space indentation.
    # `ensure_ascii=False` = keep real Unicode characters like é, ñ, '
    #                       instead of escaping them to é etc.
    with OUTPUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(books, f, indent=2, ensure_ascii=False)
    print(f"\nWrote {len(books)} books to {OUTPUT_PATH}")


# This is a Python idiom you'll see in every serious script. It means:
#   "If this file is being run directly (`python fetch_books.py`),
#    then call main(). But if some OTHER file imports this one, don't
#    run main() automatically — let that other file decide."
# It's what actually kicks off the whole process.
if __name__ == "__main__":
    main()
