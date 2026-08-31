import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import {
    transformAsync,
    transformFromAstSync,
} from "@babel/core";
import generatorModule from "@babel/generator";
import transformTypeScriptModule from "@babel/plugin-transform-typescript";

const generate = generatorModule.default ?? generatorModule;
const transformTypeScript = transformTypeScriptModule.default ?? transformTypeScriptModule;

export const SPOTIFYPLUS_WORKLET_VERSION = 2;
export const SPOTIFYPLUS_WORKLET_BUNDLE_MARKER = "globalThis.__spotifyplus_worklet_bundle__ = 2;";
export const SPOTIFYPLUS_ANIMATED_MODULE = "spotifyplus/react/reanimated";
export const SPOTIFYPLUS_GESTURE_MODULE = "spotifyplus/react/Gesture";
const LEGACY_SPOTIFYPLUS_ANIMATED_MODULE = "spotifyplus/react/Animated";

function isSpotifyPlusAnimatedModule(moduleName) {
    return moduleName === SPOTIFYPLUS_ANIMATED_MODULE
        || moduleName === LEGACY_SPOTIFYPLUS_ANIMATED_MODULE;
}

const AUTO_WORKLET_ARGUMENTS = new Map([
    ["createAnimatedPropAdapter", [0]],
    ["createWorkletRuntime", [1]],
    ["executeOnUIRuntimeSync", [0]],
    ["onBegin", [0]],
    ["onChange", [0]],
    ["onEnd", [0]],
    ["onFinalize", [0]],
    ["onStart", [0]],
    ["onTouchesCancelled", [0]],
    ["onTouchesDown", [0]],
    ["onTouchesMove", [0]],
    ["onTouchesUp", [0]],
    ["onUpdate", [0]],
    ["runOnRuntime", [1]],
    ["runOnRuntimeAsync", [1]],
    ["runOnRuntimeSync", [1]],
    ["runOnUI", [0]],
    ["runOnUIAsync", [0]],
    ["runOnUISync", [0]],
    ["scheduleOnRuntime", [1]],
    ["scheduleOnUI", [0]],
    ["useAnimatedProps", [0]],
    ["useAnimatedReaction", [0, 1]],
    ["useAnimatedGestureHandler", [0]],
    ["useAnimatedScrollHandler", [0]],
    ["useAnimatedStyle", [0]],
    ["useDerivedValue", [0]],
    ["useEvent", [0]],
    ["useFrameCallback", [0]],
    ["useWorkletCallback", [0]],
    ["withDecay", [1]],
    ["withRepeat", [3]],
    ["withSpring", [2]],
    ["withTiming", [2]],
    ["withCallback", [0]],
]);

const AUTO_WORKLET_OBJECT_ARGUMENTS = new Set([
    "useAnimatedGestureHandler",
    "useAnimatedScrollHandler",
]);

const ANIMATED_METHOD_CALLBACKS = new Set([
    "onBegin",
    "onChange",
    "onEnd",
    "onFinalize",
    "onStart",
    "onTouchesCancelled",
    "onTouchesDown",
    "onTouchesMove",
    "onTouchesUp",
    "onUpdate",
    "withCallback",
]);

const DEFAULT_WORKLET_GLOBALS = new Set([
    "Array",
    "ArrayBuffer",
    "AggregateError",
    "Atomics",
    "BigInt",
    "BigInt64Array",
    "BigUint64Array",
    "Boolean",
    "DataView",
    "Date",
    "Error",
    "EvalError",
    "Float32Array",
    "Float64Array",
    "FinalizationRegistry",
    "Infinity",
    "Int16Array",
    "Int32Array",
    "Int8Array",
    "Intl",
    "JSON",
    "Map",
    "Math",
    "NaN",
    "Number",
    "Object",
    "Promise",
    "Proxy",
    "RangeError",
    "ReferenceError",
    "Reflect",
    "RegExp",
    "Set",
    "SharedArrayBuffer",
    "String",
    "Symbol",
    "SyntaxError",
    "TypeError",
    "URIError",
    "Uint16Array",
    "Uint32Array",
    "Uint8Array",
    "Uint8ClampedArray",
    "URL",
    "URLSearchParams",
    "WeakRef",
    "WebAssembly",
    "WeakMap",
    "WeakSet",
    "console",
    "atob",
    "btoa",
    "cancelAnimationFrame",
    "clearInterval",
    "clearTimeout",
    "decodeURI",
    "decodeURIComponent",
    "encodeURI",
    "encodeURIComponent",
    "escape",
    "global",
    "globalThis",
    "isFinite",
    "isNaN",
    "parseFloat",
    "parseInt",
    "performance",
    "queueMicrotask",
    "requestAnimationFrame",
    "self",
    "setInterval",
    "setTimeout",
    "structuredClone",
    "undefined",
    "unescape",
]);

