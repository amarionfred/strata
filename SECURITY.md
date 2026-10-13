# Security model

The native preview runs as an ordinary Android app. Its terminal jobs run under Termux's Android identity and permissions. Shizuku grants only the privileges of the service that launched it; an ADB-started service is Android's shell user, not device root. Neither application installs a kernel, unlocks the bootloader or obtains root through configuration files.

The original launcher has a separate optional PRoot runtime. PRoot's root identity is not Android root, and its filesystem bindings are not a security boundary. Guest code can modify the guest home, packages and bound launcher data. A shared or store OWL widget is executable code: it can run guest commands, make network requests and read Android sources for which the launcher has permission. Regex risk hints and agent reviews do not establish safety. Per-capability controls and approval tied to file contents are not implemented yet; edits to an installed widget can change what executes.

The former `bwrap` shim discarded namespace restrictions and executed the payload. This fork instead returns exit code 78 without running it. Software requiring Bubblewrap isolation, including a coding agent's sandbox mode, remains unsupported in the legacy runtime. This also affects applications that previously relied on the permissive shim. Disabling the caller's sandbox is not a fix supplied by this fork. Native Termux compatibility does not imply support for a tool's Linux namespace sandbox.

Private Space on Android 15+ requires the default-home role and hidden-profile permission. Private apps are confined to their separate tab, removed when locked or while a lock is pending, and excluded from home shortcuts, global search, usage history and notification feeds. Unknown profile types are hidden. These rules have host tests and source checks; physical work/private-profile verification is still required.

GitHub workflows use pinned action commits with read access by default; only the release job can write releases and provenance. Dependabot configuration covers Gradle and workflow dependencies. Hosting-side protection rules, private vulnerability reporting and security alerts cannot be enabled by committing a YAML file and are not claimed as configured.

## Reporting

Do not publish credentials, private notifications, exploit payloads targeting other users or identifying device logs in a public issue. This local fork has no published remote or verified private reporting channel yet. Share a redacted description with the fork owner through an existing private channel and agree how to exchange reproductions. The upstream repository has separate maintainers; do not assume its reporting settings apply to this fork.

The original runtime's downloads and shared-code trust require further hardening. The native preview does not download that Arch rootfs or install the permissive PRoot shim.
