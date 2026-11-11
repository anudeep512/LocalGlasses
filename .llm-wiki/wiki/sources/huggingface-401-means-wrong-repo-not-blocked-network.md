---
type: source
title: HF 401 means wrong repo, not a blocked network
status: insight
category: debugging
created: 2026-11-11
updated: 2026-11-11
slug: huggingface-401-means-wrong-repo-not-blocked-network
---

# HF 401 means wrong repo, not a blocked network

Hugging Face answers **HTTP 401 `Invalid username or password`** for a repo that does not exist or is private, not 404. A wrong owner segment (`k2-fsa/` instead of `csukuangfj/`) therefore looks like a network block, and a proxy-level 401 looks the same. Before concluding "the host is blocked":

1. Fetch the site root (`https://huggingface.co/`) — a 200 proves the host is reachable.
2. Fetch a URL you know works, e.g. the app's existing model. LocalGlasses' Gemma URL returned `302 → 200` with `x-linked-size: 2588147712` the whole time.
3. List the real repo names: `https://huggingface.co/api/models?author=<org>&search=<name>`. The sherpa-onnx Whisper repos live under **csukuangfj**, not k2-fsa.
4. For exact bytes without downloading: `x-linked-size` is the file length and `x-linked-etag` is the LFS **sha256** for LFS files (small git-tracked files return a 40-hex git blob id instead, so hash those by downloading).

Cheap verification ladder: `HEAD` headers → download only the small file and hash it → download everything once on device.

Related: [[sources/localglasses-stt-sherpa-onnx-whisper-small]].

*Category: debugging*

---
*Captured: 2026-11-11*

## Related

_Add links to related pages._
