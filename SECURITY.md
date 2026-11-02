# Security Policy

## Supported versions

Only the latest release receives security fixes.

## Reporting a vulnerability

Please do not open a public issue for a security problem. Use GitHub's private reporting instead: open the **Security** tab of this repository and choose **Report a vulnerability**.

Include what you found, the steps to reproduce it, and the Android version and device you tested on. You can expect an acknowledgement within a few days.

## Scope notes

- The app has no server component and stores no credentials or API keys.
- The only network access is the optional tag lookup, which is HTTPS-only and limited to `musicbrainz.org`.
- Release signing keys (`*.jks`, `*.keystore`, `keystore.properties`) are never committed. If you find one in this repository's history, please report it.