function unwrapExpressionPath(inputPath) {
    let current = inputPath;

    while (current) {
        if (
            current.isTSAsExpression?.()
            || current.isTSSatisfiesExpression?.()
            || current.isTSNonNullExpression?.()
            || current.isTypeCastExpression?.()
            || current.isParenthesizedExpression?.()
        ) {
            current = current.get("expression");
            continue;
        }

        return current;
    }

    return inputPath;
}

function importedName(importPath) {
    const imported = importPath.node.imported;
    if (!imported) return null;
    return imported.name ?? imported.value ?? null;
}

function resolveCanonicalCalleeName(inputPath, seenBindings = new Set()) {
    const calleePath = unwrapExpressionPath(inputPath);

    if (calleePath.isSequenceExpression?.()) {
        const expressions = calleePath.get("expressions");
        return expressions.length > 0
            ? resolveCanonicalCalleeName(expressions[expressions.length - 1], seenBindings)
            : null;
    }

    if (calleePath.isMemberExpression?.() || calleePath.isOptionalMemberExpression?.()) {
        const propertyPath = calleePath.get("property");
        if (!calleePath.node.computed && propertyPath.isIdentifier()) return propertyPath.node.name;
        if (calleePath.node.computed && propertyPath.isStringLiteral()) return propertyPath.node.value;
        return null;
    }

    if (!calleePath.isIdentifier?.()) return null;

    const localName = calleePath.node.name;
    const binding = calleePath.scope.getBinding(localName);
    if (!binding || seenBindings.has(binding)) return localName;
    seenBindings.add(binding);

    if (binding.path.isImportSpecifier()) return importedName(binding.path) ?? localName;
    if (binding.path.isImportDefaultSpecifier() || binding.path.isImportNamespaceSpecifier()) return localName;

    if (binding.path.isVariableDeclarator()) {
        const initializer = binding.path.get("init");
        if (initializer?.node) return resolveCanonicalCalleeName(initializer, seenBindings) ?? localName;
    }

    return localName;
}

function resolveReferencedFunction(inputPath, seenBindings = new Set()) {
    const candidatePath = unwrapExpressionPath(inputPath);

    if (
        candidatePath.isArrowFunctionExpression?.()
        || candidatePath.isFunctionExpression?.()
        || candidatePath.isObjectMethod?.()
    ) {
        return candidatePath;
    }

    if (!candidatePath.isIdentifier?.()) return null;

    const binding = candidatePath.scope.getBinding(candidatePath.node.name);
    if (!binding || seenBindings.has(binding)) return null;
    seenBindings.add(binding);

    if (binding.path.isFunctionDeclaration()) return binding.path;
    if (binding.path.isVariableDeclarator()) {
        const initializer = binding.path.get("init");
        if (initializer?.node) return resolveReferencedFunction(initializer, seenBindings);
    }

    for (const violation of binding.constantViolations ?? []) {
        if (!violation.isAssignmentExpression()) continue;
        const right = violation.get("right");
        const resolved = resolveReferencedFunction(right, seenBindings);
        if (resolved) return resolved;
    }

    return null;
}

function resolveReferencedObject(inputPath, seenBindings = new Set()) {
    const candidatePath = unwrapExpressionPath(inputPath);
    if (candidatePath.isObjectExpression?.()) return candidatePath;
    if (!candidatePath.isIdentifier?.()) return null;

    const binding = candidatePath.scope.getBinding(candidatePath.node.name);
    if (!binding || seenBindings.has(binding)) return null;
    seenBindings.add(binding);
    if (!binding.path.isVariableDeclarator()) return null;
    const initializer = binding.path.get("init");
    return initializer?.node ? resolveReferencedObject(initializer, seenBindings) : null;
}

