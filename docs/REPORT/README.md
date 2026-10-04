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

`REPORT.md` is a draft written from the issues and the recorded decisions; every paragraph is to be
rewritten in the author's own words before it is exported (brief §9).

## Referencing style

The reference list (section 9) uses the **Harvard** style throughout: author (year) *title*.
Available at: URL (Accessed: date), with in-text citations such as (Android Developers, n.d.a).
Undated web pages are `n.d.`, and several from one author are told apart by `a`, `b`, `c` in the order
they are listed.

## Building the DOCX and the PDF

The student number is kept out of git: `REPORT.md` holds the placeholder `<StudentNumber>`, and the
export substitutes it from `.submission.env`, which `.gitignore` excludes. Once, copy the committed
template and fill in the number:

```sh
cp .submission.env.example .submission.env   # then set STUDENT_NUMBER=... in it
```

Then, from the repository root (needs `pandoc` 3.x: `brew install pandoc`):

```sh
set -a; . ./.submission.env; set +a
mkdir -p build/report
sed "s/<StudentNumber>/$STUDENT_NUMBER/g" docs/REPORT/REPORT.md \
  | pandoc -f gfm+attributes+raw_attribute --resource-path=docs/REPORT \
      -o "build/report/${STUDENT_NUMBER}_${SURNAME}_MobileAppDev700_Assignment.docx"
```

- `+attributes` reads the `{width=40%}` after each image, so a phone screenshot fits on a page;
  `+raw_attribute` passes the two `{=openxml}` blocks through: the page breaks after the cover page
  and the table of contents, and the table-of-contents field itself.
- There is no `--toc`: pandoc puts its table before the first heading, which is ahead of the cover
  page. The field under *Table of contents* is the same Word `TOC` field, placed where §6 wants it,
  for headings of levels 1 and 2. Word offers to update it when the file opens (or press F9 on it);
  in LibreOffice use *Tools > Update > Update All*. Save the DOCX after updating.

The PDF is made from that updated DOCX, so the cover page stays first and the table keeps its page
numbers:

```sh
soffice --headless --convert-to pdf --outdir build/report \
  "build/report/${STUDENT_NUMBER}_${SURNAME}_MobileAppDev700_Assignment.docx"
```

or *File > Save As > PDF* in Word. With a LaTeX engine installed (`brew install --cask mactex`), pandoc
can write the PDF directly with `--pdf-engine=xelatex --toc -o "build/report/${STUDENT_NUMBER}_${SURNAME}_MobileAppDev700_Assignment.pdf"`
in place of the `-o` above, but the `{=openxml}` blocks are dropped and the table lands before the
cover page, so the DOCX route is the one to submit.

Both files land in `build/report/`, which `.gitignore` already excludes: the DOCX and the PDF are
**never committed**, and `git status` stays clean after an export. They go into the ZIP through
Issue 37.

Checks before exporting:

```sh
grep -n '^# ' docs/REPORT/REPORT.md                      # exactly nine headings
pandoc -f gfm -t plain docs/REPORT/REPORT.md | wc -w     # 2,500 to 4,000 (code and captions included)
codespell docs/REPORT/REPORT.md                          # nothing; British spelling throughout
git grep -n '<StudentNumber>'                            # the cover page and this README only
```
