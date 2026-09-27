# Reviews

Point-in-time reviews of the Thriveling codebase. Each review is its own dated file, so [ARCHITECTURE.md](../ARCHITECTURE.md) stays a description of the current system rather than an accumulating list of findings.

| Date | Review | Findings | Status |
| :--- | :--- | :--- | :--- |
| 2026-09-26 | [Architecture review](2026-09-26-architecture-review.md) | AR-1 … AR-8 | ✅ All resolved |

## Adding a review

1. **File**: create `YYYY-MM-DD-<topic>-review.md` in this folder, and add a row to the table above.
2. **IDs**: give findings a prefix that is unique across all reviews, e.g. `AR2-1` for a second architecture review or `SR-1` for a security review, so that references in code and docs stay unambiguous.
3. **Each finding**: record *Where*, *Problem*, *Impact* and *Target design*. When a finding is fixed, add a **Resolution** line; keep the original problem text as history.
4. **Planning and decisions**: schedule the fixes in [ROADMAP.md](../ROADMAP.md). Log non-trivial choices made while fixing them in [DESIGN_DECISIONS.md](../DESIGN_DECISIONS.md).
5. **Inline notes**: put short ⚠ notes in ARCHITECTURE.md only where a finding affects how the current design should be read, and remove them once resolved.