function isAnimatedOwnedExpression(inputPath, seenBindings = new Set()) {
    const candidatePath = unwrapExpressionPath(inputPath);
    if (!candidatePath?.node) return false;

    if (candidatePath.isIdentifier()) {
        const binding = candidatePath.scope.getBinding(candidatePath.node.name);
        if (!binding || seenBindings.has(binding)) return false;
        if (resolveAnimatedBinding(binding, candidatePath.node.name)) return true;
        const importSource = importDeclarationFor(binding.path)?.node.source.value;
        if (importSource === SPOTIFYPLUS_GESTURE_MODULE) return true;
        seenBindings.add(binding);
        if (!binding.path.isVariableDeclarator()) return false;
        const initializerPath = binding.path.get("init");
        if (requireModuleName(initializerPath) === SPOTIFYPLUS_GESTURE_MODULE) return true;
        return initializerPath?.node
            ? isAnimatedOwnedExpression(initializerPath, seenBindings)
            : false;
    }

    if (candidatePath.isCallExpression() || candidatePath.isOptionalCallExpression?.()) {
        return isAnimatedOwnedExpression(candidatePath.get("callee"), seenBindings);
    }
    if (candidatePath.isMemberExpression() || candidatePath.isOptionalMemberExpression()) {
        return isAnimatedOwnedExpression(candidatePath.get("object"), seenBindings);
    }
    return false;
}

function markObjectWorklets(objectPath, state) {
    for (const propertyPath of objectPath.get("properties")) {
        if (propertyPath.isObjectMethod()) {
            state.spotifyPlusAutoWorklets.add(propertyPath.node);
            continue;
        }
        if (!propertyPath.isObjectProperty()) continue;
        const functionPath = resolveReferencedFunction(propertyPath.get("value"));
        if (functionPath?.node) state.spotifyPlusAutoWorklets.add(functionPath.node);
    }
}

function markAutomaticWorklets(programPath, state) {
    programPath.traverse({
        CallExpression(callPath) {
            const canonicalName = resolveCanonicalCalleeName(callPath.get("callee"));
            const argumentIndices = canonicalName ? AUTO_WORKLET_ARGUMENTS.get(canonicalName) : null;
            if (!argumentIndices) return;
            if (ANIMATED_METHOD_CALLBACKS.has(canonicalName)) {
                const calleePath = unwrapExpressionPath(callPath.get("callee"));
                if (
                    !(calleePath.isMemberExpression() || calleePath.isOptionalMemberExpression())
                    || !isAnimatedOwnedExpression(calleePath.get("object"))
                ) {
                    return;
                }
            }

            const argumentPaths = callPath.get("arguments");
            for (const index of argumentIndices) {
                const argumentPath = argumentPaths[index];
                if (!argumentPath?.node) continue;
                const functionPath = resolveReferencedFunction(argumentPath);
                if (functionPath?.node) {
                    state.spotifyPlusAutoWorklets.add(functionPath.node);
                    continue;
                }
                if (!AUTO_WORKLET_OBJECT_ARGUMENTS.has(canonicalName)) continue;
                const objectPath = resolveReferencedObject(argumentPath);
                if (objectPath) markObjectWorklets(objectPath, state);
            }
        },
    });
}

function hasWorkletDirective(functionPath) {
    const directives = functionPath.node.body?.directives;
    return Array.isArray(directives)
        && directives.some(directive => directive.value?.value === "worklet");
}

function removeWorkletDirective(node) {
    if (!Array.isArray(node.body?.directives)) return;
    node.body.directives = node.body.directives.filter(directive => directive.value?.value !== "worklet");
}

function isTypeOnlyReference(referencePath) {
    return !!referencePath.findParent(parentPath => (
        parentPath.isTSType?.()
        || parentPath.isTSTypeAnnotation?.()
        || parentPath.isTSTypeParameter?.()
        || parentPath.isTSTypeParameterDeclaration?.()
        || parentPath.isTSTypeParameterInstantiation?.()
        || parentPath.isTSInterfaceDeclaration?.()
        || parentPath.isTSTypeAliasDeclaration?.()
    ));
}

function isPathInside(candidatePath, ancestorPath) {
    let current = candidatePath;
    while (current) {
        if (current === ancestorPath) return true;
        current = current.parentPath;
    }
    return false;
}

function importDeclarationFor(bindingPath) {
    return bindingPath.findParent?.(parentPath => parentPath.isImportDeclaration?.()) ?? null;
}

function isAnimatedImport(bindingPath) {
    return isSpotifyPlusAnimatedModule(importDeclarationFor(bindingPath)?.node.source.value);
}

function staticMemberName(memberPath) {
    if (!memberPath?.node) return null;
    const propertyPath = memberPath.get("property");
    if (!memberPath.node.computed && propertyPath.isIdentifier()) return propertyPath.node.name;
    if (memberPath.node.computed && propertyPath.isStringLiteral()) return propertyPath.node.value;
    return null;
}

