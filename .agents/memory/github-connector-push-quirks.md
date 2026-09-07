---
name: GitHub connector push quirks
description: Constraints observed when publishing branches through the connected GitHub API.
---

The connected GitHub API can create a branch from an existing reachable commit and can advance it through the Contents API, but direct ref updates to newly-created Git data commits may return 404. Writes to workflow paths and large HTML files can be blocked by the connector's Cloudflare layer even when smaller repository writes succeed.

**Why:** A StarMail publish attempt repeatedly produced valid Git objects but could not attach them to the requested branch, and Contents API writes to the frontend and workflow paths were rejected.

**How to apply:** Prefer a configured direct Git remote or a GitHub connection with explicit Contents/Actions/ref permissions for complete branch publication. Do not leave encoded staging files or runtime loaders in the repository as a workaround unless the user explicitly accepts that architecture.