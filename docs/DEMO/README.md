# The video demonstration (brief §5)

A single recording, **5 to 7 minutes**, voice narrating throughout, 1080p, exported as MP4 (H.264),
placed directly inside the submission ZIP. The video file itself is **not committed**.

```text
docs/DEMO/
├── README.md               # this file
├── MATCH_PROOF_STEPS.md    # the exact on-screen steps that make a recipe appear and disappear (Issue 26)
└── VIDEO_SCRIPT.md         # the four parts with timings, what to click, which lines of code to point at (Issue 36)
```

## The four parts

| Part | Time | What it shows |
|------|------|---------------|
| 1. GitHub walkthrough | ~1 min | The commit history scrolled from the first commit, the milestones, a pull request with its description and CI, the Releases page |
| 2. Live app | ~2–3 min | A full Create, Read, Update, Delete cycle on pantry items; add or remove one ingredient and watch a recipe appear in or disappear from Suggested Recipes; close and reopen the app and show the data is still there |
| 3. Concepts at the code | ~2–3 min | Three of: the Activity or Fragment lifecycle in this app; how Room works end to end; how the `RecyclerView` and adapter display data; how Intents move between screens; how the strict-matching algorithm decides |
| 4. Database justification | ~30 s–1 min | Why Room over SQLite, on the device (decision 1) |

`VIDEO_SCRIPT.md` turns this table into a minute-by-minute script with the files and line ranges to
point at, and a recording and compression checklist (`ffmpeg`, length checked with `ffprobe`).