function requireModuleName(expressionPath) {
    const candidatePath = unwrapExpressionPath(expressionPath);
    if (!candidatePath?.isCallExpression?.()) return null;
    const calleePath = candidatePath.get("callee");
    const argumentPaths = candidatePath.get("arguments");
    if (argumentPaths.length !== 1) return null;
    if (calleePath.isIdentifier({ name: "require" })) {
        return argumentPaths[0].isStringLiteral() ? argumentPaths[0].node.value : null;
    }

    const wrapperName = calleePath.isIdentifier() ? calleePath.node.name : null;
    if ([
        "__importDefault",
        "__importStar",
        "__toESM",
        "_interopRequireDefault",
        "_interopRequireWildcard",
    ].includes(wrapperName)) {
        return requireModuleName(argumentPaths[0]);
    }
    return null;
}

function objectPatternExportName(patternPath, localName) {
    for (const propertyPath of patternPath.get("properties")) {
        if (!propertyPath.isObjectProperty()) continue;
        const valuePath = propertyPath.get("value");
        const localIdentifier = valuePath.isAssignmentPattern() ? valuePath.get("left") : valuePath;
        if (!localIdentifier.isIdentifier({ name: localName })) continue;

        const keyPath = propertyPath.get("key");
        if (!propertyPath.node.computed && keyPath.isIdentifier()) return keyPath.node.name;
        if (keyPath.isStringLiteral()) return keyPath.node.value;
    }
    return null;
}

function resolveAnimatedBinding(binding, localName, seenBindings = new Set()) {
    if (!binding || seenBindings.has(binding)) return null;
    seenBindings.add(binding);
    const bindingPath = binding.path;

    if (bindingPath.isImportSpecifier() && isAnimatedImport(bindingPath)) {
        return {
            exportName: importedName(bindingPath) ?? localName,
            kind: "direct",
        };
    }
    if (
        (bindingPath.isImportNamespaceSpecifier() || bindingPath.isImportDefaultSpecifier())
        && isAnimatedImport(bindingPath)
    ) {
        return { kind: "namespace" };
    }

    if (!bindingPath.isVariableDeclarator()) return null;
    const idPath = bindingPath.get("id");
    const initializerPath = unwrapExpressionPath(bindingPath.get("init"));
    if (!initializerPath?.node) return null;

    if (idPath.isObjectPattern()) {
        const exportName = objectPatternExportName(idPath, localName);
        if (!exportName) return null;
        const initializerModule = requireModuleName(initializerPath);
        if (isSpotifyPlusAnimatedModule(initializerModule)) return { exportName, kind: "direct" };
        if (initializerPath.isIdentifier()) {
            const sourceBinding = initializerPath.scope.getBinding(initializerPath.node.name);
            const source = resolveAnimatedBinding(sourceBinding, initializerPath.node.name, seenBindings);
            if (source?.kind === "namespace") return { exportName, kind: "direct" };
        }
        return null;
    }

    if (!idPath.isIdentifier({ name: localName })) return null;
    if (isSpotifyPlusAnimatedModule(requireModuleName(initializerPath))) return { kind: "namespace" };

    if (initializerPath.isIdentifier()) {
        const sourceBinding = initializerPath.scope.getBinding(initializerPath.node.name);
        return resolveAnimatedBinding(sourceBinding, initializerPath.node.name, seenBindings);
    }

    if (initializerPath.isMemberExpression() || initializerPath.isOptionalMemberExpression()) {
        const exportName = staticMemberName(initializerPath);
        const objectPath = unwrapExpressionPath(initializerPath.get("object"));
        if (!exportName || !objectPath.isIdentifier()) return null;
        const sourceBinding = objectPath.scope.getBinding(objectPath.node.name);
        const source = resolveAnimatedBinding(sourceBinding, objectPath.node.name, seenBindings);
        if (source?.kind === "namespace") return { exportName, kind: "direct" };
    }

    return null;
}

function resolveAnimatedReference(referencePath) {
    const binding = referencePath.scope.getBinding(referencePath.node.name);
    const resolved = resolveAnimatedBinding(binding, referencePath.node.name);
    if (!resolved) return null;
    if (resolved.kind === "direct") return resolved;

    const parentPath = referencePath.parentPath;
    if (
        (parentPath?.isMemberExpression() || parentPath?.isOptionalMemberExpression())
        && parentPath.get("object") === referencePath
    ) {
        const exportName = staticMemberName(parentPath);
        if (exportName) return {
            exportName,
            kind: "namespace",
        };
    }

    throw referencePath.buildCodeFrameError(
        `Animated namespace '${referencePath.node.name}' must use a static member inside a worklet. Use a named import for dynamic access.`,
    );
}

