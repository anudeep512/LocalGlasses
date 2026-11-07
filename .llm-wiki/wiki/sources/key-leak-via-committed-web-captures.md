---
type: source
title: API key leak via committed wiki web captures
status: insight
category: security
created: 2026-11-07
updated: 2026-11-07
slug: key-leak-via-committed-web-captures
---

# API key leak via committed wiki web captures

A Google API key leak was found in this repository. The key was **not** in app code — it came from a captured web page.

`wiki_capture_source` stores the raw fetched HTML under `.llm-wiki/raw/sources/<ID>/original/source.html`. The AGP 9 doc capture carried an embedded JSON array of Google sample API keys (`AIza…`), and because `.llm-wiki/` was committed, those keys entered git history and were pushed to a **public** repository.

## What fixed it

1. **Purge the path from history**, not just the working tree — a plain delete leaves the blob reachable by commit SHA:
   ```bash
   git stash -u            # filter-branch refuses to run with unstaged changes
   FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch --force \
     --index-filter 'git rm -r --cached --ignore-unmatch .llm-wiki/raw' \
     --prune-empty -- --all
   git stash clear
   git for-each-ref --format='%(refname)' refs/original | xargs -r -n1 git update-ref -d
   git reflog expire --expire=now --all && git gc --prune=now
   git push --force origin main
   ```
2. **Ignore the path permanently**: `.llm-wiki/raw/` in `.gitignore`, so future captures never ship.

## The part a rewrite does not fix

Rewriting history does **not** remove objects from GitHub. After the force push, `main` served 404 for the old path but the orphaned commit was still publicly readable:

```
curl -o /dev/null -w "%{http_code}" \
  https://raw.githubusercontent.com/anudeep512/LocalGlasses/<old-sha>/.llm-wiki/raw/sources/.../source.html
# 200  (still served, still containing the key)
git fetch git@github.com:… LocalGlasses.git <old-sha>   # still fetchable
```

GitHub keeps unreachable commits until it garbage-collects them. Containment steps that actually work:

- **Make the repo private** — stops public serving immediately.
- **Rotate the credential** at the provider; a rewrite cannot un-leak a key that was public.
- Ask GitHub Support to garbage-collect, or wait for their scheduled GC.

## Rule to carry forward

Never commit tool-cached artifacts that embed third-party content: web page captures, saved API responses, scraped docs. Distil them into written notes and ignore the raw cache. Treat any `raw/`-style directory as untrusted.

Also: always confirm repository visibility after the first push. This repo was created public by mistake, which turned a latent leak into a live one.

See [[entities/localglasses-app]].


*Category: security*

---
*Captured: 2026-11-07*

## Related

_Add links to related pages._
