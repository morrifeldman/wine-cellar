---
name: ship
description: Use when a change is finished and ready to commit.
---

# Shipping a wine-cellar change

Run all of it, every time, whatever the change touched:

```bash
scripts/format-and-bump.sh          # jj fix + a new version number; --all to sweep every file
jj commit -m "..."
jj bookmark advance                 # moves the closest bookmark to the new commit
jj git push
```

A push to main is a production deploy to Fly.io. Any other bookmark just pushes.

What the script does and why lives in README, under Development Tools.
