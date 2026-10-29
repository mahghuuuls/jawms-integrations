# JAFS validation assets

This directory is reserved for controlled native food definitions and their expected results. It is test input only; files here are not shipping food definitions and are not copied into normal play automatically.

Use the `jafs` development runtime profile for isolated checks, or `all` for coexistence checks. Required JAWMS and Wizardry dependencies remain present; the isolated profile omits Quality Tools, Potion Core, Ancient Spellcraft and CraftTweaker.

Test tools are separate opt-in runtime dependencies:

- `-Penable_agent_test_toolkit=true` selects the checksum-pinned Toolkit JAR from `dev/mods`.
- `-Penable_jawms_developer_tools=true` selects the matching checksum-pinned JAWMS Developer Tools JAR from `dev/mods`.

Both flags default to false. Acquire the pinned release artifacts before enabling them; neither tool belongs in the shipped mod. The build reports a missing file or checksum mismatch explicitly.

Before a profile change, stop this project's game processes and use the managed-artifact cleanup and profile verifiers. A copied optional JAR can otherwise invalidate an absence check.
