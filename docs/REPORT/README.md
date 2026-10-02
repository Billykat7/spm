# The written report (brief §6)

This folder collects the report's material as the work happens, so week 8 is assembly, not
archaeology. The `report-evidence` rule says what every pull request adds here.

```text
docs/REPORT/
├── README.md          # this file
├── screenshots/       # NN_<slug>.png, real screenshots of the running app, one per screen and function
│   └── INDEX.md       # | NN | file | caption | issue |   (Issue 33 re-takes the final set)
├── diagrams/          # screen_flow.md, er_diagram.md (Mermaid) and their PNG/SVG exports (Issue 34)
├── CHALLENGES.md      # dated entries: problem, cause, fix (commit), learned (two or three become section 7)
├── UX_CHECKLIST.md    # every screen × every accessibility and consistency check (Issue 30)
└── REPORT.md          # the report itself, in the brief's nine sections (Issue 35); exported to DOCX/PDF for the ZIP
```

## The nine sections, in order

1. Cover page: app name, student name, student number, module name, date
2. Table of contents
3. Introduction: the problem (food waste from forgotten leftovers) and who the app is for
4. System design: the screen-flow diagram and the ER diagram, with the database choice and why (decision 1)
5. Screenshots of every output, each with a caption; real screenshots only, no mockups or stock images
6. Key code snippets: 3 to 5, short, with what each does and why it was written that way (the matcher, a DAO, an Intent factory, an adapter, the normaliser)
7. Challenges and solutions: 2 to 3 real problems, from `CHALLENGES.md`
8. Conclusion and reflection: what was learned, what to improve given more time
9. Reference list, in one consistent style, including a working link to the GitHub repository

The exported `.docx`/`.pdf` is **not committed** (it goes in the submission ZIP, Issue 37); the
Markdown source and the diagrams are.
