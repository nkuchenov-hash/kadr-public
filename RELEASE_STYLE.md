# KADR Release Story Rules

Every public KADR app release gets a short human-readable story in English and Russian.

## Required structure

1. **Title** — short and memorable; it should describe the theme of the update, not repeat the version number.
2. **Opening** — 1–2 sentences explaining why the update matters to a normal user.
3. **What actually changed** — only shipped and verified changes. Use concrete behavior, not internal commit language.
4. **Known limitation, when relevant** — if an important part is still preview/limited, say so plainly.
5. **Build provenance** — version, private source commit, and Integrated Release run.

## Tone

- Clear before clever.
- Lightly funny, not stand-up comedy.
- One joke or playful line is usually enough.
- Bugs may be mocked only after they are actually fixed.
- Never claim a feature is fixed, reliable, or shipped unless the released build proves it.
- No raw commit-log dumps as release notes.
- Avoid corporate phrases such as “we are thrilled to announce”. KADR is a screenshot app, not a new airport terminal.

## Length

Target roughly 80–180 words per language for ordinary updates. Larger releases may be longer if the user-facing changes genuinely need it.

## Languages

Every release story must contain both sections in this order:

```text
EN

<English release story>

RU

<Russian release story>
```
