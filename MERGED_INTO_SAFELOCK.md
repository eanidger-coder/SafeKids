# SafeKids has been merged into SafeLock

SafeKids (this repository) and KiddoLock have been combined into a single unified
parental-control app called **SafeLock**.

## Where is the merged code?

The merged SafeLock 2.0 codebase is hosted on the KiddoLock repository,
on the same branch as this notice:

- Repository: https://github.com/eanidger-coder/KiddoLock
- Branch: `claude/merge-parental-control-apps-J5v8S`
- PR: https://github.com/eanidger-coder/KiddoLock/pull/1

## What got merged?

All three SafeKids detection layers were ported verbatim (with a package rename):

| SafeKids source | SafeLock destination |
|---|---|
| `com.safekids.core.ContentClassifier`  | `com.kiddolock.app.content.core.ContentClassifier`  |
| `com.safekids.core.EscalationTracker`  | `com.kiddolock.app.content.core.EscalationTracker`  |
| `com.safekids.core.ChannelAnalyzer`    | `com.kiddolock.app.content.core.ChannelAnalyzer`    |
| `com.safekids.data.entities.*`         | `com.kiddolock.app.content.entities.*`              |
| `com.safekids.data.dao.*`              | `com.kiddolock.app.content.dao.*`                   |
| `com.safekids.data.SafeKidsDatabase`   | `com.kiddolock.app.content.SafeLockDatabase`        |
| `com.safekids.service.SafeKidsAccessibilityService` | merged into `com.kiddolock.app.services.SafeLockAccessibilityService` |
| `com.safekids.ui.BlockedActivity`      | `com.kiddolock.app.ui.BlockedActivity`              |
| Content filter settings UI             | `com.kiddolock.app.ui.ContentFilterActivity`        |

## UI aesthetic

SafeLock adopts the SafeKids dark-neon design system (cyan `#00F2FF`, purple
`#7000FF`, deep-space `#050810`) across the entire app.

## What happens to this repo?

SafeKids as a standalone project is effectively frozen. Future development
happens on SafeLock.

---

**Language note:** The user prefers Hebrew RTL communication in chat.
