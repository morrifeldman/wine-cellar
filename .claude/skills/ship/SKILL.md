---
name: ship
description: Finish a wine-cellar change — format, bump the app version, commit, push. Use whenever a task is done and the work is about to be committed.
---

# Shipping a wine-cellar change

```bash
scripts/format-and-bump.sh          # jj fix + a new version number; --all to sweep every file
jj commit -m "..."
jj bookmark set main -r @-
jj git push -b main
```

Two things the script can't decide for you:

- **Skip the version bump for changes with no app code** — docs, scripts, this
  skill. Run `jj fix` alone instead. Bumping there prompts every user to reload
  for nothing.
- **A push to main is a production deploy** to Fly.io. Any other bookmark just
  pushes.

Details on what the script does and why live in README, under Development Tools.