function getDeclaredWorkletName(functionPath, types, state) {
    if (functionPath.node.id?.name) return functionPath.node.id.name;

    const parentPath = functionPath.parentPath;
    if (parentPath?.isVariableDeclarator() && parentPath.get("id").isIdentifier()) {
        return parentPath.node.id.name;
    }
    if (parentPath?.isAssignmentExpression() && parentPath.get("left").isIdentifier()) {
        return parentPath.node.left.name;
    }
    if (parentPath?.isObjectProperty() && !parentPath.node.computed) {
        const key = parentPath.node.key;
        if (types.isIdentifier(key)) return key.name;
        if (types.isStringLiteral(key)) return types.toIdentifier(key.value);
    }
    if (functionPath.isObjectMethod() && !functionPath.node.computed) {
        const key = functionPath.node.key;
        if (types.isIdentifier(key)) return key.name;
        if (types.isStringLiteral(key)) return types.toIdentifier(key.value);
    }

    const start = functionPath.node.loc?.start;
    const line = start?.line ?? 0;
    const column = (start?.column ?? 0) + 1;
    const index = state.spotifyPlusWorkletIndex++;
    return `worklet_${line}_${column}_${index}`;
}

function collectClosureCaptures(functionPath, workletName, globals) {
    const captures = new Map();
    const ownBinding = functionPath.scope.getBinding(workletName);

    functionPath.traverse({
        ReferencedIdentifier(referencePath) {
            if (isTypeOnlyReference(referencePath)) return;

            const name = referencePath.node.name;
            if (globals.has(name)) return;

            const binding = referencePath.scope.getBinding(name);
            if (binding && isPathInside(binding.path, functionPath)) return;

            if (name === workletName && binding && binding === ownBinding) return;
            if (
                name === workletName
                && binding
                && functionPath.parentPath?.isVariableDeclarator()
                && binding.path === functionPath.parentPath
            ) {
                return;
            }

            const animatedReference = resolveAnimatedReference(referencePath);
            const existing = captures.get(name);
            if (animatedReference?.kind === "direct") {
                captures.set(name, {
                    global: animatedReference.exportName,
                    name,
                });
                return;
            }
            if (animatedReference?.kind === "namespace") {
                const namespace = existing?.namespace ?? new Map();
                namespace.set(animatedReference.exportName, animatedReference.exportName);
                captures.set(name, {
                    name,
                    namespace,
                });
                return;
            }

            if (!existing) captures.set(name, { name });
        },
    });

    return [...captures.values()].sort((left, right) => left.name.localeCompare(right.name));
}

function createFunctionExpression(functionPath, types, workletName, includeClosure, closureNames) {
    const sourceNode = functionPath.node;
    let body;

    if (types.isBlockStatement(sourceNode.body)) {
        body = types.cloneNode(sourceNode.body, true);
    } else {
        body = types.blockStatement([
            types.returnStatement(types.cloneNode(sourceNode.body, true)),
        ]);
    }

    removeWorkletDirective({ body });

    if (includeClosure && closureNames.length > 0) {
        const properties = closureNames.map(name => types.objectProperty(
            types.identifier(name),
            types.identifier(name),
            false,
            true,
        ));
        const closureDeclaration = types.variableDeclaration("const", [
            types.variableDeclarator(
                types.objectPattern(properties),
                types.memberExpression(types.thisExpression(), types.identifier("__closure")),
            ),
        ]);
        body.body.unshift(closureDeclaration);
    }

    const functionExpression = types.functionExpression(
        types.identifier(types.toIdentifier(workletName)),
        sourceNode.params.map(parameter => types.cloneNode(parameter, true)),
        body,
        sourceNode.generator === true,
        sourceNode.async === true,
    );

    functionExpression.returnType = sourceNode.returnType
        ? types.cloneNode(sourceNode.returnType, true)
        : null;
    functionExpression.typeParameters = sourceNode.typeParameters
        ? types.cloneNode(sourceNode.typeParameters, true)
        : null;

    return functionExpression;
}

function stripTypesFromFunction(functionNode, types, filename, sourceCode) {
    const holderName = "__spotifyPlusWorkletFunction";
    const holder = types.variableDeclaration("const", [
        types.variableDeclarator(types.identifier(holderName), functionNode),
    ]);
    const file = types.file(types.program([holder]));
    const extension = path.extname(filename).toLowerCase();
    const isTSX = extension === ".tsx" || extension === ".mtsx" || extension === ".ctsx";
    const transformed = transformFromAstSync(file, sourceCode, {
        ast: true,
        babelrc: false,
        cloneInputAst: true,
        code: false,
        configFile: false,
        filename,
        plugins: [[transformTypeScript, {
            allExtensions: true,
            allowDeclareFields: true,
            allowNamespaces: true,
            isTSX,
        }]],
    });

    const declaration = transformed?.ast?.program?.body?.[0];
    const result = declaration?.declarations?.[0]?.init;
    if (!result) throw new Error(`Could not strip TypeScript syntax from worklet in ${filename}`);
    return result;
}

