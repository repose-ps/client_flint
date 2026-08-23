# Revision-377 retained and unvalidated state

This file classifies state that may look dead, redundant, or suspicious after deobfuscation but is intentionally retained. It is a preservation aid, not a list of cleanup targets.

## Retained because the supplied revision-377 cache cannot validate the path

- **Widget type 1 (`Widget.TYPE_UNKNOWN`)** — the supplied interface cache contains no type-1 widgets. The decoder fields and `WidgetRuntime.resetAnimations` type-1 recursion are retained because the fixture cannot prove they are unnecessary or incorrect.
- **Missing `headicons,0` widget sprite** — widget 204 references this sprite group, while the supplied media archive has no `headicons.dat`. The existing null-sprite tolerance is retained; no substitute sprite group is inferred.

## Retained because apparent inactivity is not proof of dead state

- **`Widget.type1UnknownValue` and `Widget.type1UnknownEnabled`** — format fields for widget type 1. They remain decoded even though the supplied cache has no records exercising them.
- **`Widget.contentType650InterfaceId`** — recorded while interfaces are decoded but otherwise unused by this client. The assignment is retained because it is authentic decoded state and deleting it provides no behavioral benefit.
- **`param`, `mesanim`, and `mes` config records** — these records are effectively default/empty in the supplied cache. Their emptiness is a property of this fixture, not evidence that alternate revision-377 data could not use the slots.

## Retained historical anomaly

- **Sound track 1592 loop bounds** — its cached loop range is inconsistent with synthesized duration. The historical mixing arithmetic can consequently compute a negative length after disabling looping. This behavior is regression-locked as authentic rather than normalized.

## Exception handling intentionally not changed in Step 8

Several renderer, region, widget, startup, and compatibility paths catch broad exceptions. Step 8 classifies these as behavior-sensitive rather than dead code. Narrowing or removing those catches requires focused malformed-input or renderer evidence and is not a documentation/formatting change.

## Rule for future cleanup

A field, branch, sentinel, or format slot should be deleted only when all supported callers and data paths are understood and tests or cache evidence demonstrate that removal cannot change revision-377 behavior. Apparent inactivity alone is insufficient.
