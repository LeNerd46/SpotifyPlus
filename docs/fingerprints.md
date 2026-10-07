# Hook fingerprints

Hooks should name the thing they need once, then give that mapping several independent ways to find it. The resolver evaluates every successful alternative, chooses the highest-scoring valid target, and caches its reflection descriptor. Cache entries are scoped to the Spotify APK's path, size, and modification time and are validated again when loaded.

```java
private static final FingerprintMapping<Class<?>> MENU_MODEL =
        FingerprintMapping.forClass("context-menu.model")
                // Cheap compatibility path for a known Spotify build.
                .fingerprint(Fingerprints.namedClass("9.1.82 name", "p.h2e", 800))
                // Structural fallback for renamed/obfuscated builds.
                .fingerprint(Fingerprints.dexClass("model invariant",
                        FindClass.create().matcher(ClassMatcher.create()
                                .usingStrings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=")
                                .fieldCount(16)), 600,
                        data -> data.getMethodCount() == 2 ? 25 : 0))
                // Reflection validators apply to fresh and cached results.
                .validate(type -> type.getDeclaredFields().length == 16)
                .minimumScore(500)
                .build();

@Override protected void hookSetup() {
    Class<?> model = resolve(MENU_MODEL);
}
```

Use `TargetType.CLASS`, `METHOD`, `CONSTRUCTOR`, or `FIELD`. `Fingerprints` has matching hard-coded and DexKit factories. DexKit queries accept the normal `ClassMatcher`, `MethodMatcher`, and `FieldMatcher` API, so method/field counts, types, modifiers, strings, callers, opcodes, and other DexKit constraints remain available. The optional scorer adds per-result points when one query returns several candidates.

Keep mapping keys globally unique and stable. Prefer two or more independent fingerprints, give highly specific invariants higher scores, and add reflection validators for assumptions the hook would otherwise make. A hard-coded name is useful as one alternative, but should not be the only one for an obfuscated target.
