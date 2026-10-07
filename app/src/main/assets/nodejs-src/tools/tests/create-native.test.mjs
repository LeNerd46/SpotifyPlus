import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createNativeProject, NATIVE_SDK_URL, runCreateNative, validateNamespace } from "../create-native.mjs";

const sdkBytes = Buffer.from([0x50, 0x4b, 0x03, 0x04, 0x01]);
const fetchSdk = async url => {
    assert.equal(url, NATIVE_SDK_URL);
    return { ok: true, arrayBuffer: async () => sdkBytes };
};

async function temporaryDirectory(t) {
    const directory = await fs.mkdtemp(path.join(os.tmpdir(), "spotifyplus-native-test-"));
    t.after(() => fs.rm(directory, { recursive: true, force: true }));
    return directory;
}

test("namespaces reject invalid identifiers, keywords, and path injection", () => {
    assert.equal(validateNamespace("com.example.myplugin"), true);
    for (const namespace of ["", "single", "com..example", "com.1plugin", "com.native", "com.class", "com.object", "../escape", "com.example\"\n"]) {
        assert.notEqual(validateNamespace(namespace), true, namespace);
    }
});

for (const language of ["java", "kotlin"]) {
    test(`${language} project uses the chosen namespace and compile-only SDK with no activity`, async t => {
        const temporaryDir = await temporaryDirectory(t);
        const projectDir = path.join(temporaryDir, `${language}-plugin`);
        await createNativeProject({ projectDir, language, namespace: "org.example.plugin" }, { fetchImpl: fetchSdk });
        const read = name => fs.readFile(path.join(projectDir, name), "utf8");
        const sourceDir = "app/src/main/java/org/example/plugin";
        const extension = language === "java" ? "java" : "kt";
        assert.deepEqual((await fs.readdir(path.join(projectDir, sourceDir))).sort(), [
            `ExampleComponent.${extension}`, `ExampleView.${extension}`, `NativePlugin.${extension}`,
        ]);
        const plugin = await read(`${sourceDir}/NativePlugin.${extension}`);
        assert.match(plugin, /package org\.example\.plugin/);
        assert.match(plugin, /registry\.registerComponent\(/);
        const component = await read(`${sourceDir}/ExampleComponent.${extension}`);
        assert.match(component, /SpotifyPlusComponent<ExampleView>/);
        assert.match(component, /import android\.content\.Context/);
        assert.match(component, /newProps\.has\("text"\)/);
        assert.doesNotMatch(component, /GradientTextView/);
        const view = await read(`${sourceDir}/ExampleView.${extension}`);
        assert.match(view, /import android\.util\.AttributeSet/);
        assert.match(view, /import android\.graphics\.Canvas/);
        assert.match(view, /super\.onDraw\(canvas\)/);
        assert.doesNotMatch(await read("app/src/main/AndroidManifest.xml"), /activity|intent-filter/);
        const gradle = await read("app/build.gradle.kts");
        assert.match(gradle, /namespace = "org\.example\.plugin"/);
        assert.match(gradle, /compileOnly\(files\("\$rootDir\/lib\/spotifyplus-sdk\.aar"\)\)/);
        assert.match(gradle, /JavaVersion\.VERSION_21/);
        if (language === "kotlin") {
            assert.match(gradle, /org\.jetbrains\.kotlin\.android/);
            assert.match(gradle, /jvmTarget = "21"/);
        } else {
            assert.doesNotMatch(gradle, /kotlin/);
        }
        assert.deepEqual(await fs.readFile(path.join(projectDir, "lib/spotifyplus-sdk.aar")), sdkBytes);
        const wrapper = await fs.readFile(path.join(projectDir, "gradle/wrapper/gradle-wrapper.jar"));
        assert.equal(wrapper.subarray(0, 2).toString(), "PK");
        assert.match(await read("gradlew"), /Gradle/);
        assert.match(await read("gradlew.bat"), /Gradle/);
        assert.match(await read("README.md"), /org\.example\.plugin\.NativePlugin/);
    });
}

test("existing projects are preserved without downloading", async t => {
    const projectDir = await temporaryDirectory(t);
    await fs.writeFile(path.join(projectDir, "keep.txt"), "existing project");
    await assert.rejects(createNativeProject({ projectDir, language: "java", namespace: "com.example.plugin" }, {
        fetchImpl: () => assert.fail("must not download into an existing project"),
    }), /already exists/);
    assert.equal(await fs.readFile(path.join(projectDir, "keep.txt"), "utf8"), "existing project");
});

test("download failures leave no generated project", async t => {
    const temporaryDir = await temporaryDirectory(t);
    const projectDir = path.join(temporaryDir, "plugin");
    for (const fetchImpl of [
        async () => ({ ok: false, status: 404 }),
        async () => ({ ok: true, arrayBuffer: async () => Buffer.from("not an AAR") }),
        async () => { throw new Error("offline"); },
    ]) {
        await assert.rejects(createNativeProject({ projectDir, language: "java", namespace: "com.example.plugin" }, { fetchImpl }), /Could not download the SpotifyPlus SDK/);
        assert.deepEqual(await fs.readdir(temporaryDir), []);
    }
});

test("interactive command prompts for language, namespace, and destination", async t => {
    const temporaryDir = await temporaryDirectory(t);
    const projectDir = path.join(temporaryDir, "prompted");
    const messages = [];
    await runCreateNative({}, {
        prompt: async questions => {
            assert.deepEqual(questions.map(question => question.name), ["language", "namespace", "projectDir"]);
            assert.deepEqual(questions[0].choices.map(choice => choice.value), ["java", "kotlin"]);
            return { language: "kotlin", namespace: "com.example.prompted", projectDir };
        },
        fetchImpl: fetchSdk,
        log: message => messages.push(message),
    });
    assert.match(messages.join("\n"), /created kotlin native plugin/);
    assert.ok(await fs.stat(path.join(projectDir, "app/src/main/java/com/example/prompted/NativePlugin.kt")));
});

test("a directory supplied on the command line skips only the destination prompt", async t => {
    const temporaryDir = await temporaryDirectory(t);
    const projectDir = path.join(temporaryDir, "cli-directory");
    await runCreateNative({ projectDir }, {
        prompt: async questions => {
            assert.deepEqual(questions.map(question => question.name), ["language", "namespace"]);
            return { language: "java", namespace: "com.example.plugin" };
        },
        fetchImpl: fetchSdk,
        log: () => {},
    });
    assert.ok(await fs.stat(path.join(projectDir, "lib/spotifyplus-sdk.aar")));
});
