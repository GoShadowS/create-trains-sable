# Security Policy

## Reporting a vulnerability

Use the repository's private Security Advisory form at
https://github.com/GoShadowS/create-trains-sable/security/advisories/new.
That keeps exploit details out of public issues until a fix exists.

If private advisories are unavailable to you, open an issue with the `security`
label and leave out anything that could be used to attack a server. Do not post
exploit code, world files with private data, or server addresses in a public
issue.

For a sensitive report, a maintainer can also be reached through their GitHub
profile. There is no dedicated security email address yet.

Please include the affected version, the Minecraft/NeoForge/Create/Sable versions,
the impact you observed, and steps to reproduce. A short video or a minimal world
helps, but full log files are usually enough.

## Supported versions

| Version | Security fixes |
| --- | --- |
| 0.1.x | Yes, while the 0.1 line is maintained |
| Older releases | No; upgrade to the current release |

Only the latest release of the maintained line receives fixes. There is no
long-term support branch at this stage of the project.

## What counts as a security issue

- Code execution, file access or network access that a server operator did not
  authorise.
- A packet or saved-data payload that lets a client crash or control the server.
- A reproducible way to corrupt or delete another player's data.

Ordinary crashes, physics glitches, missing textures and world-generation
problems are bugs, not vulnerabilities. Report those as normal issues, following
[SUPPORT.md](SUPPORT.md).

## Response expectations

This is a small project maintained in spare time. Acknowledgement within a week
is realistic; a fix depends on the upstream Create and Sable projects when the
problem lives in their code. There is currently no bug bounty programme.
Reporters who want credit are named in the release notes unless they ask
otherwise.