function normalizeLocation(filename, rootDir, functionPath) {
    const absoluteFilename = path.resolve(filename);
    const absoluteRoot = path.resolve(rootDir ?? process.cwd());
    let relative = path.relative(absoluteRoot, absoluteFilename);
    if (!relative || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
        relative = path.basename(absoluteFilename);
    }

    const start = functionPath.node.loc?.start;
    const suffix = start ? `:${start.line}:${start.column + 1}` : "";
    return `${relative.split(path.sep).join("/")}${suffix}`;
}

function createWorkletSource(functionPath, types, workletName, closureNames, state) {
    let hasJSX = false;
    functionPath.traverse({
        JSXElement(jsxPath) {
            hasJSX = true;
            jsxPath.stop();
        },
        JSXFragment(jsxPath) {
            hasJSX = true;
            jsxPath.stop();
        },
    });
    if (hasJSX) {
        throw functionPath.buildCodeFrameError("SpotifyPlus worklets cannot contain JSX. Move React rendering outside the worklet.");
    }

    const filename = state.file.opts.filename ?? "unknown.js";
    const sourceCode = state.file.code ?? "";
    const rawFunction = createFunctionExpression(functionPath, types, workletName, true, closureNames);
    const javascriptFunction = stripTypesFromFunction(rawFunction, types, filename, sourceCode);
    const relativeSource = normalizeLocation(filename, state.opts.rootDir, functionPath).replace(/:\d+:\d+$/, "");
    const generated = generate(javascriptFunction, {
        comments: false,
        sourceFileName: relativeSource,
        sourceMaps: true,
    }, sourceCode);
    const code = generated.code;
    const hash = crypto.createHash("sha256").update(code).digest("hex");
    const location = normalizeLocation(filename, state.opts.rootDir, functionPath);
    const sourceMap = generated.map ? JSON.stringify(generated.map) : undefined;

    return {
        code,
        hash,
        location,
        sourceMap,
    };
}

function globalsForCaptures(captures) {
    const globals = new Map();
    for (const capture of captures) {
        if (capture.global) globals.set(capture.name, capture.global);
        for (const [propertyName, exportName] of capture.namespace ?? []) {
            globals.set(`${capture.name}.${propertyName}`, exportName);
        }
    }
    return globals;
}

function createGlobalsObject(types, globals) {
    return types.objectExpression([...globals].map(([localName, exportName]) => types.objectProperty(
        types.stringLiteral(localName),
        types.stringLiteral(exportName),
    )));
}

function createWorkletGlobalMarker(types, exportName) {
    return types.objectExpression([
        types.objectProperty(types.identifier("__spotifyPlusShareable"), types.stringLiteral("workletGlobal")),
        types.objectProperty(types.identifier("module"), types.stringLiteral(SPOTIFYPLUS_ANIMATED_MODULE)),
        types.objectProperty(types.identifier("name"), types.stringLiteral(exportName)),
    ]);
}

function createCaptureValue(types, capture) {
    if (capture.global) return createWorkletGlobalMarker(types, capture.global);
    if (capture.namespace) {
        return types.objectExpression([...capture.namespace].map(([propertyName, exportName]) => types.objectProperty(
            types.isValidIdentifier(propertyName) ? types.identifier(propertyName) : types.stringLiteral(propertyName),
            createWorkletGlobalMarker(types, exportName),
        )));
    }
    return types.identifier(capture.name);
}

function createDataObject(types, source, globals) {
    const properties = [
        types.objectProperty(types.identifier("code"), types.stringLiteral(source.code)),
        types.objectProperty(types.identifier("location"), types.stringLiteral(source.location)),
    ];
    if (source.sourceMap) {
        properties.push(types.objectProperty(types.identifier("sourceMap"), types.stringLiteral(source.sourceMap)));
    }
    if (globals.size > 0) {
        properties.push(types.objectProperty(types.identifier("globals"), createGlobalsObject(types, globals)));
    }
    return types.objectExpression(properties);
}

