## What and why

<!-- What does this change, and why? Link the issue it fixes, e.g. "Fixes #12". -->

## How it was tested

<!-- Unit tests, and any checks on an emulator or watch (see docs/VERIFICATION.md). Screenshots or a short video help for UI changes. -->

## Checklist

- [ ] `./gradlew test` passes
- [ ] New `.kt` / `.java` files start with the [license header](https://github.com/DunderGG/health-companion/blob/main/CONTRIBUTING.md#license-header)
- [ ] Domain logic lives in `:core:domain`, not in `:wearApp`
- [ ] Non-trivial design choices are recorded in [docs/DESIGN_DECISIONS.md](https://github.com/DunderGG/health-companion/blob/main/docs/DESIGN_DECISIONS.md)
- [ ] If a Room entity changed: `CompanionDatabase.VERSION` is bumped, a migration and migration test are added, and the new schema JSON is committed (see [CONTRIBUTING.md](https://github.com/DunderGG/health-companion/blob/main/CONTRIBUTING.md#changing-the-database-schema))
