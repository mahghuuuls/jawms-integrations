# JAFS validation assets

The `lifecycle-*` files and `cyc3_jafs_b_*` bundles belong only to the lifecycle campaign. They are inactive inputs, never automatically installed as foods. The baseline has all15 operations, signed opposite amounts, independent percent foods,30s expiry and two0.5 maximum/5% maximum/-60% delay foods. Reload files preserve native IDs; four skip files exercise native omission of invalid modifiers while valid fields apply. The wrong-shape file reaches whole-file reload failure/retention. The overflow profile contains native-valid2147483648 and two1e308 flat inputs, reaching integer overflow and a non-finite sum while keeping independent valid percentages. The campaign map names which file to copy before each reload.

The development-only observer is built by `jafsValidationJar` into `build/validation`. `runClient -Pintegration_runtime_profile=jafs -Penable_jafs_validation_probe=true` installs it; the ordinary false/absent flag removes its exact managed JAR on the next development-client preparation. It never enters the shipping JAR. `/jafs-probe <0..12000>` bounds its public-read callback/END-tick records;0 stops it. Its synthetic property has no authored modifier or gameplay effect. Final teardown removes the managed JAR and restores native/Toolkit/client settings from backups.

This directory is reserved for controlled native food definitions and their expected results. It is test input only; files here are not shipping food definitions and are not copied into normal play automatically.

Use the `jafs` development runtime profile for isolated checks, or `all` for coexistence checks. Required JAWMS and Wizardry dependencies remain present; the isolated profile omits Quality Tools, Potion Core, Ancient Spellcraft and CraftTweaker.

Test tools are separate opt-in runtime dependencies:

- `-Penable_agent_test_toolkit=true` selects the checksum-pinned Toolkit JAR from `dev/mods`.
- `-Penable_jawms_developer_tools=true` selects the matching checksum-pinned JAWMS Developer Tools JAR from `dev/mods`.

Both flags default to false. Acquire the pinned release artifacts before enabling them; neither tool belongs in the shipped mod. The build reports a missing file or checksum mismatch explicitly.

Before a profile change, stop this project's game processes and use the managed-artifact cleanup and profile verifiers. A copied optional JAR can otherwise invalidate an absence check.

`first-profile-foods.json` is the C1-only apple fixture (+20 flat maximum mana for one hour). Stage it as `run/config/jafs/foods.json` with a backup, in a disposable world only. The native parser contract test verifies acceptance; it does not prove Minecraft eating, saved-food reconciliation or saved mana preservation. No Gradle task copies this file into ordinary play automatically.

The `cyc3_jafs_a_*` bundles in the neighboring Toolkit directory belong to that campaign. Run setup once, eat in Survival, and inspect after eating. The before-relog bundle alone sets above-base current mana; neither resume bundle sets or refills it. Save/quit and full process restart are separate boundaries. Keep join automation disabled, keep the same exact development identity, and preserve each launch's result files/log before Gradle reinstalls the Toolkit configuration.
