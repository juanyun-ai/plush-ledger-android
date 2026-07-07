# Security Policy

## Supported Versions

Security fixes are prioritized for the latest public Android release and the current private development branch. Older versions may receive fixes only when the issue affects user data, authentication, update delivery, or backend access.

## Reporting a Vulnerability

Please do not open a public issue for vulnerabilities, leaked credentials, bypasses, account access problems, or database-policy findings.

Report security issues through the official contact channel shown in the app or product website, and include:

- affected version or commit;
- reproduction steps;
- expected and actual behavior;
- whether user data, authentication, update delivery, or backend permissions may be affected.

## Secret Handling

Do not place service-role keys, API secrets, AI model keys, WeChat AppSecret values, signing materials, admin credentials, or database owner credentials in the app, repository, APK, screenshots, release notes, or public docs.

Supabase publishable keys may appear in client code only when row-level security, edge-function authorization, and storage policies are configured to prevent cross-user access.
