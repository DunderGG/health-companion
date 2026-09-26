# Security Policy

## Supported Versions

Health Companion is in early development and has no releases yet. Only the latest commit on `main` is supported, and fixes land there.

## Reporting a Vulnerability

Please **do not open a public issue** for a security problem.

Report it privately through GitHub instead: go to the repository's **Security** tab and choose **Report a vulnerability**. Please include:

- what the problem is and what an attacker could do with it
- steps to reproduce, or a proof of concept
- the commit you tested against, and the watch or emulator you used

Once the problem is confirmed, a fix is made on `main`, and you are credited in the fix unless you would rather not be.

## Scope

The app keeps all of its data on the watch and has no internet permission, no account and no server. The most relevant issues are therefore on the device itself, for example:

- another app reading or changing the pet's data or your habit history
- exported components (the tile, complications, receivers or services) that can be misused by other apps
- health data (steps, floors, heart rate) ending up somewhere it should not, such as logs or backups
