# CraftTweaker Integration

CraftTweaker is optional. JAWMS Integrations registers this API when CraftTweaker 1.12-4.1.20.715 or newer is installed. There is no upper version limit, but a newer release whose required registration contract has changed is rejected for this integration without changing the other integrations.

All calls require an online logical-server `IPlayer` and run on the server thread. Mana amounts are whole, non-negative points. JAWMS owns bounds, spell reservations, synchronization, creative-player behavior, and final state changes.

## State

```zenscript
val state = mods.jawmsintegrations.Mana.getState(player);
print(state.currentMana ~ " / " ~ state.maximumMana);
```

`ManaState` is immutable and exposes:

- `currentMana` and `maximumMana` as whole mana points.
- `effectiveRegeneration` as mana per second.
- `effectiveLockoutTicks` and `remainingLockoutTicks` as ticks.
- `effectiveLockoutSeconds` and `remainingLockoutSeconds` as seconds.
- `canRegenerateDuringPostCastLockout`.
- `canRegenerateDuringContinuousCasting`.
- `continuousCastActive`.
- `regenerationEligible`.

All properties come from one coherent JAWMS snapshot.

## Mutations

```zenscript
val payment = mods.jawmsintegrations.Mana.consumeMana(player, 25);
if (!payment.successful) {
    print("Payment failed: " ~ payment.failure);
}

mods.jawmsintegrations.Mana.setCurrentMana(player, 50);
mods.jawmsintegrations.Mana.restoreMana(player, 20);
mods.jawmsintegrations.Mana.drainMana(player, 10);
```

- `setCurrentMana(player, target)` sets and clamps current mana through JAWMS.
- `restoreMana(player, amount)` restores up to the missing mana.
- `consumeMana(player, amount)` consumes exactly the requested amount atomically. Use this for affordability and payment; do not perform a separate check followed by a mutation.
- `drainMana(player, maximumAmount)` drains up to the requested maximum and may commit a smaller amount.

Every mutation returns an immutable `ManaMutationResult` with:

- `successful`.
- `failure`: `none`, `insufficient_mana`, or `rejected`.
- `requestedAmount`.
- `actualDelta`: signed change to current mana.
- `actualAmount`: non-negative committed amount.
- `oldState` and `newState`: immutable `ManaState` snapshots.

Invalid arguments, disabled integration state, invalid players, wrong-side calls, unsafe execution context, reservation conflicts, and unsupported JAWMS operations produce a script error instead of a successful result.

## Regeneration Lockout

```zenscript
val appliedTicks = mods.jawmsintegrations.Mana.startRegenerationLockout(player);
```

This starts or restarts the player's post-cast mana-regeneration delay using the effective duration currently accepted by JAWMS and returns that captured duration in ticks. It accepts no custom duration. It does not simulate or charge a spell, change mana or hunger, touch spell payment/reservations, or alter continuous-cast state. An effective duration of zero clears an active countdown or is a no-op and returns zero.

The integration uses the fixed cause `jawmsintegrations:crafttweaker_regeneration_lockout` for the JAWMS state-change event.

When the CraftTweaker integration is disabled, the three script types remain registered so scripts compile, but every call fails with a clear inactive-integration error and changes no mana.
