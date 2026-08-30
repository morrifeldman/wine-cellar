---
name: ship
description: Finish a wine-cellar change — format, bump the app version, commit, push. Use whenever a task is done and the work is about to be committed.
---

# Shipping a wine-cellar change

Run all of it, every time, whatever the change touched:

```bash
scripts/format-and-bump.sh          # jj fix + a new version number; --all to sweep every file
jj commit -m "..."
jj bookmark set main -r @-
jj git push -b main
```

A push to main is a production deploy to Fly.io. Any other bookmark just pushes.

What the script does and why lives in README, under Development Tools.
