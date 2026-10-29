# JAFS validation assets

This directory is reserved for controlled native food definitions and their expected results. It is test input only; files here are not shipping food definitions and are not copied into normal play automatically.

Use the `jafs` development runtime profile for isolated checks, or `all` for coexistence checks. Required JAWMS and Wizardry dependencies remain present; the isolated profile omits Quality Tools, Potion Core, Ancient Spellcraft and CraftTweaker.

Test tools are separate opt-in runtime dependencies:

- `-Penable_agent_test_toolkit=true` selects the checksum-pinned Toolkit JAR from `dev/mods`.
- `-Penable_jawms_developer_tools=true` selects the matching checksum-pinned JAWMS Developer Tools JAR from `dev/mods`.

Both flags default to false. Acquire the pinned release artifacts before enabling them; neither tool belongs in the shipped mod. The build reports a missing file or checksum mismatch explicitly.

Before a profile change, stop this project's game processes and use the managed-artifact cleanup and profile verifiers. A copied optional JAR can otherwise invalidate an absence check.

`first-profile-foods.json` is the C1-only apple fixture (+20 flat maximum mana for one hour). Stage it as `run/config/jafs/foods.json` with a backup, in a disposable world only. The native parser contract test verifies acceptance; it does not prove Minecraft eating, saved-food reconciliation or saved mana preservation. No Gradle task copies this file into ordinary play automatically.

The `cyc3_jafs_a_*` bundles in the neighboring Toolkit directory belong to that campaign. Run setup once, eat in Survival, and inspect after eating. The before-relog bundle alone sets above-base current mana; neither resume bundle sets or refills it. Save/quit and full process restart are separate boundaries. Keep join automation disabled, keep the same exact development identity, and preserve each launch's result files/log before Gradle reinstalls the Toolkit configuration.