function createAttachmentStatements(types, scope, target, captures, source) {
    const closureIdentifier = scope.generateUidIdentifier("spotifyPlusClosure");
    const initDataIdentifier = scope.generateUidIdentifier("spotifyPlusInitData");
    const globals = globalsForCaptures(captures);
    const closureProperties = captures.map(capture => types.objectProperty(
        types.identifier(capture.name),
        createCaptureValue(types, capture),
    ));
    const statements = [
        types.variableDeclaration("const", [
            types.variableDeclarator(closureIdentifier, types.objectExpression(closureProperties)),
        ]),
        types.variableDeclaration("const", [
            types.variableDeclarator(initDataIdentifier, createDataObject(types, source, globals)),
        ]),
        types.expressionStatement(types.assignmentExpression(
            "=",
            types.memberExpression(types.cloneNode(target), types.identifier("__spotifyPlusWorklet")),
            types.objectExpression([
                types.objectProperty(types.identifier("version"), types.numericLiteral(SPOTIFYPLUS_WORKLET_VERSION)),
                types.objectProperty(types.identifier("hash"), types.stringLiteral(source.hash)),
                types.objectProperty(types.identifier("code"), types.stringLiteral(source.code)),
                types.objectProperty(types.identifier("closure"), types.cloneNode(closureIdentifier)),
                types.objectProperty(types.identifier("location"), types.stringLiteral(source.location)),
                ...(source.sourceMap
                    ? [types.objectProperty(types.identifier("sourceMap"), types.stringLiteral(source.sourceMap))]
                    : []),
                ...(globals.size > 0
                    ? [types.objectProperty(types.identifier("globals"), createGlobalsObject(types, globals))]
                    : []),
            ]),
        )),
        types.expressionStatement(types.assignmentExpression(
            "=",
            types.memberExpression(types.cloneNode(target), types.identifier("__workletHash")),
            types.stringLiteral(source.hash),
        )),
        types.expressionStatement(types.assignmentExpression(
            "=",
            types.memberExpression(types.cloneNode(target), types.identifier("__closure")),
            types.cloneNode(closureIdentifier),
        )),
        types.expressionStatement(types.assignmentExpression(
            "=",
            types.memberExpression(types.cloneNode(target), types.identifier("__initData")),
            types.cloneNode(initDataIdentifier),
        )),
    ];

    return statements;
}

function createFactoryCall(functionPath, types, workletName, captures, source) {
    const workletIdentifier = functionPath.scope.generateUidIdentifier("spotifyPlusWorklet");
    const originalFunction = createFunctionExpression(functionPath, types, workletName, false, []);
    const body = [
        types.variableDeclaration("const", [
            types.variableDeclarator(workletIdentifier, originalFunction),
        ]),
        ...createAttachmentStatements(types, functionPath.scope, workletIdentifier, captures, source),
        types.returnStatement(types.cloneNode(workletIdentifier)),
    ];

    return types.callExpression(types.arrowFunctionExpression([], types.blockStatement(body)), []);
}

function attachToFunctionDeclaration(functionPath, types, captures, source) {
    const identifier = functionPath.node.id;
    if (!identifier) return false;

    const statements = createAttachmentStatements(types, functionPath.scope, identifier, captures, source);
    const parentPath = functionPath.parentPath;
    const insertionPath = parentPath?.isExportNamedDeclaration() || parentPath?.isExportDefaultDeclaration()
        ? parentPath
        : functionPath;
    insertionPath.insertAfter(statements);
    return true;
}

function processWorklet(functionPath, state, types) {
    if (state.spotifyPlusProcessedWorklets.has(functionPath.node)) return;
    if (!functionPath.node.body) return;

    if (functionPath.isClassMethod?.() || functionPath.isClassPrivateMethod?.()) {
        throw functionPath.buildCodeFrameError("SpotifyPlus worklets do not support class methods. Use a function declaration or arrow function.");
    }

    const workletName = getDeclaredWorkletName(functionPath, types, state);
    const globals = new Set([
        ...DEFAULT_WORKLET_GLOBALS,
        ...(state.opts.globals ?? []),
    ]);
    const captures = collectClosureCaptures(functionPath, workletName, globals);
    const closureNames = captures.map(capture => capture.name);
    const source = createWorkletSource(functionPath, types, workletName, closureNames, state);

    state.spotifyPlusProcessedWorklets.add(functionPath.node);
    removeWorkletDirective(functionPath.node);

    if (functionPath.isFunctionDeclaration() && attachToFunctionDeclaration(functionPath, types, captures, source)) {
        state.opts.onWorklet?.({
            closureNames,
            globals: Object.fromEntries(globalsForCaptures(captures)),
            ...source,
        });
        return;
    }

    const factoryCall = createFactoryCall(functionPath, types, workletName, captures, source);
    if (functionPath.isObjectMethod()) {
        functionPath.replaceWith(types.objectProperty(
            types.cloneNode(functionPath.node.key, true),
            factoryCall,
            functionPath.node.computed === true,
            false,
        ));
    } else {
        functionPath.replaceWith(factoryCall);
    }
    functionPath.skip();

    state.opts.onWorklet?.({
        closureNames,
        globals: Object.fromEntries(globalsForCaptures(captures)),
        ...source,
    });
}

