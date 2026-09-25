# KADR Release Story Rules

The release journal is for KADR users, not for build logs. Every public app update must read like the first release story on the site and must exist in both English and Russian.

## Required structure

1. **Localized title** — short, memorable, and about the user-visible theme of the update.
2. **Opening** — one short paragraph explaining why the update matters.
3. **What changed** — one or two short paragraphs about shipped, verified behavior in normal user language.
4. **Optional final line** — one light joke or playful sentence when it fits.
5. **Known limitation** — mention it plainly when it materially affects the user.

Do not put source commits, workflow run IDs, internal branch names, raw commit logs, or other build provenance into the release story shown on the website. Technical provenance belongs in build metadata or internal release records.

## Tone

- Clear before clever.
- Human, concise, and concrete.
- Lightly funny, not stand-up comedy.
- Bugs may be mocked only after they are actually fixed.
- Never claim a feature is fixed, reliable, or shipped unless the released build proves it.
- Avoid internal engineering language unless the user genuinely needs the term.
- Avoid corporate phrases such as “we are thrilled to announce”. KADR is a screenshot app, not a new airport terminal.

## Length

Target roughly 50–100 words per language for an ordinary update. Use more only when the user-facing changes genuinely need it.

## Languages and machine-readable format

Every release story must contain **both** sections, each with its own localized title, in this exact order:

```text
EN

# <English title>

<English opening paragraph>

<English body paragraph(s)>

RU

# <Russian title>

<Russian opening paragraph>

<Russian body paragraph(s)>
```

The website must never substitute English text for a missing Russian section. An incomplete release story should not be rendered in the public journal until both languages are present.