export function spotifyPlusWorkletsBabelPlugin({ types }) {
    return {
        name: "spotifyplus-worklets",
        pre() {
            this.spotifyPlusAutoWorklets = new WeakSet();
            this.spotifyPlusProcessedWorklets = new WeakSet();
            this.spotifyPlusWorkletIndex = 0;
        },
        visitor: {
            Program: {
                enter(programPath, state) {
                    markAutomaticWorklets(programPath, state);
                },
            },
            Function: {
                exit(functionPath, state) {
                    if (
                        hasWorkletDirective(functionPath)
                        || state.spotifyPlusAutoWorklets.has(functionPath.node)
                    ) {
                        processWorklet(functionPath, state, types);
                    }
                },
            },
        },
    };
}

function parserPluginsFor(filename) {
    const extension = path.extname(filename).toLowerCase();
    const plugins = [];
    if ([".ts", ".tsx", ".mts", ".cts", ".mtsx", ".ctsx"].includes(extension)) {
        plugins.push("typescript");
    }
    if ([".jsx", ".tsx", ".mtsx", ".ctsx"].includes(extension)) plugins.push("jsx");
    plugins.push("decorators-legacy");
    return plugins;
}

export async function transformWorklets(source, options = {}) {
    const filename = path.resolve(options.filename ?? "worklet.js");
    const extension = path.extname(filename).toLowerCase();
    const isTypeScript = [".ts", ".tsx", ".mts", ".cts", ".mtsx", ".ctsx"].includes(extension);
    const isTSX = [".tsx", ".mtsx", ".ctsx"].includes(extension);
    let workletCount = 0;
    const worklets = [];
    const result = await transformAsync(source, {
        ast: false,
        babelrc: false,
        code: true,
        configFile: false,
        filename,
        parserOpts: {
            plugins: parserPluginsFor(filename),
            sourceType: "unambiguous",
        },
        plugins: [
            [spotifyPlusWorkletsBabelPlugin, {
                globals: options.globals ?? [],
                onWorklet(worklet) {
                    workletCount++;
                    worklets.push(worklet);
                    options.onWorklet?.(worklet);
                },
                rootDir: options.rootDir ?? path.dirname(filename),
            }],
            ...(isTypeScript
                ? [[transformTypeScript, {
                    allExtensions: true,
                    allowDeclareFields: true,
                    allowNamespaces: true,
                    isTSX,
                }]]
                : []),
        ],
        sourceFileName: path.basename(filename),
        sourceMaps: true,
    });

    if (typeof result?.code !== "string") throw new Error(`Babel did not produce output for ${filename}`);

    let code = result.code;
    if (options.inlineSourceMap !== false && result.map) {
        const encodedMap = Buffer.from(JSON.stringify(result.map), "utf8").toString("base64");
        code += `\n//# sourceMappingURL=data:application/json;charset=utf-8;base64,${encodedMap}`;
    }

    return {
        code,
        map: result.map ?? null,
        workletCount,
        worklets,
    };
}

function loaderForFile(filename) {
    const extension = path.extname(filename).toLowerCase();
    if ([".ts", ".mts", ".cts"].includes(extension)) return "ts";
    if ([".tsx", ".mtsx", ".ctsx"].includes(extension)) return "tsx";
    if (extension === ".jsx") return "jsx";
    return "js";
}

function isTransformableSource(filename, rootDir, transformDependencies) {
    const relative = path.relative(rootDir, filename);
    if (relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) return false;
    if (!transformDependencies && relative.split(path.sep).includes("node_modules")) return false;
    return true;
}

export function spotifyPlusWorkletsPlugin(options = {}) {
    const rootDir = path.resolve(options.rootDir ?? process.cwd());

    return {
        name: "spotifyplus-worklets",
        setup(build) {
            build.onLoad({ filter: /\.[cm]?[jt]sx?$/ }, async args => {
                if (!isTransformableSource(args.path, rootDir, options.transformDependencies === true)) return null;

                const source = await fs.readFile(args.path, "utf8");
                const transformed = await transformWorklets(source, {
                    filename: args.path,
                    globals: options.globals,
                    inlineSourceMap: true,
                    rootDir,
                });

                return {
                    contents: transformed.code,
                    loader: loaderForFile(args.path),
                    resolveDir: path.dirname(args.path),
                    watchFiles: [args.path],
                };
            });
        },
    };
}
