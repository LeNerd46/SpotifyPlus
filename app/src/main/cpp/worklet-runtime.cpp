#include "worklet-runtime.h"

#include <android/log.h>
#include <jni.h>
#include <node.h>
#include <v8.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cctype>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <functional>
#include <iomanip>
#include <limits>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#include "spotifyengine.h"

namespace
{
constexpr const char* TAG = "SpotifyPlus::Worklets";
constexpr char KEY_SEPARATOR = '\x1f';

constexpr const char* CONTEXT_BOOTSTRAP = R"SPOTIFYPLUS(
(function () {
  const registry = Object.create(null);
  const shareableReferences = new WeakMap();
  const uiFunctions = new Map();
  let nextUiFunctionId = 1;
  Object.defineProperty(globalThis, '__spotifyplusWorkletGlobals', {
    value: registry,
    configurable: false,
    enumerable: false,
    writable: false,
  });

  Object.defineProperty(globalThis, '_WORKLET', { value: true, enumerable: false });
  Object.defineProperty(globalThis, 'global', { value: globalThis, enumerable: false });
  Object.defineProperty(globalThis, 'performance', {
    value: Object.freeze({ now: () => __spotifyplusNow() }),
    enumerable: false,
  });

  const consoleMethods = {};
  for (const level of ['debug', 'info', 'log', 'warn', 'error']) {
    consoleMethods[level] = (...args) => __spotifyplusLog(level, args);
  }
  Object.defineProperty(globalThis, 'console', {
    value: Object.freeze(consoleMethods),
    enumerable: false,
  });
  Object.defineProperty(globalThis, 'queueMicrotask', {
    value(callback) {
      if (typeof callback !== 'function') throw new TypeError('queueMicrotask requires a function');
      Promise.resolve().then(callback);
    },
    enumerable: false,
  });
  globalThis.queueMicrotask = function (callback) {
    if (typeof callback !== 'function') throw new TypeError('queueMicrotask requires a function');
    Promise.resolve().then(callback);
  };

  let nextAnimationFrameId = 1;
  const animationFrameCallbacks = new Map();
  globalThis.requestAnimationFrame = function (callback) {
    if (typeof callback !== 'function') throw new TypeError('requestAnimationFrame requires a function');
    const id = nextAnimationFrameId++;
    animationFrameCallbacks.set(id, callback);
    __spotifyplusRequestFrame();
    return id;
  };
  globalThis.cancelAnimationFrame = function (id) {
    animationFrameCallbacks.delete(Number(id));
  };
  globalThis.__spotifyplusDrainAnimationFrames = function (timestamp) {
    const callbacks = Array.from(animationFrameCallbacks.values());
    animationFrameCallbacks.clear();
    let firstError;
    for (const callback of callbacks) {
      try {
        callback(timestamp);
      } catch (error) {
        firstError ??= error;
      }
    }
    if (firstError !== undefined) throw firstError;
    return animationFrameCallbacks.size > 0;
  };

  globalThis.__spotifyplusInstallWorkletGlobals = function (moduleName, values) {
    if (!values || typeof values !== 'object') return false;
    for (const name of Object.keys(values)) {
      registry[moduleName + '#' + name] = values[name];
      if (!(name in registry)) registry[name] = values[name];
    }
    return true;
  };

  globalThis.__spotifyplusReviveShareable = function revive(value) {
    if (Array.isArray(value)) return value.map(revive);
    if (value === null || typeof value !== 'object') return value;

    const kind = value.__spotifyPlusShareable;
    if (!kind) {
      const object = {};
      for (const key of Object.keys(value)) object[key] = revive(value[key]);
      return object;
    }

    if (kind === 'sharedValue') {
      const id = String(value.id);
      const shared = {};
      Object.defineProperty(shared, 'value', {
        enumerable: true,
        get() { return __spotifyplusGetSharedValue(id); },
        set(next) { __spotifyplusSetSharedValue(id, next); },
      });
      shared.get = function () { return __spotifyplusGetSharedValue(id); };
      shared.set = function (next) {
        const current = __spotifyplusGetSharedValue(id);
        return __spotifyplusSetSharedValue(id, typeof next === 'function' ? next(current) : next);
      };
      shared.modify = function (modifier) {
        const current = __spotifyplusGetSharedValue(id);
        const next = typeof modifier === 'function' ? modifier(current) : current;
        __spotifyplusSetSharedValue(id, next);
        return next;
      };
      shared.cancel = function () { return __spotifyplusCancelAnimation(id); };
      shareableReferences.set(shared, value);
      return shared;
    }

    if (kind === 'worklet') {
      const id = String(value.id);
      const worklet = function (...args) { return __spotifyplusCallWorklet(id, args); };
      shareableReferences.set(worklet, value);
      return worklet;
    }

    if (kind === 'inlineWorklet') {
      const worklet = compileSerializedWorklet(value.metadata ?? value.worklet ?? value.value);
      if (typeof worklet !== 'function') throw new TypeError('Invalid inline worklet metadata');
      return worklet;
    }

    if (kind === 'rnFunction') {
      const id = String(value.id);
      const fn = function (...args) { return __spotifyplusScheduleOnRN(id, args); };
      shareableReferences.set(fn, value);
      return fn;
    }

    if (kind === 'workletGlobal') {
      const globalValue = registry[String(value.module) + '#' + String(value.name)] ?? registry[String(value.name)];
      if (globalValue && (typeof globalValue === 'object' || typeof globalValue === 'function')) {
        shareableReferences.set(globalValue, value);
      }
      return globalValue;
    }

    if (kind === 'view') {
      const surfaceId = String(value.surfaceId);
      const nodeId = Number(value.nodeId);
      const view = {
        surfaceId,
        nodeId,
        measure() { return __spotifyplusMeasure(surfaceId, nodeId); },
        getRelativeCoords(x, y) { return __spotifyplusGetRelativeCoords(surfaceId, nodeId, x, y); },
        scrollTo(x, y, animated) { return __spotifyplusScrollTo(surfaceId, nodeId, x, y, !!animated); },
        dispatchCommand(command, args) { return __spotifyplusDispatchCommand(surfaceId, nodeId, command, args ?? []); },
        setNativeProps(props) { return __spotifyplusSetNativeProps(surfaceId, nodeId, props); },
      };
      shareableReferences.set(view, value);
      return Object.freeze(view);
    }

    if (kind === 'uiFunction') {
      const id = String(value.id);
      const fn = uiFunctions.get(id);
      uiFunctions.delete(id);
      if (typeof fn !== 'function') throw new TypeError('UI function reference expired: ' + id);
      return fn;
    }

    if (kind === 'map') return new Map((value.entries ?? []).map(([key, item]) => [revive(key), revive(item)]));
    if (kind === 'set') return new Set((value.values ?? []).map(revive));
    if (kind === 'regexp') return new RegExp(String(value.source ?? ''), String(value.flags ?? ''));
    if (kind === 'date') return new Date(String(value.value));
    if (kind === 'bigint') return BigInt(String(value.value));
    if (kind === 'undefined') return undefined;
    if (kind === 'arrayBuffer') return new Uint8Array(value.bytes ?? []).buffer;
    if (kind === 'typedArray') {
      const constructor = globalThis[String(value.name)];
      if (typeof constructor !== 'function') throw new TypeError('Unknown typed array ' + value.name);
      const bytes = new Uint8Array(value.values ?? []);
      const buffer = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
      return value.name === 'DataView' ? new DataView(buffer) : new constructor(buffer);
    }

    throw new TypeError('Unsupported SpotifyPlus shareable marker: ' + kind);
  };

  globalThis.__spotifyplusSerializeShareable = function serialize(value, seen = new Set()) {
    if (value === null || typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      return value;
    }
    if (value === undefined) return { __spotifyPlusShareable: 'undefined' };
    if (typeof value === 'bigint') {
      return { __spotifyPlusShareable: 'bigint', value: String(value) };
    }
    if (typeof value === 'symbol') throw new TypeError('Symbols cannot cross the worklet boundary');

    const known = shareableReferences.get(value);
    if (known) return known;
    if (typeof value === 'function') {
      const id = String(nextUiFunctionId++);
      uiFunctions.set(id, value);
      return { __spotifyPlusShareable: 'uiFunction', id };
    }
    if (seen.has(value)) throw new TypeError('Circular values cannot cross the worklet boundary');

    seen.add(value);
    try {
      if (Array.isArray(value)) return value.map(item => serialize(item, seen));
      if (value instanceof Map) {
        return {
          __spotifyPlusShareable: 'map',
          entries: Array.from(value, ([key, item]) => [serialize(key, seen), serialize(item, seen)]),
        };
      }
      if (value instanceof Set) {
        return {
          __spotifyPlusShareable: 'set',
          values: Array.from(value, item => serialize(item, seen)),
        };
      }
      if (value instanceof RegExp) {
        return { __spotifyPlusShareable: 'regexp', source: value.source, flags: value.flags };
      }
      if (value instanceof Date) {
        return { __spotifyPlusShareable: 'date', value: value.toISOString() };
      }
      if (value instanceof ArrayBuffer) {
        return { __spotifyPlusShareable: 'arrayBuffer', bytes: Array.from(new Uint8Array(value)) };
      }
      if (ArrayBuffer.isView(value)) {
        return {
          __spotifyPlusShareable: 'typedArray',
          name: value.constructor?.name ?? 'Uint8Array',
          values: Array.from(new Uint8Array(value.buffer, value.byteOffset, value.byteLength)),
        };
      }

      const output = {};
      for (const key of Object.keys(value)) output[key] = serialize(value[key], seen);
      return output;
    } finally {
      seen.delete(value);
    }
  };

  function compileSerializedWorklet(serialized) {
    if (typeof serialized === 'function') return serialized;
    const metadata = serialized && (serialized.metadata ?? serialized);
    if (!metadata || typeof metadata.code !== 'string') return null;
    const closure = globalThis.__spotifyplusReviveShareable(metadata.closure ?? {});
    const factory = new Function(
      '__closure',
      'with(__closure){const __fn=(' + metadata.code + '\n);' +
        "Object.defineProperty(__fn,'__closure',{value:__closure,enumerable:false});return __fn;}",
    );
    return factory(closure);
  }

  function callSerializedWorklet(serialized, args) {
    const worklet = compileSerializedWorklet(serialized);
    return worklet ? worklet.call(worklet, ...args) : undefined;
  }

  function mapValue(value, mapper) {
    if (Array.isArray(value)) return value.map(item => mapValue(item, mapper));
    if (value && typeof value === 'object') {
      const result = {};
      for (const key of Object.keys(value)) result[key] = mapValue(value[key], mapper);
      return result;
    }
    return mapper(Number(value));
  }

  function zipValue(left, right, mapper) {
    if (Array.isArray(left) && Array.isArray(right)) {
      return left.map((item, index) => zipValue(item, right[index] ?? item, mapper));
    }
    if (left && right && typeof left === 'object' && typeof right === 'object') {
      const result = {};
      const keys = new Set([...Object.keys(left), ...Object.keys(right)]);
      for (const key of keys) result[key] = zipValue(left[key], right[key] ?? left[key], mapper);
      return result;
    }
    return mapper(Number(left), Number(right));
  }

  function isNumericShape(value) {
    if (typeof value === 'number') return Number.isFinite(value);
    if (Array.isArray(value)) return value.every(isNumericShape);
    if (value && typeof value === 'object') return Object.values(value).every(isNumericShape);
    return false;
  }

  function isMatrix(value) {
    return Array.isArray(value) && value.length === 16 && value.every(item => typeof item === 'number');
  }

  function normalizeMatrix(value) {
    if (!isMatrix(value)) return value;
    const homogeneousScale = Math.abs(value[15]) > 1e-9 ? value[15] : 1;
    const normalized = value.map(item => Number.isFinite(item) ? item / homogeneousScale : 0);
    normalized[15] = 1;
    return normalized;
  }

  function dot3(left, right) {
    return left[0] * right[0] + left[1] * right[1] + left[2] * right[2];
  }

  function cross3(left, right) {
    return [
      left[1] * right[2] - left[2] * right[1],
      left[2] * right[0] - left[0] * right[2],
      left[0] * right[1] - left[1] * right[0],
    ];
  }

  function length3(value) {
    return Math.sqrt(dot3(value, value));
  }

  function scale3(value, scale) {
    return value.map(component => component * scale);
  }

  function subtract3(left, right) {
    return left.map((component, index) => component - right[index]);
  }

  function normalizeQuaternion(value) {
    const length = Math.hypot(value[0], value[1], value[2], value[3]);
    return length > 1e-9
      ? value.map(component => component / length)
      : [0, 0, 0, 1];
  }

  function quaternionFromBasis(xAxis, yAxis, zAxis) {
    const m00 = xAxis[0];
    const m01 = yAxis[0];
    const m02 = zAxis[0];
    const m10 = xAxis[1];
    const m11 = yAxis[1];
    const m12 = zAxis[1];
    const m20 = xAxis[2];
    const m21 = yAxis[2];
    const m22 = zAxis[2];
    const trace = m00 + m11 + m22;
    let quaternion;

    if (trace > 0) {
      const scale = Math.sqrt(trace + 1) * 2;
      quaternion = [
        (m21 - m12) / scale,
        (m02 - m20) / scale,
        (m10 - m01) / scale,
        scale / 4,
      ];
    } else if (m00 > m11 && m00 > m22) {
      const scale = Math.sqrt(1 + m00 - m11 - m22) * 2;
      quaternion = [
        scale / 4,
        (m01 + m10) / scale,
        (m02 + m20) / scale,
        (m21 - m12) / scale,
      ];
    } else if (m11 > m22) {
      const scale = Math.sqrt(1 + m11 - m00 - m22) * 2;
      quaternion = [
        (m01 + m10) / scale,
        scale / 4,
        (m12 + m21) / scale,
        (m02 - m20) / scale,
      ];
    } else {
      const scale = Math.sqrt(1 + m22 - m00 - m11) * 2;
      quaternion = [
        (m02 + m20) / scale,
        (m12 + m21) / scale,
        scale / 4,
        (m10 - m01) / scale,
      ];
    }
    return normalizeQuaternion(quaternion);
  }

  function basisFromQuaternion(value) {
    const [x, y, z, w] = normalizeQuaternion(value);
    const xx = x * x;
    const yy = y * y;
    const zz = z * z;
    const xy = x * y;
    const xz = x * z;
    const yz = y * z;
    const xw = x * w;
    const yw = y * w;
    const zw = z * w;
    return [
      [1 - 2 * (yy + zz), 2 * (xy + zw), 2 * (xz - yw)],
      [2 * (xy - zw), 1 - 2 * (xx + zz), 2 * (yz + xw)],
      [2 * (xz + yw), 2 * (yz - xw), 1 - 2 * (xx + yy)],
    ];
  }

  function decomposeMatrix(value) {
    if (!isMatrix(value)) return null;
    const matrix = normalizeMatrix(value);

    // React Native transform matrices are column-major. Perspective matrices
    // cannot be represented by the affine rotation/scale/translation model.
    if (Math.abs(matrix[3]) > 1e-7 || Math.abs(matrix[7]) > 1e-7 || Math.abs(matrix[11]) > 1e-7) {
      return null;
    }

    let xAxis = [matrix[0], matrix[1], matrix[2]];
    let yAxis = [matrix[4], matrix[5], matrix[6]];
    let zAxis = [matrix[8], matrix[9], matrix[10]];
    let scaleX = length3(xAxis);
    if (scaleX < 1e-9) return null;
    xAxis = scale3(xAxis, 1 / scaleX);

    let skewXY = dot3(xAxis, yAxis);
    yAxis = subtract3(yAxis, scale3(xAxis, skewXY));
    const scaleY = length3(yAxis);
    if (scaleY < 1e-9) return null;
    yAxis = scale3(yAxis, 1 / scaleY);
    skewXY /= scaleY;

    let skewXZ = dot3(xAxis, zAxis);
    zAxis = subtract3(zAxis, scale3(xAxis, skewXZ));
    let skewYZ = dot3(yAxis, zAxis);
    zAxis = subtract3(zAxis, scale3(yAxis, skewYZ));
    const scaleZ = length3(zAxis);
    if (scaleZ < 1e-9) return null;
    zAxis = scale3(zAxis, 1 / scaleZ);
    skewXZ /= scaleZ;
    skewYZ /= scaleZ;

    if (dot3(xAxis, cross3(yAxis, zAxis)) < 0) {
      scaleX = -scaleX;
      xAxis = scale3(xAxis, -1);
      skewXY = -skewXY;
      skewXZ = -skewXZ;
    }

    return {
      translation: [matrix[12], matrix[13], matrix[14]],
      scale: [scaleX, scaleY, scaleZ],
      skew: [skewXY, skewXZ, skewYZ],
      rotation: quaternionFromBasis(xAxis, yAxis, zAxis),
    };
  }

  function composeMatrix(parts) {
    const [xAxis, yAxis, zAxis] = basisFromQuaternion(parts.rotation);
    const [scaleX, scaleY, scaleZ] = parts.scale;
    const [skewXY, skewXZ, skewYZ] = parts.skew;
    const column0 = scale3(xAxis, scaleX);
    const column1 = scale3([
      xAxis[0] * skewXY + yAxis[0],
      xAxis[1] * skewXY + yAxis[1],
      xAxis[2] * skewXY + yAxis[2],
    ], scaleY);
    const column2 = scale3([
      xAxis[0] * skewXZ + yAxis[0] * skewYZ + zAxis[0],
      xAxis[1] * skewXZ + yAxis[1] * skewYZ + zAxis[1],
      xAxis[2] * skewXZ + yAxis[2] * skewYZ + zAxis[2],
    ], scaleZ);
    return [
      column0[0], column0[1], column0[2], 0,
      column1[0], column1[1], column1[2], 0,
      column2[0], column2[1], column2[2], 0,
      parts.translation[0], parts.translation[1], parts.translation[2], 1,
    ];
  }

  function slerpQuaternion(from, to, progress) {
    let start = normalizeQuaternion(from);
    let end = normalizeQuaternion(to);
    let cosine = start.reduce((sum, component, index) => sum + component * end[index], 0);
    if (cosine < 0) {
      end = end.map(component => -component);
      cosine = -cosine;
    }
    if (cosine > 0.9995) {
      return normalizeQuaternion(start.map((component, index) => component + (end[index] - component) * progress));
    }

    const angle = Math.acos(Math.min(1, Math.max(-1, cosine)));
    const sine = Math.sin(angle);
    const fromWeight = Math.sin((1 - progress) * angle) / sine;
    const toWeight = Math.sin(progress * angle) / sine;
    return normalizeQuaternion(start.map((component, index) => component * fromWeight + end[index] * toWeight));
  }

  function matrixPartsToVector(parts) {
    return [
      ...parts.translation,
      ...parts.scale,
      ...parts.skew,
      ...parts.rotation,
    ];
  }

  function matrixVectorToParts(value) {
    return {
      translation: value.slice(0, 3),
      scale: value.slice(3, 6),
      skew: value.slice(6, 9),
      rotation: normalizeQuaternion(value.slice(9, 13)),
    };
  }

  function alignMatrixVectors(from, to) {
    const startRotation = from.slice(9, 13);
    const targetRotation = to.slice(9, 13);
    const dot = startRotation.reduce((sum, component, index) => sum + component * targetRotation[index], 0);
    if (dot >= 0) return to;
    return [...to.slice(0, 9), ...targetRotation.map(component => -component)];
  }

  function mixMatrix(from, to, progress) {
    const fromParts = decomposeMatrix(from);
    const toParts = decomposeMatrix(to);
    if (!fromParts || !toParts) return null;
    return composeMatrix({
      translation: fromParts.translation.map((component, index) => component + (toParts.translation[index] - component) * progress),
      scale: fromParts.scale.map((component, index) => component + (toParts.scale[index] - component) * progress),
      skew: fromParts.skew.map((component, index) => component + (toParts.skew[index] - component) * progress),
      rotation: slerpQuaternion(fromParts.rotation, toParts.rotation, progress),
    });
  }

  function normalizeAnimatable(value) {
    if (isMatrix(value)) return normalizeMatrix(value);
    if (Array.isArray(value)) return value.map(normalizeAnimatable);
    if (value && typeof value === 'object') {
      const result = {};
      for (const key of Object.keys(value)) result[key] = normalizeAnimatable(value[key]);
      return result;
    }
    return value;
  }

  function clampShape(value, minimum, maximum) {
    if (typeof value === 'number') {
      if (minimum !== undefined) value = Math.max(Number(minimum), value);
      if (maximum !== undefined) value = Math.min(Number(maximum), value);
      return value;
    }
    if (Array.isArray(value)) return value.map(item => clampShape(item, minimum, maximum));
    if (value && typeof value === 'object') {
      const result = {};
      for (const key of Object.keys(value)) result[key] = clampShape(value[key], minimum, maximum);
      return result;
    }
    return value;
  }

  function parseColor(value) {
    if (typeof value !== 'string') return null;
    const hex = value.match(/^#([0-9a-f]{6}|[0-9a-f]{8})$/i);
    if (hex) {
      const digits = hex[1];
      return [
        parseInt(digits.slice(0, 2), 16),
        parseInt(digits.slice(2, 4), 16),
        parseInt(digits.slice(4, 6), 16),
        digits.length === 8 ? parseInt(digits.slice(6, 8), 16) : 255,
      ];
    }
    const rgba = value.match(/^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)$/i);
    return rgba ? [Number(rgba[1]), Number(rgba[2]), Number(rgba[3]), Math.round(Number(rgba[4] ?? 1) * 255)] : null;
  }

  function mixValue(from, to, progress) {
    if (typeof from === 'number' && typeof to === 'number') return from + (to - from) * progress;
    if (isMatrix(from) && isMatrix(to)) {
      const mixedMatrix = mixMatrix(from, to, progress);
      if (mixedMatrix) return mixedMatrix;
      return normalizeMatrix(from.map((item, index) => mixValue(item, to[index], progress)));
    }
    if (Array.isArray(from) && Array.isArray(to)) {
      const mixed = from.map((item, index) => mixValue(item, to[index] ?? item, progress));
      return mixed;
    }
    if (from && to && typeof from === 'object' && typeof to === 'object') {
      const result = {};
      const keys = new Set([...Object.keys(from), ...Object.keys(to)]);
      for (const key of keys) result[key] = mixValue(from[key], to[key] ?? from[key], progress);
      return result;
    }
    const fromColor = parseColor(from);
    const toColor = parseColor(to);
    if (fromColor && toColor) {
      const channels = fromColor.map((channel, index) => Math.round(channel + (toColor[index] - channel) * progress));
      return `rgba(${channels[0]},${channels[1]},${channels[2]},${channels[3] / 255})`;
    }
    if (typeof from === 'string' && typeof to === 'string') {
      const fromUnit = from.match(/^(-?[\d.]+)(.*)$/);
      const toUnit = to.match(/^(-?[\d.]+)(.*)$/);
      if (fromUnit && toUnit && fromUnit[2] === toUnit[2]) {
        return `${Number(fromUnit[1]) + (Number(toUnit[1]) - Number(fromUnit[1])) * progress}${fromUnit[2]}`;
      }
    }
    return progress < 1 ? from : to;
  }

  function easingValue(easing, progress) {
    const value = Math.min(1, Math.max(0, progress));
    if (!easing) return value * value * (3 - 2 * value);
    if (typeof easing === 'function') return Number(easing(value));
    if (easing.metadata || easing.code) {
      const worklet = compileSerializedWorklet(easing);
      return worklet ? Number(worklet.call(worklet, value)) : value;
    }
    const type = easing.type;
    if (type === 'linear') return value;
    if (type === 'quad') return value * value;
    if (type === 'cubic') return value * value * value;
    if (type === 'poly') return value ** Number(easing.power ?? 2);
    if (type === 'sin') return 1 - Math.cos((value * Math.PI) / 2);
    if (type === 'circle') return 1 - Math.sqrt(Math.max(0, 1 - value * value));
    if (type === 'exp') return value === 0 ? 0 : 2 ** (10 * (value - 1));
    if (type === 'steps') {
      const count = Math.max(1, Number(easing.count ?? 1));
      return (easing.roundToNextStep ? Math.ceil(value * count) : Math.floor(value * count)) / count;
    }
    if (type === 'in') return easingValue(easing.easing, value);
    if (type === 'out') return 1 - easingValue(easing.easing, 1 - value);
    if (type === 'inOut') return value < 0.5
      ? easingValue(easing.easing, value * 2) / 2
      : 1 - easingValue(easing.easing, (1 - value) * 2) / 2;
    return value * value * (3 - 2 * value);
  }

  function shouldReduce(descriptor, systemReducedMotion) {
    const setting = descriptor?.config?.reduceMotion;
    return setting === 'always' || (setting !== 'never' && systemReducedMotion);
  }

  function createAnimationState(descriptor, fromValue, now, systemReducedMotion) {
    descriptor = globalThis.__spotifyplusReviveShareable(descriptor);
    if (!descriptor || descriptor.__spotifyPlusAnimation !== true) {
      throw new TypeError('Invalid SpotifyPlus animation descriptor');
    }

    const state = {
      descriptor,
      fromValue,
      current: fromValue,
      startedAt: now,
      lastTimestamp: now,
      finished: false,
      callbackCalled: false,
      reduced: shouldReduce(descriptor, systemReducedMotion),
    };

    if (state.reduced && descriptor.toValue !== undefined) {
      state.current = descriptor.toValue;
      state.finished = true;
      return state;
    }

    if (descriptor.type === 'spring') {
      state.velocity = mapValue(fromValue, () => Number(descriptor.config?.velocity ?? 0));
      const fromMatrix = decomposeMatrix(fromValue);
      const toMatrix = decomposeMatrix(descriptor.toValue);
      if (fromMatrix && toMatrix) {
        const fromVector = matrixPartsToVector(fromMatrix);
        const targetVector = alignMatrixVectors(fromVector, matrixPartsToVector(toMatrix));
        state.matrixSpring = {
          from: fromVector,
          current: fromVector.slice(),
          target: targetVector,
        };
        state.velocity = fromVector.map(() => Number(descriptor.config?.velocity ?? 0));
      }
    } else if (descriptor.type === 'decay') {
      state.velocity = Number(descriptor.config?.velocity ?? 0) * Number(descriptor.config?.velocityFactor ?? 1);
    } else if (descriptor.type === 'delay') {
      state.delayUntil = now + Math.max(0, Number(descriptor.config?.delayMs ?? 0));
    } else if (descriptor.type === 'sequence') {
      state.childIndex = 0;
    } else if (descriptor.type === 'repeat') {
      state.iteration = 0;
      state.repeatFrom = fromValue;
    } else if (descriptor.type === 'custom') {
      const factory = compileSerializedWorklet(descriptor.config?.factory);
      state.custom = factory ? factory.call(factory) : null;
      if (state.custom?.onStart) state.custom.onStart(state.custom, fromValue, now, null);
    }
    return state;
  }

  function finishAnimation(state, finished) {
    if (state.callbackCalled) return;
    state.callbackCalled = true;
    try {
      callSerializedWorklet(state.descriptor.callback, [finished, state.current]);
    } finally {
      callSerializedWorklet(state.descriptor.__spotifyPlusCleanup, [finished]);
    }
  }

  function stepAnimation(state, now, systemReducedMotion) {
    if (state.finished) {
      finishAnimation(state, true);
      return state;
    }

    const descriptor = state.descriptor;
    const config = descriptor.config ?? {};
    const deltaMs = Math.max(0, Math.min(64, now - state.lastTimestamp));
    state.lastTimestamp = now;

    if (state.reduced || shouldReduce(descriptor, systemReducedMotion)) {
      if (descriptor.toValue !== undefined) state.current = descriptor.toValue;
      state.finished = true;
    } else if (descriptor.type === 'timing') {
      const duration = Math.max(0, Number(config.duration ?? 300));
      const progress = duration === 0 ? 1 : Math.min(1, (now - state.startedAt) / duration);
      state.current = mixValue(state.fromValue, descriptor.toValue, easingValue(config.easing, progress));
      state.finished = progress >= 1;
    } else if (descriptor.type === 'spring') {
      if (config.duration !== undefined) {
        const duration = Math.max(1, Number(config.duration ?? 550));
        const dampingRatio = Math.max(0.001, Number(config.dampingRatio ?? 0.5));
        const progress = Math.min(1, (now - state.startedAt) / duration);
        let response;
        if (dampingRatio < 1) {
          const angular = 10 * Math.sqrt(Math.max(0.0001, 1 - dampingRatio * dampingRatio));
          response = 1 - Math.exp(-10 * dampingRatio * progress) * (
            Math.cos(angular * progress) +
            dampingRatio / Math.sqrt(1 - dampingRatio * dampingRatio) * Math.sin(angular * progress)
          );
        } else {
          response = 1 - Math.exp(-10 * progress) * (1 + 10 * progress);
        }
        state.current = normalizeAnimatable(mixValue(state.fromValue, descriptor.toValue, response));
        if (config.clamp) {
          state.current = clampShape(state.current, config.clamp.min, config.clamp.max);
        }
        state.finished = progress >= 1;
        if (state.finished) {
          state.current = normalizeAnimatable(descriptor.toValue);
          if (config.clamp) {
            state.current = clampShape(state.current, config.clamp.min, config.clamp.max);
          }
        }
      } else if (!isNumericShape(state.fromValue) || !isNumericShape(descriptor.toValue)) {
        const duration = Math.max(1, Number(config.duration ?? 550));
        const progress = Math.min(1, (now - state.startedAt) / duration);
        state.current = normalizeAnimatable(
          mixValue(state.fromValue, descriptor.toValue, 1 - Math.exp(-6 * progress)),
        );
        state.finished = progress >= 1;
      } else {
        const dt = deltaMs / 1000;
        const mass = Math.max(0.001, Number(config.mass ?? 1));
        const stiffness = Math.max(0, Number(config.stiffness ?? 100));
        const damping = Math.max(0, Number(config.damping ?? 10));
        const currentShape = state.matrixSpring ? state.matrixSpring.current : state.current;
        const targetShape = state.matrixSpring ? state.matrixSpring.target : descriptor.toValue;
        const fromShape = state.matrixSpring ? state.matrixSpring.from : state.fromValue;
        const acceleration = zipValue(currentShape, targetShape, (current, next) => stiffness * (next - current) / mass);
        state.velocity = zipValue(state.velocity, acceleration, (velocity, force) => velocity + force * dt);
        state.velocity = mapValue(state.velocity, velocity => velocity * Math.exp(-damping * dt / mass));
        let nextShape = zipValue(currentShape, state.velocity, (current, velocity) => current + velocity * dt);
        if (state.matrixSpring) {
          nextShape = [
            ...nextShape.slice(0, 9),
            ...normalizeQuaternion(nextShape.slice(9, 13)),
          ];
          state.matrixSpring.current = nextShape;
          state.current = composeMatrix(matrixVectorToParts(nextShape));
        } else {
          state.current = normalizeAnimatable(nextShape);
        }

        let maxVelocity = 0;
        let maxDistance = 0;
        const inspect = (current, next, velocity) => {
          if (Array.isArray(current)) {
            current.forEach((item, index) => inspect(item, next[index], velocity[index]));
          } else {
            maxVelocity = Math.max(maxVelocity, Math.abs(Number(velocity)));
            maxDistance = Math.max(maxDistance, Math.abs(Number(next) - Number(current)));
          }
        };
        inspect(nextShape, targetShape, state.velocity);
        const threshold = Math.max(0.000001, Number(config.energyThreshold ?? 0.001));
        state.finished = maxVelocity < threshold * 10 && maxDistance < threshold;
        if (config.overshootClamping) {
          const clampOvershoot = (current, from, next) => {
            if (Array.isArray(current)) {
              return current.map((item, index) => clampOvershoot(item, from[index], next[index]));
            }
            if (current && typeof current === 'object') {
              const result = {};
              for (const key of Object.keys(current)) {
                result[key] = clampOvershoot(current[key], from[key], next[key]);
              }
              return result;
            }
            const lower = Math.min(Number(from), Number(next));
            const upper = Math.max(Number(from), Number(next));
            return Math.min(upper, Math.max(lower, Number(current)));
          };
          nextShape = clampOvershoot(nextShape, fromShape, targetShape);
          if (state.matrixSpring) {
            nextShape = [
              ...nextShape.slice(0, 9),
              ...normalizeQuaternion(nextShape.slice(9, 13)),
            ];
            state.matrixSpring.current = nextShape;
            state.current = composeMatrix(matrixVectorToParts(nextShape));
          } else {
            state.current = nextShape;
          }
        }
        if (state.finished) state.current = descriptor.toValue;
      }
    } else if (descriptor.type === 'decay') {
      const deceleration = Math.min(0.999999, Math.max(0, Number(config.deceleration ?? 0.998)));
      state.velocity *= deceleration ** (deltaMs / 16.6667);
      state.current = Number(state.current) + state.velocity * (deltaMs / 1000);
      if (Array.isArray(config.clamp)) {
        const min = Number(config.clamp[0]);
        const max = Number(config.clamp[1]);
        if (state.current <= min || state.current >= max) {
          const boundary = state.current <= min ? min : max;
          if (config.rubberBandEffect) {
            const factor = Math.min(1, Math.max(0, Number(config.rubberBandFactor ?? 0.6)));
            const overshoot = state.current - boundary;
            state.current = boundary + overshoot * factor;
            state.velocity = state.velocity * factor - overshoot * factor * 30 * (deltaMs / 1000);
            if (Math.abs(overshoot) < 0.1 && Math.abs(state.velocity) < 0.1) {
              state.current = boundary;
              state.finished = true;
            }
          } else {
            state.current = boundary;
            state.finished = true;
          }
        }
      }
      if (Math.abs(state.velocity) < 0.1) {
        if (Array.isArray(config.clamp)) {
          state.current = Math.min(Number(config.clamp[1]), Math.max(Number(config.clamp[0]), state.current));
        }
        state.finished = true;
      }
    } else if (descriptor.type === 'delay') {
      if (now >= state.delayUntil) {
        if (!state.child) state.child = createAnimationState(descriptor.children?.[0], state.current, now, systemReducedMotion);
        stepAnimation(state.child, now, systemReducedMotion);
        state.current = state.child.current;
        state.finished = state.child.finished;
      }
    } else if (descriptor.type === 'sequence') {
      const children = descriptor.children ?? [];
      if (state.childIndex >= children.length) {
        state.finished = true;
      } else {
        if (!state.child) state.child = createAnimationState(children[state.childIndex], state.current, now, systemReducedMotion);
        stepAnimation(state.child, now, systemReducedMotion);
        state.current = state.child.current;
        if (state.child.finished) {
          state.childIndex += 1;
          state.child = null;
          state.finished = state.childIndex >= children.length;
        }
      }
    } else if (descriptor.type === 'repeat') {
      const childDescriptor = descriptor.children?.[0];
      const repetitions = Number(config.numberOfReps ?? 2);
      if (!childDescriptor || (repetitions >= 0 && state.iteration >= repetitions)) {
        state.finished = true;
      } else {
        if (!state.child) {
          const reverse = !!config.reverse && state.iteration % 2 === 1;
          const repeated = reverse ? { ...childDescriptor, toValue: state.repeatFrom } : childDescriptor;
          state.child = createAnimationState(repeated, state.current, now, systemReducedMotion);
        }
        stepAnimation(state.child, now, systemReducedMotion);
        state.current = state.child.current;
        if (state.child.finished) {
          state.iteration += 1;
          state.child = null;
          state.finished = repetitions >= 0 && state.iteration >= repetitions;
        }
      }
    } else if (descriptor.type === 'clamp') {
      if (!state.child) state.child = createAnimationState(descriptor.children?.[0], state.current, now, systemReducedMotion);
      stepAnimation(state.child, now, systemReducedMotion);
      state.current = state.child.current;
      if (typeof state.current === 'number') {
        if (config.min !== undefined) state.current = Math.max(Number(config.min), state.current);
        if (config.max !== undefined) state.current = Math.min(Number(config.max), state.current);
      }
      state.finished = state.child.finished;
    } else if (descriptor.type === 'custom' && state.custom?.onFrame) {
      state.finished = !!state.custom.onFrame(state.custom, now);
      state.current = state.custom.current;
    } else {
      if (descriptor.toValue !== undefined) state.current = descriptor.toValue;
      state.finished = true;
    }

    if (state.finished) finishAnimation(state, true);
    return state;
  }

  globalThis.__spotifyplusCreateAnimationState = createAnimationState;
  globalThis.__spotifyplusStepAnimationState = stepAnimation;
  globalThis.__spotifyplusCancelAnimationState = function (state) {
    if (!state) return;
    state.finished = true;
    finishAnimation(state, false);
  };
})();
)SPOTIFYPLUS";

using Clock = std::chrono::steady_clock;

std::string MakeContextKey(const std::string& scriptId, uint64_t generation)
{
    return scriptId + KEY_SEPARATOR + std::to_string(generation);
}

std::string MakeSharedKey(const std::string& contextKey, const std::string& sharedValueId)
{
    return contextKey + KEY_SEPARATOR + sharedValueId;
}

std::string MakeMapperTargetKey(const std::string& surfaceId, int32_t nodeId)
{
    return surfaceId + KEY_SEPARATOR + std::to_string(nodeId);
}

bool LooksLikeAnimationDescriptor(const std::string& json)
{
    return json.find("\"__spotifyPlusAnimation\":true") != std::string::npos;
}

const char* FindJsonValue(const std::string& json, const char* key)
{
    const std::string token = "\"" + std::string(key) + "\"";
    const size_t name = json.find(token);
    if (name == std::string::npos) return nullptr;

    size_t cursor = json.find(':', name + token.size());
    if (cursor == std::string::npos) return nullptr;
    cursor++;
    while (cursor < json.size() && std::isspace(static_cast<unsigned char>(json[cursor]))) cursor++;
    return cursor < json.size() ? json.c_str() + cursor : nullptr;
}

double JsonNumber(const std::string& json, const char* key, double fallback)
{
    const char* value = FindJsonValue(json, key);
    if (!value) return fallback;

    char* end = nullptr;
    const double result = std::strtod(value, &end);
    return end != value && std::isfinite(result) ? result : fallback;
}

bool JsonStringEquals(const std::string& json, const char* key, const char* expected)
{
    const char* value = FindJsonValue(json, key);
    if (!value || *value != '"') return false;
    value++;

    const size_t length = std::strlen(expected);
    return std::strncmp(value, expected, length) == 0 && value[length] == '"';
}

bool JsonBoolean(const std::string& json, const char* key, bool fallback)
{
    const char* value = FindJsonValue(json, key);
    if (!value) return fallback;
    if (std::strncmp(value, "true", 4) == 0) return true;
    if (std::strncmp(value, "false", 5) == 0) return false;
    return fallback;
}

std::string TransformSourceValue(
    const std::string& sourceId,
    const std::string& configJson,
    const std::string& rawValueJson)
{
    if (sourceId.rfind("sensor:", 0) == 0 &&
        JsonBoolean(configJson, "adjustToInterfaceOrientation", false))
    {
        double x = JsonNumber(rawValueJson, "x", 0.0);
        double y = JsonNumber(rawValueJson, "y", 0.0);
        const double z = JsonNumber(rawValueJson, "z", 0.0);
        const int orientation = static_cast<int>(JsonNumber(rawValueJson, "interfaceOrientation", 0.0));
        const double originalX = x;
        const double originalY = y;

        if (orientation == 90)
        {
            x = -originalY;
            y = originalX;
        }
        else if (orientation == 180)
        {
            x = -originalX;
            y = -originalY;
        }
        else if (orientation == 270)
        {
            x = originalY;
            y = -originalX;
        }

        std::string adjusted =
            "{\"x\":" + std::to_string(x) +
            ",\"y\":" + std::to_string(y) +
            ",\"z\":" + std::to_string(z) +
            ",\"interfaceOrientation\":" + std::to_string(orientation);
        if (sourceId == "sensor:5")
        {
            constexpr double PI = 3.14159265358979323846;
            const double angle = -static_cast<double>(orientation) * PI / 180.0;
            const double cosine = std::cos(angle / 2.0);
            const double sine = std::sin(angle / 2.0);
            const double qw = JsonNumber(rawValueJson, "qw", 1.0);
            const double qx = JsonNumber(rawValueJson, "qx", 0.0);
            const double qy = JsonNumber(rawValueJson, "qy", 0.0);
            const double qz = JsonNumber(rawValueJson, "qz", 0.0);
            double yaw = JsonNumber(rawValueJson, "yaw", 0.0) + angle;
            while (yaw > PI) yaw -= PI * 2.0;
            while (yaw < -PI) yaw += PI * 2.0;

            adjusted +=
                ",\"qw\":" + std::to_string(cosine * qw - sine * qz) +
                ",\"qx\":" + std::to_string(cosine * qx - sine * qy) +
                ",\"qy\":" + std::to_string(cosine * qy + sine * qx) +
                ",\"qz\":" + std::to_string(cosine * qz + sine * qw) +
                ",\"yaw\":" + std::to_string(yaw) +
                ",\"pitch\":" + std::to_string(JsonNumber(rawValueJson, "pitch", 0.0)) +
                ",\"roll\":" + std::to_string(JsonNumber(rawValueJson, "roll", 0.0));
        }
        return adjusted + "}";
    }

    if (sourceId != "playbackClock") return rawValueJson;

    char* end = nullptr;
    double positionMs = std::strtod(rawValueJson.c_str(), &end);
    if (end == rawValueJson.c_str() || !std::isfinite(positionMs)) return rawValueJson;

    // Match the existing animation source semantics: offset is expressed in
    // milliseconds and is applied before an optional seconds conversion.
    positionMs += JsonNumber(configJson, "offset", 0.0);
    const double value = JsonStringEquals(configJson, "unit", "seconds")
                             ? positionMs / 1000.0
                             : positionMs;
    return std::to_string(value);
}

std::string ToStdString(v8::Isolate* isolate, v8::Local<v8::Value> value)
{
    if (value.IsEmpty()) return "";
    v8::String::Utf8Value utf8(isolate, value);
    return *utf8 ? std::string(*utf8, utf8.length()) : std::string();
}

v8::Local<v8::String> ToV8String(v8::Isolate* isolate, const std::string& value)
{
    return v8::String::NewFromUtf8(
               isolate,
               value.c_str(),
               v8::NewStringType::kNormal,
               static_cast<int>(value.size()))
        .ToLocalChecked();
}

std::string EscapeJson(const std::string& value)
{
    std::string escaped;
    escaped.reserve(value.size() + 8);

    for (unsigned char character : value)
    {
        switch (character)
        {
            case '\\':
                escaped += "\\\\";
                break;
            case '"':
                escaped += "\\\"";
                break;
            case '\b':
                escaped += "\\b";
                break;
            case '\f':
                escaped += "\\f";
                break;
            case '\n':
                escaped += "\\n";
                break;
            case '\r':
                escaped += "\\r";
                break;
            case '\t':
                escaped += "\\t";
                break;
            default:
                if (character < 0x20)
                {
                    static constexpr char HEX[] = "0123456789abcdef";
                    escaped += "\\u00";
                    escaped += HEX[(character >> 4) & 0x0f];
                    escaped += HEX[character & 0x0f];
                }
                else
                {
                    escaped += static_cast<char>(character);
                }
        }
    }

    return escaped;
}

std::string EscapePointerSegment(const std::string& value)
{
    std::string escaped;
    escaped.reserve(value.size());
    for (char character : value)
    {
        if (character == '~')
        {
            escaped += "~0";
        }
        else if (character == '/')
        {
            escaped += "~1";
        }
        else
        {
            escaped += character;
        }
    }
    return escaped;
}

char* CopyForAbi(const std::string& value)
{
    auto* copy = static_cast<char*>(std::malloc(value.size() + 1));
    if (!copy) return nullptr;
    std::memcpy(copy, value.data(), value.size());
    copy[value.size()] = '\0';
    return copy;
}

class MainThreadTaskRunner final : public v8::TaskRunner
{
   public:
    explicit MainThreadTaskRunner(std::function<void()> schedule) : schedule_(std::move(schedule)) {}

    void PostTask(std::unique_ptr<v8::Task> task) override
    {
        Enqueue(std::move(task), Clock::now());
    }

    void PostNonNestableTask(std::unique_ptr<v8::Task> task) override
    {
        Enqueue(std::move(task), Clock::now());
    }

    void PostDelayedTask(std::unique_ptr<v8::Task> task, double delayInSeconds) override
    {
        const auto delay = std::chrono::duration_cast<Clock::duration>(
            std::chrono::duration<double>(std::max(0.0, delayInSeconds)));
        Enqueue(std::move(task), Clock::now() + delay);
    }

    void PostNonNestableDelayedTask(std::unique_ptr<v8::Task> task, double delayInSeconds) override
    {
        PostDelayedTask(std::move(task), delayInSeconds);
    }

    void PostIdleTask(std::unique_ptr<v8::IdleTask>) override {}

    bool IdleTasksEnabled() override
    {
        return false;
    }

    bool NonNestableTasksEnabled() const override
    {
        return true;
    }

    bool NonNestableDelayedTasksEnabled() const override
    {
        return true;
    }

    void DrainDueTasks()
    {
        std::vector<std::unique_ptr<v8::Task>> ready;
        const auto now = Clock::now();

        {
            std::lock_guard<std::mutex> lock(mutex_);
            if (!active_) return;

            auto iterator = tasks_.begin();
            while (iterator != tasks_.end())
            {
                if (iterator->due <= now)
                {
                    ready.push_back(std::move(iterator->task));
                    iterator = tasks_.erase(iterator);
                }
                else
                {
                    ++iterator;
                }
            }
        }

        for (auto& task : ready)
        {
            if (task) task->Run();
        }
    }

    int64_t NextDelayMillis() const
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!active_ || tasks_.empty()) return -1;

        auto earliest = tasks_.front().due;
        for (const PendingTask& task : tasks_)
        {
            earliest = std::min(earliest, task.due);
        }

        const auto remaining = earliest - Clock::now();
        if (remaining <= Clock::duration::zero()) return 0;

        const double milliseconds = std::chrono::duration<double, std::milli>(remaining).count();
        return std::max<int64_t>(1, static_cast<int64_t>(std::ceil(milliseconds)));
    }

    void Deactivate()
    {
        std::lock_guard<std::mutex> lock(mutex_);
        active_ = false;
        tasks_.clear();
    }

   private:
    struct PendingTask
    {
        Clock::time_point due;
        std::unique_ptr<v8::Task> task;
    };

    void Enqueue(std::unique_ptr<v8::Task> task, Clock::time_point due)
    {
        if (!task) return;

        {
            std::lock_guard<std::mutex> lock(mutex_);
            if (!active_) return;
            tasks_.push_back(PendingTask{due, std::move(task)});
        }

        schedule_();
    }

    mutable std::mutex mutex_;
    std::vector<PendingTask> tasks_;
    std::function<void()> schedule_;
    bool active_ = true;
};

class MainThreadPlatformDelegate final : public node::IsolatePlatformDelegate
{
   public:
    explicit MainThreadPlatformDelegate(std::shared_ptr<MainThreadTaskRunner> taskRunner)
        : taskRunner_(std::move(taskRunner))
    {
    }

    std::shared_ptr<v8::TaskRunner> GetForegroundTaskRunner() override
    {
        return taskRunner_;
    }

    bool IdleTasksEnabled() override
    {
        return false;
    }

   private:
    std::shared_ptr<MainThreadTaskRunner> taskRunner_;
};

enum class CommandType
{
    CreateContext,
    DisposeContext,
    DisposeScript,
    RegisterWorklet,
    UnregisterWorklet,
    InstallGlobals,
    ScheduleWorklet,
    RegisterMapper,
    UnregisterMapper,
    DeleteSharedValue,
    SharedValueChanged,
    StartAnimation,
    CancelAnimation,
    SetReducedMotionOverride,
};

struct Command
{
    CommandType type;
    std::string scriptId;
    uint64_t generation = 0;
    std::string id;
    std::string secondaryId;
    std::string source;
    std::string payload;
    std::string surfaceId;
    int32_t nodeId = 0;
    int32_t priority = 0;
    bool flag = false;
};

struct WorkletError
{
    std::string scriptId;
    uint64_t generation = 0;
    std::string workletId;
    std::string phase;
    std::string message;
    std::string stack;
};

struct NativeShareable
{
    enum class Kind
    {
        Null,
        Boolean,
        Number,
        String,
        Array,
        Object,
    };

    Kind kind = Kind::Null;
    bool booleanValue = false;
    double numberValue = 0.0;
    std::string stringValue;
    std::vector<NativeShareable> arrayValue;
    std::vector<std::pair<std::string, NativeShareable>> objectValue;
};

std::string NativeShareableToJson(const NativeShareable& value)
{
    switch (value.kind)
    {
        case NativeShareable::Kind::Null:
            return "null";
        case NativeShareable::Kind::Boolean:
            return value.booleanValue ? "true" : "false";
        case NativeShareable::Kind::Number:
        {
            if (!std::isfinite(value.numberValue)) return "null";
            std::ostringstream output;
            output << std::setprecision(17) << value.numberValue;
            return output.str();
        }
        case NativeShareable::Kind::String:
            return "\"" + EscapeJson(value.stringValue) + "\"";
        case NativeShareable::Kind::Array:
        {
            std::string output = "[";
            for (size_t index = 0; index < value.arrayValue.size(); index++)
            {
                if (index > 0) output += ",";
                output += NativeShareableToJson(value.arrayValue[index]);
            }
            return output + "]";
        }
        case NativeShareable::Kind::Object:
        {
            std::string output = "{";
            for (size_t index = 0; index < value.objectValue.size(); index++)
            {
                if (index > 0) output += ",";
                output += "\"" + EscapeJson(value.objectValue[index].first) + "\":";
                output += NativeShareableToJson(value.objectValue[index].second);
            }
            return output + "}";
        }
    }
    return "null";
}

bool NativeShareablesEqual(const NativeShareable& left, const NativeShareable& right)
{
    if (left.kind != right.kind) return false;
    switch (left.kind)
    {
        case NativeShareable::Kind::Null:
            return true;
        case NativeShareable::Kind::Boolean:
            return left.booleanValue == right.booleanValue;
        case NativeShareable::Kind::Number:
            return left.numberValue == right.numberValue ||
                   (std::isnan(left.numberValue) && std::isnan(right.numberValue));
        case NativeShareable::Kind::String:
            return left.stringValue == right.stringValue;
        case NativeShareable::Kind::Array:
            if (left.arrayValue.size() != right.arrayValue.size()) return false;
            for (size_t index = 0; index < left.arrayValue.size(); index++)
            {
                if (!NativeShareablesEqual(left.arrayValue[index], right.arrayValue[index])) return false;
            }
            return true;
        case NativeShareable::Kind::Object:
            if (left.objectValue.size() != right.objectValue.size()) return false;
            for (size_t index = 0; index < left.objectValue.size(); index++)
            {
                if (left.objectValue[index].first != right.objectValue[index].first ||
                    !NativeShareablesEqual(left.objectValue[index].second, right.objectValue[index].second))
                {
                    return false;
                }
            }
            return true;
    }
    return false;
}

struct SharedValue
{
    std::string json;
    std::shared_ptr<const NativeShareable> nativeValue;
    uint64_t version = 0;
};

class WorkletRuntime;

struct ContextHostData
{
    WorkletRuntime* runtime;
    std::string contextKey;
};

struct RegisteredWorklet
{
    std::string id;
    v8::Global<v8::Function> function;
};

struct MapperOutputSnapshot
{
    std::string surfaceId;
    int32_t nodeId = 0;
    std::unordered_map<std::string, std::vector<WorkletViewUpdate>> properties;
};

using MapperOutputCollection = std::unordered_map<std::string, MapperOutputSnapshot>;

struct RegisteredMapper
{
    std::string id;
    std::string workletId;
    std::string surfaceId;
    int32_t nodeId = 0;
    int32_t priority = 0;
    bool runEveryFrame = false;
    bool dirty = true;
    std::unordered_set<std::string> dependencies;
    MapperOutputCollection lastOutputs;
};

struct ContextState
{
    std::string scriptId;
    uint64_t generation = 0;
    std::string key;
    std::unique_ptr<ContextHostData> hostData;
    v8::Global<v8::Context> context;
    std::unordered_map<std::string, RegisteredWorklet> worklets;
    std::unordered_map<std::string, RegisteredMapper> mappers;
    // -1 follows Android, 0 forces animations, 1 always reduces motion.
    int reducedMotionOverride = -1;
};

struct ScheduledExecution
{
    std::string contextKey;
    std::string workletId;
    std::string argsJson;
};

struct SourceBinding
{
    std::string contextKey;
    std::string sharedValueId;
    std::string configJson;
};

struct AnimationInstance
{
    std::string contextKey;
    std::string sharedKey;
    v8::Global<v8::Object> state;
};

class WorkletRuntime
{
   public:
    static WorkletRuntime& Get()
    {
        // The process owns V8. Avoid a static destructor racing Node shutdown.
        static WorkletRuntime* instance = new WorkletRuntime();
        return *instance;
    }

    bool ConfigurePlatform(node::MultiIsolatePlatform* platform)
    {
        if (!platform) return false;

        {
            std::lock_guard<std::mutex> lock(platformMutex_);
            if (platform_ && platform_ != platform)
            {
                PushError({"", 0, "", "configure", "Node's V8 platform changed after initialization", ""});
                return false;
            }

            platform_ = platform;
        }

        ScheduleUi();
        return true;
    }

    bool IsAvailable() const
    {
        std::lock_guard<std::mutex> lock(platformMutex_);
        return platform_ != nullptr;
    }

    bool QueueCreateContext(const std::string& scriptId, uint64_t generation)
    {
        if (scriptId.empty()) return false;
        return Queue(Command{CommandType::CreateContext, scriptId, generation});
    }

    bool QueueDisposeContext(const std::string& scriptId, uint64_t generation)
    {
        if (scriptId.empty()) return false;
        return Queue(Command{CommandType::DisposeContext, scriptId, generation});
    }

    bool QueueDisposeScript(const std::string& scriptId)
    {
        if (scriptId.empty()) return false;
        return Queue(Command{CommandType::DisposeScript, scriptId});
    }

    bool QueueRegisterWorklet(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& workletId,
        const std::string& source,
        const std::string& closureJson)
    {
        if (scriptId.empty() || workletId.empty() || source.empty()) return false;

        Command command{CommandType::RegisterWorklet, scriptId, generation};
        command.id = workletId;
        command.source = source;
        command.payload = closureJson.empty() ? "{}" : closureJson;
        return Queue(std::move(command));
    }

    bool QueueUnregisterWorklet(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& workletId)
    {
        if (scriptId.empty() || workletId.empty()) return false;

        Command command{CommandType::UnregisterWorklet, scriptId, generation};
        command.id = workletId;
        return Queue(std::move(command));
    }

    bool QueueScheduleWorklet(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& workletId,
        const std::string& argsJson)
    {
        if (scriptId.empty() || workletId.empty()) return false;

        Command command{CommandType::ScheduleWorklet, scriptId, generation};
        command.id = workletId;
        command.payload = argsJson.empty() ? "[]" : argsJson;
        return Queue(std::move(command));
    }

    bool QueueInstallGlobals(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& moduleName,
        const std::string& source)
    {
        if (scriptId.empty() || moduleName.empty() || source.empty()) return false;

        Command command{CommandType::InstallGlobals, scriptId, generation};
        command.id = moduleName;
        command.source = source;
        return Queue(std::move(command));
    }

    bool QueueRegisterMapper(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& mapperId,
        const std::string& workletId,
        const std::string& surfaceId,
        int32_t nodeId,
        int32_t priority,
        bool runEveryFrame)
    {
        if (scriptId.empty() || mapperId.empty() || workletId.empty() || surfaceId.empty()) return false;

        Command command{CommandType::RegisterMapper, scriptId, generation};
        command.id = mapperId;
        command.secondaryId = workletId;
        command.surfaceId = surfaceId;
        command.nodeId = nodeId;
        command.priority = priority;
        command.flag = runEveryFrame;
        return Queue(std::move(command));
    }

    bool QueueUnregisterMapper(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& mapperId)
    {
        if (scriptId.empty() || mapperId.empty()) return false;

        Command command{CommandType::UnregisterMapper, scriptId, generation};
        command.id = mapperId;
        return Queue(std::move(command));
    }

    bool SetSharedValue(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& sharedValueId,
        const std::string& valueJson)
    {
        if (scriptId.empty() || sharedValueId.empty() || valueJson.empty()) return false;

        if (LooksLikeAnimationDescriptor(valueJson))
        {
            Command command{CommandType::StartAnimation, scriptId, generation};
            command.id = sharedValueId;
            command.payload = valueJson;
            return Queue(std::move(command));
        }

        const std::string contextKey = MakeContextKey(scriptId, generation);
        const std::string sharedKey = MakeSharedKey(contextKey, sharedValueId);
        bool changed = false;

        {
            std::lock_guard<std::mutex> lock(sharedMutex_);
            SharedValue& value = sharedValues_[sharedKey];
            if (value.json != valueJson)
            {
                value.json = valueJson;
                value.nativeValue.reset();
                value.version++;
                cancelledAnimations_.erase(sharedKey);
                changed = true;
            }
        }

        if (changed) QueueSharedValueChanged(contextKey, sharedKey);
        return true;
    }

    std::string GetSharedValue(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& sharedValueId,
        bool* found) const
    {
        if (found) *found = false;
        if (scriptId.empty() || sharedValueId.empty()) return "";

        const std::string key = MakeSharedKey(MakeContextKey(scriptId, generation), sharedValueId);
        std::string json;
        std::shared_ptr<const NativeShareable> nativeValue;
        {
            std::lock_guard<std::mutex> lock(sharedMutex_);
            auto iterator = sharedValues_.find(key);
            if (iterator == sharedValues_.end()) return "";
            json = iterator->second.json;
            nativeValue = iterator->second.nativeValue;
        }

        if (found) *found = true;
        return nativeValue ? NativeShareableToJson(*nativeValue) : json;
    }

    bool DeleteSharedValue(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& sharedValueId)
    {
        if (scriptId.empty() || sharedValueId.empty()) return false;

        Command command{CommandType::DeleteSharedValue, scriptId, generation};
        command.id = sharedValueId;
        return Queue(std::move(command));
    }

    bool CancelAnimation(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& sharedValueId)
    {
        if (scriptId.empty() || sharedValueId.empty()) return false;
        const std::string key = MakeSharedKey(MakeContextKey(scriptId, generation), sharedValueId);

        {
            std::lock_guard<std::mutex> lock(sharedMutex_);
            if (sharedValues_.find(key) == sharedValues_.end()) return false;
            cancelledAnimations_.insert(key);
        }

        Command command{CommandType::CancelAnimation, scriptId, generation};
        command.id = sharedValueId;
        return Queue(std::move(command));
    }

    bool GetReducedMotion() const
    {
        return systemReducedMotion_.load();
    }

    bool SetReducedMotionOverride(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& modeJson)
    {
        if (scriptId.empty()) return false;

        int mode = -1;
        if (modeJson == "\"always\"" || modeJson == "always")
        {
            mode = 1;
        }
        else if (modeJson == "\"never\"" || modeJson == "never")
        {
            mode = 0;
        }
        else if (modeJson != "null" && modeJson != "\"system\"" && modeJson != "system" && !modeJson.empty())
        {
            return false;
        }

        Command command{CommandType::SetReducedMotionOverride, scriptId, generation};
        command.priority = mode;
        return Queue(std::move(command));
    }

    bool RegisterSource(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& sourceId,
        const std::string& sharedValueId,
        const std::string& configJson)
    {
        if (scriptId.empty() || sourceId.empty() || sharedValueId.empty()) return false;

        const std::string contextKey = MakeContextKey(scriptId, generation);
        std::string latestValue;
        std::string previousConfig;
        const std::string normalizedConfig = configJson.empty() ? "{}" : configJson;
        bool activate = false;

        {
            std::lock_guard<std::mutex> lock(sourceMutex_);
            std::vector<SourceBinding>& bindings = sourceBindings_[sourceId];
            auto existing = std::find_if(
                bindings.begin(),
                bindings.end(),
                [&](const SourceBinding& binding)
                {
                    return binding.contextKey == contextKey && binding.sharedValueId == sharedValueId;
                });

            if (existing == bindings.end())
            {
                bindings.push_back({contextKey, sharedValueId, normalizedConfig});
                activate = true;
            }
            else if (existing->configJson != normalizedConfig)
            {
                previousConfig = existing->configJson;
                existing->configJson = normalizedConfig;
                activate = true;
            }

            auto latest = sourceLatestValues_.find(sourceId);
            if (latest != sourceLatestValues_.end()) latestValue = latest->second;
        }

        if (!previousConfig.empty())
        {
            SpotifyPlusEngine::Get().SetWorkletSourceActive(sourceId, previousConfig, false);
        }
        if (activate)
        {
            SpotifyPlusEngine::Get().SetWorkletSourceActive(sourceId, normalizedConfig, true);
        }

        if (!latestValue.empty())
        {
            SetSharedValue(
                scriptId,
                generation,
                sharedValueId,
                TransformSourceValue(sourceId, normalizedConfig, latestValue));
        }

        ScheduleUi();
        return true;
    }

    bool UnregisterSource(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& sourceId,
        const std::string& sharedValueId)
    {
        if (scriptId.empty() || sourceId.empty() || sharedValueId.empty()) return false;

        const std::string contextKey = MakeContextKey(scriptId, generation);
        std::string removedConfig;
        bool removed = false;
        {
            std::lock_guard<std::mutex> lock(sourceMutex_);
            auto source = sourceBindings_.find(sourceId);
            if (source == sourceBindings_.end()) return false;

            std::vector<SourceBinding>& bindings = source->second;
            auto binding = std::find_if(
                bindings.begin(),
                bindings.end(),
                [&](const SourceBinding& candidate)
                {
                    return candidate.contextKey == contextKey && candidate.sharedValueId == sharedValueId;
                });
            if (binding != bindings.end())
            {
                removedConfig = binding->configJson;
                bindings.erase(binding);
                removed = true;
            }
            if (bindings.empty()) sourceBindings_.erase(source);
        }

        if (removed)
        {
            SpotifyPlusEngine::Get().SetWorkletSourceActive(sourceId, removedConfig, false);
        }
        return removed;
    }

    bool PublishSourceValue(const std::string& sourceId, const std::string& valueJson)
    {
        if (sourceId.empty() || valueJson.empty()) return false;

        if (sourceId == "reducedMotion")
        {
            systemReducedMotion_.store(valueJson == "true" || valueJson == "1");
        }

        std::vector<SourceBinding> bindings;
        {
            std::lock_guard<std::mutex> lock(sourceMutex_);
            sourceLatestValues_[sourceId] = valueJson;
            auto source = sourceBindings_.find(sourceId);
            if (source != sourceBindings_.end()) bindings = source->second;
        }

        for (const SourceBinding& binding : bindings)
        {
            const size_t separator = binding.contextKey.rfind(KEY_SEPARATOR);
            if (separator == std::string::npos) continue;

            const std::string scriptId = binding.contextKey.substr(0, separator);
            const uint64_t generation = std::strtoull(binding.contextKey.c_str() + separator + 1, nullptr, 10);
            SetSharedValue(
                scriptId,
                generation,
                binding.sharedValueId,
                TransformSourceValue(sourceId, binding.configJson, valueJson));
        }

        return true;
    }

    char* TakeErrorJson()
    {
        WorkletError error;

        {
            std::lock_guard<std::mutex> lock(errorMutex_);
            if (errors_.empty()) return nullptr;
            error = std::move(errors_.front());
            errors_.pop_front();
        }

        std::string json = "{\"scriptId\":\"" + EscapeJson(error.scriptId) +
                           "\",\"generation\":" + std::to_string(error.generation) +
                           ",\"workletId\":\"" + EscapeJson(error.workletId) +
                           "\",\"phase\":\"" + EscapeJson(error.phase) +
                           "\",\"message\":\"" + EscapeJson(error.message) +
                           "\",\"stack\":\"" + EscapeJson(error.stack) + "\"}";
        return CopyForAbi(json);
    }

    int64_t DoFrame(int64_t frameTimeNanos)
    {
        if (!ClaimOrCheckUiThread()) return -1;
        inFrame_ = true;
        currentFrameTimeNanos_ = frameTimeNanos;

        if (!EnsureIsolate())
        {
            inFrame_ = false;
            return -1;
        }

        {
            v8::Isolate::Scope isolateScope(isolate_);
            v8::HandleScope handleScope(isolate_);

            ProcessCommands();
            taskRunner_->DrainDueTasks();
            PublishFrameSource(frameTimeNanos);
            PublishPlaybackSource();
            StepAnimations(frameTimeNanos);
            DrainAnimationFrames(frameTimeNanos);
            ExecuteScheduled(frameTimeNanos);
            ExecuteMappers(frameTimeNanos);
            isolate_->PerformMicrotaskCheckpoint();
        }

        FlushUpdates(frameTimeNanos);
        previousFrameTimeNanos_ = frameTimeNanos;
        inFrame_ = false;

        if (HasQueuedCommands() || HasDirtyOrContinuousMappers() || HasContinuousSourceBindings() || hasPendingAnimationFrame_ || !animations_.empty()) return 0;
        return taskRunner_ ? taskRunner_->NextDelayMillis() : -1;
    }

    std::string ExecuteNow(
        const std::string& scriptId,
        uint64_t generation,
        const std::string& workletId,
        const std::string& argsJson,
        int64_t frameTimeNanos,
        bool* success)
    {
        if (success) *success = false;
        if (!ClaimOrCheckUiThread()) return "";

        inFrame_ = true;
        currentFrameTimeNanos_ = frameTimeNanos;
        if (!EnsureIsolate())
        {
            inFrame_ = false;
            return "";
        }

        std::string resultJson;

        {
            v8::Isolate::Scope isolateScope(isolate_);
            v8::HandleScope handleScope(isolate_);
            ProcessCommands();

            ContextState* contextState = FindContext(MakeContextKey(scriptId, generation));
            if (!contextState)
            {
                PushError({scriptId, generation, workletId, "executeNow", "Worklet context was not found", ""});
            }
            else
            {
                v8::Local<v8::Value> result;
                if (CallWorklet(*contextState, workletId, argsJson, "executeNow", &result))
                {
                    v8::Local<v8::Context> context = contextState->context.Get(isolate_);
                    v8::Context::Scope contextScope(context);
                    if (result->IsUndefined())
                    {
                        resultJson = "null";
                        if (success) *success = true;
                    }
                    else
                    {
                        if (SerializeShareableToJson(
                                *contextState,
                                result,
                                "executeNow:result",
                                &resultJson))
                        {
                            if (success) *success = true;
                        }
                    }
                }
            }

            isolate_->PerformMicrotaskCheckpoint();
        }

        FlushUpdates(frameTimeNanos);
        inFrame_ = false;

        if (HasDirtyOrContinuousMappers() || hasPendingAnimationFrame_) ScheduleUi();
        return resultJson;
    }

    void Shutdown()
    {
        if (!ClaimOrCheckUiThread()) return;

        DeactivateAllSources();
        std::lock_guard<std::mutex> platformLock(platformMutex_);
        if (!isolate_) return;

        {
            v8::Isolate::Scope isolateScope(isolate_);
            v8::HandleScope handleScope(isolate_);
            animations_.clear();
            contexts_.clear();
            pendingExecutions_.clear();
            pendingUpdates_.clear();
        }

        if (taskRunner_) taskRunner_->Deactivate();
        if (platform_) platform_->UnregisterIsolate(isolate_);
        isolate_->Dispose();

        isolate_ = nullptr;
        platformDelegate_.reset();
        taskRunner_.reset();
        allocator_.reset();
        previousFrameTimeNanos_ = 0;
        currentFrameTimeNanos_ = 0;
        lastPlaybackPositionMs_ = std::numeric_limits<double>::quiet_NaN();
        pendingBackwardPlaybackPositionMs_ = std::numeric_limits<double>::quiet_NaN();
        hasPendingAnimationFrame_ = false;
        uiThreadId_ = std::thread::id();

        {
            std::lock_guard<std::mutex> commandLock(commandMutex_);
            commands_.clear();
        }

        {
            std::lock_guard<std::mutex> sharedLock(sharedMutex_);
            sharedValues_.clear();
            cancelledAnimations_.clear();
        }

    }

   private:
    WorkletRuntime() = default;

    static void SharedGetCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host || info.Length() < 1) return;

        std::string sharedValueId = ToStdString(info.GetIsolate(), info[0]);
        host->runtime->ReadSharedValueOnUi(*host, sharedValueId, info);
    }

    static void SharedSetCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host || info.Length() < 2) return;

        std::string sharedValueId = ToStdString(info.GetIsolate(), info[0]);
        bool stored = host->runtime->WriteSharedValueOnUi(*host, sharedValueId, info[1]);
        info.GetReturnValue().Set(stored);
    }

    static void CancelSharedAnimationCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host || info.Length() < 1) return;

        const std::string sharedKey = MakeSharedKey(
            host->contextKey,
            ToStdString(info.GetIsolate(), info[0]));
        const bool existed = host->runtime->animations_.find(sharedKey) != host->runtime->animations_.end();
        host->runtime->CancelAnimationOnUi(sharedKey, true);
        info.GetReturnValue().Set(existed);
    }

    static void EmitUpdateCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host || info.Length() < 4) return;

        v8::Local<v8::Context> context = info.GetIsolate()->GetCurrentContext();
        int32_t nodeId = 0;
        if (!info[1]->Int32Value(context).To(&nodeId)) return;

        const std::string surfaceId = ToStdString(info.GetIsolate(), info[0]);
        const std::string property = ToStdString(info.GetIsolate(), info[2]);
        bool appended = host->runtime->AppendValueUpdates(
            host->runtime->CurrentContextState(),
            surfaceId,
            nodeId,
            property,
            info[3],
            "emitUpdate");
        info.GetReturnValue().Set(appended);
    }

    static void SetNativePropsCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host || info.Length() < 3 || !info[2]->IsObject()) return;

        v8::Local<v8::Context> context = info.GetIsolate()->GetCurrentContext();
        int32_t nodeId = 0;
        if (!info[1]->Int32Value(context).To(&nodeId)) return;

        ContextState* state = host->runtime->CurrentContextState();
        if (!state) state = host->runtime->FindContext(host->contextKey);
        if (!state) return;

        host->runtime->AppendProps(
            *state,
            ToStdString(info.GetIsolate(), info[0]),
            nodeId,
            info[2].As<v8::Object>(),
            "setNativeProps",
            nullptr);
        info.GetReturnValue().Set(true);
    }

    static void MeasureCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host || info.Length() < 2) return;

        v8::Local<v8::Context> context = info.GetIsolate()->GetCurrentContext();
        int32_t nodeId = 0;
        if (!info[1]->Int32Value(context).To(&nodeId)) return;

        std::string json = SpotifyPlusEngine::Get().MeasureWorkletView(
            ToStdString(info.GetIsolate(), info[0]),
            nodeId);
        host->runtime->ReturnParsedHostJson(info, json, "measure");
    }

    static void RelativeCoordsCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host || info.Length() < 4) return;

        v8::Local<v8::Context> context = info.GetIsolate()->GetCurrentContext();
        int32_t nodeId = 0;
        double absoluteX = 0;
        double absoluteY = 0;
        if (!info[1]->Int32Value(context).To(&nodeId) ||
            !info[2]->NumberValue(context).To(&absoluteX) ||
            !info[3]->NumberValue(context).To(&absoluteY))
        {
            return;
        }

        std::string json = SpotifyPlusEngine::Get().GetWorkletRelativeCoords(
            ToStdString(info.GetIsolate(), info[0]),
            nodeId,
            absoluteX,
            absoluteY);
        host->runtime->ReturnParsedHostJson(info, json, "getRelativeCoords");
    }

    static void ScrollToCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host || info.Length() < 5) return;

        v8::Local<v8::Context> context = info.GetIsolate()->GetCurrentContext();
        int32_t nodeId = 0;
        double x = 0;
        double y = 0;
        if (!info[1]->Int32Value(context).To(&nodeId) ||
            !info[2]->NumberValue(context).To(&x) ||
            !info[3]->NumberValue(context).To(&y))
        {
            return;
        }

        bool result = SpotifyPlusEngine::Get().ScrollWorkletView(
            ToStdString(info.GetIsolate(), info[0]),
            nodeId,
            x,
            y,
            info[4]->BooleanValue(info.GetIsolate()));
        info.GetReturnValue().Set(result);
    }

    static void DispatchCommandCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host || info.Length() < 4) return;

        v8::Local<v8::Context> context = info.GetIsolate()->GetCurrentContext();
        int32_t nodeId = 0;
        if (!info[1]->Int32Value(context).To(&nodeId)) return;

        v8::Local<v8::String> argsJson;
        if (!v8::JSON::Stringify(context, info[3]).ToLocal(&argsJson)) return;

        bool result = SpotifyPlusEngine::Get().DispatchWorkletCommand(
            ToStdString(info.GetIsolate(), info[0]),
            nodeId,
            ToStdString(info.GetIsolate(), info[2]),
            ToStdString(info.GetIsolate(), argsJson));
        info.GetReturnValue().Set(result);
    }

    static void CallWorkletCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host || info.Length() < 2 || !info[1]->IsArray()) return;

        ContextState* state = host->runtime->FindContext(host->contextKey);
        if (!state) return;
        host->runtime->CallWorkletFromUi(
            *state,
            ToStdString(info.GetIsolate(), info[0]),
            info[1].As<v8::Array>(),
            info);
    }

    static void ScheduleOnRnCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host || info.Length() < 2 || !info[1]->IsArray()) return;

        ContextState* state = host->runtime->FindContext(host->contextKey);
        if (!state) return;

        std::string argsJson;
        if (!host->runtime->SerializeShareableToJson(
                *state,
                info[1],
                "runOnRN:args",
                &argsJson))
        {
            return;
        }

        const std::string payload =
            "{\"scriptId\":\"" + EscapeJson(state->scriptId) +
            "\",\"generation\":" + std::to_string(state->generation) +
            ",\"functionId\":\"" + EscapeJson(ToStdString(info.GetIsolate(), info[0])) +
            "\",\"args\":" + argsJson + "}";
        SpotifyPlusEngine::Get().EmitEvent("worklet:runOnRN", payload);
        info.GetReturnValue().Set(true);
    }

    static void RequestFrameCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host) return;
        host->runtime->hasPendingAnimationFrame_ = true;
        host->runtime->ScheduleUi();
    }

    static void NowCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host) return;

        double now = host->runtime->currentFrameTimeNanos_ > 0
                         ? static_cast<double>(host->runtime->currentFrameTimeNanos_) / 1000000.0
                         : std::chrono::duration<double, std::milli>(Clock::now().time_since_epoch()).count();
        info.GetReturnValue().Set(now);
    }

    static void LogCallback(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        ContextHostData* host = GetHostData(info);
        if (!host || info.Length() < 2) return;

        std::string message = "[worklet:" + ToStdString(info.GetIsolate(), info[0]) + "]";
        if (info[1]->IsArray())
        {
            v8::Local<v8::Context> context = info.GetIsolate()->GetCurrentContext();
            v8::Local<v8::Array> args = info[1].As<v8::Array>();
            for (uint32_t i = 0; i < args->Length(); i++)
            {
                v8::Local<v8::Value> value;
                if (!args->Get(context, i).ToLocal(&value)) continue;
                message += " " + ToStdString(info.GetIsolate(), value);
            }
        }

        SpotifyPlusEngine::Get().Log(message.c_str());
    }

    static ContextHostData* GetHostData(const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        if (info.Data().IsEmpty() || !info.Data()->IsExternal()) return nullptr;
        return static_cast<ContextHostData*>(info.Data().As<v8::External>()->Value());
    }

    void ReturnParsedHostJson(
        const v8::FunctionCallbackInfo<v8::Value>& info,
        const std::string& json,
        const std::string& phase)
    {
        if (json.empty())
        {
            info.GetReturnValue().Set(v8::Null(info.GetIsolate()));
            return;
        }

        v8::Local<v8::Value> value;
        if (!v8::JSON::Parse(info.GetIsolate()->GetCurrentContext(), ToV8String(info.GetIsolate(), json)).ToLocal(&value))
        {
            if (activeContext_)
            {
                PushError({
                    activeContext_->scriptId,
                    activeContext_->generation,
                    activeMapper_ ? activeMapper_->workletId : "",
                    phase,
                    "Native host returned invalid JSON",
                    ""});
            }
            return;
        }

        info.GetReturnValue().Set(value);
    }

    void CallWorkletFromUi(
        ContextState& state,
        const std::string& workletId,
        v8::Local<v8::Array> args,
        const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        auto iterator = state.worklets.find(workletId);
        if (iterator == state.worklets.end())
        {
            PushError({state.scriptId, state.generation, workletId, "capturedWorklet", "Captured worklet was not registered", ""});
            return;
        }

        v8::Local<v8::Context> context = state.context.Get(isolate_);
        std::vector<v8::Local<v8::Value>> argv;
        argv.reserve(args->Length());
        for (uint32_t i = 0; i < args->Length(); i++)
        {
            v8::Local<v8::Value> value;
            if (!args->Get(context, i).ToLocal(&value)) return;
            argv.push_back(value);
        }

        v8::TryCatch tryCatch(isolate_);
        v8::Local<v8::Function> function = iterator->second.function.Get(isolate_);
        ContextState* previousContext = activeContext_;
        activeContext_ = &state;

        v8::Local<v8::Value> result;
        bool called = function
                          ->Call(
                              context,
                              function,
                              static_cast<int>(argv.size()),
                              argv.empty() ? nullptr : argv.data())
                          .ToLocal(&result);
        activeContext_ = previousContext;

        if (!called)
        {
            PushV8Error(state, workletId, "capturedWorklet", tryCatch);
            return;
        }

        info.GetReturnValue().Set(result);
    }

    bool Queue(Command command)
    {
        if (!IsAvailable()) return false;

        {
            std::lock_guard<std::mutex> lock(commandMutex_);
            commands_.push_back(std::move(command));
        }

        ScheduleUi();
        return true;
    }

    void QueueSharedValueChanged(const std::string& contextKey, const std::string& sharedKey)
    {
        Command command{CommandType::SharedValueChanged};
        command.id = contextKey;
        command.secondaryId = sharedKey;

        {
            std::lock_guard<std::mutex> lock(commandMutex_);
            commands_.push_back(std::move(command));
        }

        ScheduleUi();
    }

    void ScheduleUi()
    {
        if (inFrame_ && std::this_thread::get_id() == uiThreadId_) return;
        SpotifyPlusEngine::Get().RequestWorkletFrame();
    }

    bool ClaimOrCheckUiThread()
    {
        const std::thread::id current = std::this_thread::get_id();
        if (uiThreadId_ == std::thread::id())
        {
            uiThreadId_ = current;
            return true;
        }

        if (uiThreadId_ == current) return true;

        PushError({"", 0, "", "thread", "UI V8 isolate was entered from a non-owner thread", ""});
        __android_log_write(ANDROID_LOG_ERROR, TAG, "Rejected worklet runtime entry from a non-owner thread");
        return false;
    }

    bool EnsureIsolate()
    {
        if (isolate_) return true;

        node::MultiIsolatePlatform* platform = nullptr;
        {
            std::lock_guard<std::mutex> lock(platformMutex_);
            platform = platform_;
        }

        if (!platform)
        {
            PushError({"", 0, "", "initialize", "Node MultiIsolatePlatform is not configured", ""});
            return false;
        }

        allocator_ = node::ArrayBufferAllocator::Create();
        if (!allocator_)
        {
            PushError({"", 0, "", "initialize", "Unable to create the V8 ArrayBuffer allocator", ""});
            return false;
        }

        taskRunner_ = std::make_shared<MainThreadTaskRunner>([this]() { ScheduleUi(); });
        platformDelegate_ = std::make_unique<MainThreadPlatformDelegate>(taskRunner_);

        v8::Isolate::CreateParams params;
        params.array_buffer_allocator = allocator_.get();
        isolate_ = v8::Isolate::Allocate();
        if (!isolate_)
        {
            PushError({"", 0, "", "initialize", "Unable to allocate the UI V8 isolate", ""});
            platformDelegate_.reset();
            taskRunner_.reset();
            allocator_.reset();
            return false;
        }

        platform->RegisterIsolate(isolate_, platformDelegate_.get());
        v8::Isolate::Initialize(isolate_, params);
        isolate_->SetMicrotasksPolicy(v8::MicrotasksPolicy::kExplicit);

        __android_log_write(ANDROID_LOG_INFO, TAG, "Initialized UI-thread V8 isolate");
        return true;
    }

    bool HasQueuedCommands() const
    {
        std::lock_guard<std::mutex> lock(commandMutex_);
        return !commands_.empty();
    }

    void ProcessCommands()
    {
        std::deque<Command> commands;
        {
            std::lock_guard<std::mutex> lock(commandMutex_);
            commands.swap(commands_);
        }

        for (Command& command : commands)
        {
            switch (command.type)
            {
                case CommandType::CreateContext:
                    EnsureContext(command.scriptId, command.generation);
                    break;
                case CommandType::DisposeContext:
                    DisposeContext(command.scriptId, command.generation);
                    break;
                case CommandType::DisposeScript:
                    DisposeScript(command.scriptId);
                    break;
                case CommandType::RegisterWorklet:
                    RegisterWorklet(command);
                    break;
                case CommandType::UnregisterWorklet:
                {
                    ContextState* state = FindContext(MakeContextKey(command.scriptId, command.generation));
                    if (state) state->worklets.erase(command.id);
                    break;
                }
                case CommandType::InstallGlobals:
                    InstallGlobals(command);
                    break;
                case CommandType::ScheduleWorklet:
                    pendingExecutions_.push_back(
                        {MakeContextKey(command.scriptId, command.generation), command.id, command.payload});
                    break;
                case CommandType::RegisterMapper:
                    RegisterMapper(command);
                    break;
                case CommandType::UnregisterMapper:
                    UnregisterMapper(command);
                    break;
                case CommandType::DeleteSharedValue:
                    DeleteSharedValueOnUi(
                        MakeContextKey(command.scriptId, command.generation),
                        MakeSharedKey(MakeContextKey(command.scriptId, command.generation), command.id));
                    break;
                case CommandType::SharedValueChanged:
                    CancelAnimationOnUi(command.secondaryId, true);
                    MarkMappersDirty(command.id, command.secondaryId);
                    break;
                case CommandType::StartAnimation:
                    StartAnimationOnUi(
                        MakeContextKey(command.scriptId, command.generation),
                        MakeSharedKey(MakeContextKey(command.scriptId, command.generation), command.id),
                        command.payload);
                    break;
                case CommandType::CancelAnimation:
                    CancelAnimationOnUi(
                        MakeSharedKey(MakeContextKey(command.scriptId, command.generation), command.id),
                        true);
                    break;
                case CommandType::SetReducedMotionOverride:
                {
                    ContextState* state = EnsureContext(command.scriptId, command.generation);
                    if (state) state->reducedMotionOverride = command.priority;
                    break;
                }
            }
        }
    }

    bool EffectiveReducedMotion(const ContextState& state) const
    {
        if (state.reducedMotionOverride == 1) return true;
        if (state.reducedMotionOverride == 0) return false;
        return systemReducedMotion_.load();
    }

    ContextState* EnsureContext(const std::string& scriptId, uint64_t generation)
    {
        const std::string key = MakeContextKey(scriptId, generation);
        auto existing = contexts_.find(key);
        if (existing != contexts_.end()) return existing->second.get();

        auto state = std::make_unique<ContextState>();
        state->scriptId = scriptId;
        state->generation = generation;
        state->key = key;
        state->hostData = std::make_unique<ContextHostData>(ContextHostData{this, key});

        v8::Local<v8::External> hostData = v8::External::New(isolate_, state->hostData.get());
        v8::Local<v8::ObjectTemplate> global = v8::ObjectTemplate::New(isolate_);
        global->Set(
            ToV8String(isolate_, "__spotifyplusGetSharedValue"),
            v8::FunctionTemplate::New(isolate_, SharedGetCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusSetSharedValue"),
            v8::FunctionTemplate::New(isolate_, SharedSetCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusCancelAnimation"),
            v8::FunctionTemplate::New(isolate_, CancelSharedAnimationCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusEmitUpdate"),
            v8::FunctionTemplate::New(isolate_, EmitUpdateCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusSetNativeProps"),
            v8::FunctionTemplate::New(isolate_, SetNativePropsCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusMeasure"),
            v8::FunctionTemplate::New(isolate_, MeasureCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusGetRelativeCoords"),
            v8::FunctionTemplate::New(isolate_, RelativeCoordsCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusScrollTo"),
            v8::FunctionTemplate::New(isolate_, ScrollToCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusDispatchCommand"),
            v8::FunctionTemplate::New(isolate_, DispatchCommandCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusCallWorklet"),
            v8::FunctionTemplate::New(isolate_, CallWorkletCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusScheduleOnRN"),
            v8::FunctionTemplate::New(isolate_, ScheduleOnRnCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusRequestFrame"),
            v8::FunctionTemplate::New(isolate_, RequestFrameCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusNow"),
            v8::FunctionTemplate::New(isolate_, NowCallback, hostData));
        global->Set(
            ToV8String(isolate_, "__spotifyplusLog"),
            v8::FunctionTemplate::New(isolate_, LogCallback, hostData));

        v8::Local<v8::Context> context = v8::Context::New(isolate_, nullptr, global);
        if (context.IsEmpty())
        {
            PushError({scriptId, generation, "", "createContext", "V8 failed to create a context", ""});
            return nullptr;
        }

        state->context.Reset(isolate_, context);

        {
            v8::Context::Scope contextScope(context);
            v8::TryCatch tryCatch(isolate_);
            v8::Local<v8::Script> bootstrap;
            if (!v8::Script::Compile(context, ToV8String(isolate_, CONTEXT_BOOTSTRAP)).ToLocal(&bootstrap) ||
                bootstrap->Run(context).IsEmpty())
            {
                PushV8Error(*state, "", "bootstrap", tryCatch);
                return nullptr;
            }
        }

        ContextState* result = state.get();
        contexts_[key] = std::move(state);
        return result;
    }

    ContextState* FindContext(const std::string& key)
    {
        auto iterator = contexts_.find(key);
        return iterator == contexts_.end() ? nullptr : iterator->second.get();
    }

    ContextState* CurrentContextState()
    {
        return activeContext_;
    }

    void DisposeContext(const std::string& scriptId, uint64_t generation)
    {
        const std::string key = MakeContextKey(scriptId, generation);
        CancelAnimationsForContext(key);
        contexts_.erase(key);
        EraseSharedValuesForContext(key);
        EraseSourceBindingsForContext(key);

        pendingExecutions_.erase(
            std::remove_if(
                pendingExecutions_.begin(),
                pendingExecutions_.end(),
                [&](const ScheduledExecution& execution) { return execution.contextKey == key; }),
            pendingExecutions_.end());
    }

    void DisposeScript(const std::string& scriptId)
    {
        std::vector<std::string> keys;
        for (const auto& entry : contexts_)
        {
            if (entry.second->scriptId == scriptId) keys.push_back(entry.first);
        }

        for (const std::string& key : keys)
        {
            CancelAnimationsForContext(key);
            contexts_.erase(key);
            EraseSharedValuesForContext(key);
            EraseSourceBindingsForContext(key);
        }

        pendingExecutions_.erase(
            std::remove_if(
                pendingExecutions_.begin(),
                pendingExecutions_.end(),
                [&](const ScheduledExecution& execution)
                {
                    return std::find(keys.begin(), keys.end(), execution.contextKey) != keys.end();
                }),
            pendingExecutions_.end());
    }

    void EraseSharedValuesForContext(const std::string& contextKey)
    {
        const std::string prefix = contextKey + KEY_SEPARATOR;
        std::lock_guard<std::mutex> lock(sharedMutex_);

        auto iterator = sharedValues_.begin();
        while (iterator != sharedValues_.end())
        {
            if (iterator->first.rfind(prefix, 0) == 0)
            {
                iterator = sharedValues_.erase(iterator);
            }
            else
            {
                ++iterator;
            }
        }
    }

    void DeleteSharedValueOnUi(
        const std::string& contextKey,
        const std::string& sharedKey)
    {
        CancelAnimationOnUi(sharedKey, true);
        bool removed = false;
        {
            std::lock_guard<std::mutex> lock(sharedMutex_);
            removed = sharedValues_.erase(sharedKey) > 0;
            cancelledAnimations_.erase(sharedKey);
        }
        if (removed) MarkMappersDirty(contextKey, sharedKey);
    }

    void EraseSourceBindingsForContext(const std::string& contextKey)
    {
        struct Deactivation
        {
            std::string sourceId;
            std::string configJson;
        };
        std::vector<Deactivation> deactivations;

        {
            std::lock_guard<std::mutex> lock(sourceMutex_);
            auto source = sourceBindings_.begin();
            while (source != sourceBindings_.end())
            {
                std::vector<SourceBinding>& bindings = source->second;
                auto binding = bindings.begin();
                while (binding != bindings.end())
                {
                    if (binding->contextKey == contextKey)
                    {
                        deactivations.push_back({source->first, binding->configJson});
                        binding = bindings.erase(binding);
                    }
                    else
                    {
                        ++binding;
                    }
                }

                if (bindings.empty())
                {
                    source = sourceBindings_.erase(source);
                }
                else
                {
                    ++source;
                }
            }
        }

        for (const Deactivation& deactivation : deactivations)
        {
            SpotifyPlusEngine::Get().SetWorkletSourceActive(
                deactivation.sourceId,
                deactivation.configJson,
                false);
        }
    }

    void DeactivateAllSources()
    {
        struct Deactivation
        {
            std::string sourceId;
            std::string configJson;
        };
        std::vector<Deactivation> deactivations;

        {
            std::lock_guard<std::mutex> lock(sourceMutex_);
            for (const auto& source : sourceBindings_)
            {
                for (const SourceBinding& binding : source.second)
                {
                    deactivations.push_back({source.first, binding.configJson});
                }
            }
            sourceBindings_.clear();
            sourceLatestValues_.clear();
        }

        for (const Deactivation& deactivation : deactivations)
        {
            SpotifyPlusEngine::Get().SetWorkletSourceActive(
                deactivation.sourceId,
                deactivation.configJson,
                false);
        }
    }

    void PublishFrameSource(int64_t frameTimeNanos)
    {
        std::vector<SourceBinding> bindings;
        {
            std::lock_guard<std::mutex> lock(sourceMutex_);
            for (const char* sourceId : {"frame", "frameTimestamp"})
            {
                auto source = sourceBindings_.find(sourceId);
                if (source != sourceBindings_.end())
                {
                    bindings.insert(bindings.end(), source->second.begin(), source->second.end());
                }
            }
            if (bindings.empty()) return;
        }

        const double timestamp = static_cast<double>(frameTimeNanos) / 1000000.0;
        const double delta = previousFrameTimeNanos_ > 0
                                 ? static_cast<double>(frameTimeNanos - previousFrameTimeNanos_) / 1000000.0
                                 : 0.0;
        auto frameValue = std::make_shared<NativeShareable>();
        frameValue->kind = NativeShareable::Kind::Object;
        for (const auto& property : std::vector<std::pair<std::string, double>>{
                 {"timestamp", timestamp},
                 {"timeSincePreviousFrame", delta},
                 {"frameTimeNanos", static_cast<double>(frameTimeNanos)},
             })
        {
            NativeShareable child;
            child.kind = NativeShareable::Kind::Number;
            child.numberValue = property.second;
            frameValue->objectValue.emplace_back(property.first, std::move(child));
        }

        for (const SourceBinding& binding : bindings)
        {
            const std::string sharedKey = MakeSharedKey(binding.contextKey, binding.sharedValueId);
            bool changed = false;

            {
                std::lock_guard<std::mutex> lock(sharedMutex_);
                SharedValue& value = sharedValues_[sharedKey];
                if (!value.nativeValue || !NativeShareablesEqual(*value.nativeValue, *frameValue))
                {
                    value.json.clear();
                    value.nativeValue = frameValue;
                    value.version++;
                    changed = true;
                }
            }

            if (changed) MarkMappersDirty(binding.contextKey, sharedKey);
        }
    }

    void PublishPlaybackSource()
    {
        std::vector<SourceBinding> bindings;
        {
            std::lock_guard<std::mutex> lock(sourceMutex_);
            auto source = sourceBindings_.find("playbackClock");
            if (source == sourceBindings_.end() || source->second.empty()) return;
            bindings = source->second;
        }

        const double sampledPositionMs = SpotifyPlusEngine::Get().GetPlaybackPosition();
        if (!std::isfinite(sampledPositionMs) || sampledPositionMs < 0.0) return;

        constexpr double BACKWARD_SAMPLE_TOLERANCE_MS = 250.0;
        constexpr double BACKWARD_CONFIRMATION_TOLERANCE_MS = 2000.0;
        double positionMs = sampledPositionMs;
        if (
            std::isfinite(lastPlaybackPositionMs_) &&
            positionMs + BACKWARD_SAMPLE_TOLERANCE_MS < lastPlaybackPositionMs_)
        {
            const bool confirmsBackwardPosition =
                std::isfinite(pendingBackwardPlaybackPositionMs_) &&
                std::abs(positionMs - pendingBackwardPlaybackPositionMs_) <= BACKWARD_CONFIRMATION_TOLERANCE_MS;
            if (!confirmsBackwardPosition)
            {
                pendingBackwardPlaybackPositionMs_ = positionMs;
                return;
            }
        }

        lastPlaybackPositionMs_ = positionMs;
        pendingBackwardPlaybackPositionMs_ = std::numeric_limits<double>::quiet_NaN();
        for (const SourceBinding& binding : bindings)
        {
            double transformedPosition = positionMs + JsonNumber(binding.configJson, "offset", 0.0);
            if (JsonStringEquals(binding.configJson, "unit", "seconds")) transformedPosition /= 1000.0;
            auto nativeValue = std::make_shared<NativeShareable>();
            nativeValue->kind = NativeShareable::Kind::Number;
            nativeValue->numberValue = transformedPosition;
            const std::string sharedKey = MakeSharedKey(binding.contextKey, binding.sharedValueId);
            bool changed = false;

            {
                std::lock_guard<std::mutex> lock(sharedMutex_);
                SharedValue& value = sharedValues_[sharedKey];
                if (!value.nativeValue || !NativeShareablesEqual(*value.nativeValue, *nativeValue))
                {
                    value.json.clear();
                    value.nativeValue = std::move(nativeValue);
                    value.version++;
                    changed = true;
                }
            }

            if (changed) MarkMappersDirty(binding.contextKey, sharedKey);
        }
    }

    void RegisterWorklet(const Command& command)
    {
        ContextState* state = EnsureContext(command.scriptId, command.generation);
        if (!state) return;

        v8::Local<v8::Context> context = state->context.Get(isolate_);
        v8::Context::Scope contextScope(context);
        v8::TryCatch tryCatch(isolate_);

        const std::string wrapper =
            "(function(__rawClosure){const __closure=__spotifyplusReviveShareable(__rawClosure);"
            "with(__closure){const __fn=(" + command.source + "\n);"
            "Object.defineProperty(__fn,'__closure',{value:__closure,enumerable:false});return __fn;}})(" +
            command.payload + ")";
        v8::Local<v8::String> source = ToV8String(isolate_, wrapper);
        v8::Local<v8::Script> script;

        if (!v8::Script::Compile(context, source).ToLocal(&script))
        {
            PushV8Error(*state, command.id, "compile", tryCatch);
            return;
        }

        v8::Local<v8::Value> result;
        if (!script->Run(context).ToLocal(&result) || !result->IsFunction())
        {
            if (tryCatch.HasCaught())
            {
                PushV8Error(*state, command.id, "register", tryCatch);
            }
            else
            {
                PushError({state->scriptId, state->generation, command.id, "register", "Serialized source did not evaluate to a function", ""});
            }
            return;
        }

        RegisteredWorklet worklet;
        worklet.id = command.id;
        worklet.function.Reset(isolate_, result.As<v8::Function>());
        state->worklets[command.id] = std::move(worklet);
    }

    void InstallGlobals(const Command& command)
    {
        ContextState* state = EnsureContext(command.scriptId, command.generation);
        if (!state) return;

        v8::Local<v8::Context> context = state->context.Get(isolate_);
        v8::Context::Scope contextScope(context);
        v8::TryCatch tryCatch(isolate_);

        const std::string wrapper =
            "__spotifyplusInstallWorkletGlobals(\"" + EscapeJson(command.id) + "\",(" +
            command.source + "\n))";
        v8::Local<v8::Script> script;
        if (!v8::Script::Compile(context, ToV8String(isolate_, wrapper)).ToLocal(&script) ||
            script->Run(context).IsEmpty())
        {
            PushV8Error(*state, "", "installGlobals", tryCatch);
        }
    }

    void RegisterMapper(const Command& command)
    {
        ContextState* state = EnsureContext(command.scriptId, command.generation);
        if (!state) return;

        RegisteredMapper mapper;
        mapper.id = command.id;
        mapper.workletId = command.secondaryId;
        mapper.surfaceId = command.surfaceId;
        mapper.nodeId = command.nodeId;
        mapper.priority = command.priority;
        mapper.runEveryFrame = command.flag;
        mapper.dirty = true;
        state->mappers[command.id] = std::move(mapper);
    }

    void UnregisterMapper(const Command& command)
    {
        ContextState* state = FindContext(MakeContextKey(command.scriptId, command.generation));
        if (!state) return;
        auto mapper = state->mappers.find(command.id);
        if (mapper == state->mappers.end()) return;
        for (const auto& targetEntry : mapper->second.lastOutputs)
        {
            const MapperOutputSnapshot& target = targetEntry.second;
            for (const auto& propertyEntry : target.properties)
            {
                WorkletViewUpdate removal;
                removal.surfaceId = target.surfaceId;
                removal.nodeId = target.nodeId;
                removal.property = propertyEntry.first;
                removal.type = WorkletUpdateType::Null;
                pendingUpdates_.push_back(std::move(removal));
                MarkOverlappingMappersDirty(*state, &mapper->second, target, propertyEntry.first);
            }
        }
        state->mappers.erase(mapper);
    }

    void StartAnimationOnUi(
        const std::string& contextKey,
        const std::string& sharedKey,
        const std::string& descriptorJson)
    {
        ContextState* state = FindContext(contextKey);
        if (!state)
        {
            PushError({"", 0, "", "animation", "Animation context was not found", ""});
            return;
        }

        std::string currentJson = "0";
        std::shared_ptr<const NativeShareable> currentNativeValue;
        {
            std::lock_guard<std::mutex> lock(sharedMutex_);
            auto shared = sharedValues_.find(sharedKey);
            if (shared != sharedValues_.end())
            {
                currentJson = shared->second.json.empty() ? "0" : shared->second.json;
                currentNativeValue = shared->second.nativeValue;
            }
            cancelledAnimations_.erase(sharedKey);
        }

        CancelAnimationOnUi(sharedKey, true);

        v8::Local<v8::Context> context = state->context.Get(isolate_);
        v8::Context::Scope contextScope(context);
        v8::TryCatch tryCatch(isolate_);

        v8::Local<v8::Value> serializedDescriptor;
        v8::Local<v8::Value> serializedCurrent;
        if (!v8::JSON::Parse(context, ToV8String(isolate_, descriptorJson)).ToLocal(&serializedDescriptor) ||
            (currentNativeValue
                 ? !NativeShareableToV8Value(context, *currentNativeValue, &serializedCurrent)
                 : !v8::JSON::Parse(context, ToV8String(isolate_, currentJson)).ToLocal(&serializedCurrent)))
        {
            PushV8Error(*state, "", "animation:parse", tryCatch);
            return;
        }

        v8::Local<v8::Value> descriptor;
        v8::Local<v8::Value> current;
        if (!TransformShareable(
                *state,
                "__spotifyplusReviveShareable",
                "animation:descriptor",
                serializedDescriptor,
                &descriptor) ||
            !TransformShareable(
                *state,
                "__spotifyplusReviveShareable",
                "animation:current",
                serializedCurrent,
                &current))
        {
            return;
        }

        v8::Local<v8::Value> factoryValue;
        if (!context->Global()
                 ->Get(context, ToV8String(isolate_, "__spotifyplusCreateAnimationState"))
                 .ToLocal(&factoryValue) ||
            !factoryValue->IsFunction())
        {
            PushError({state->scriptId, state->generation, "", "animation", "Animation bootstrap factory was not found", ""});
            return;
        }

        v8::Local<v8::Value> args[] = {
            descriptor,
            current,
            v8::Number::New(isolate_, static_cast<double>(currentFrameTimeNanos_) / 1000000.0),
            v8::Boolean::New(isolate_, EffectiveReducedMotion(*state)),
        };
        v8::Local<v8::Value> result;
        ContextState* previousContext = activeContext_;
        activeContext_ = state;
        bool called = factoryValue.As<v8::Function>()
                          ->Call(context, context->Global(), 4, args)
                          .ToLocal(&result);
        activeContext_ = previousContext;

        if (!called || !result->IsObject())
        {
            if (tryCatch.HasCaught())
            {
                PushV8Error(*state, "", "animation:create", tryCatch);
            }
            else
            {
                PushError({state->scriptId, state->generation, "", "animation:create", "Animation factory returned invalid state", ""});
            }
            return;
        }

        AnimationInstance instance;
        instance.contextKey = contextKey;
        instance.sharedKey = sharedKey;
        instance.state.Reset(isolate_, result.As<v8::Object>());
        animations_[sharedKey] = std::move(instance);
    }

    void CancelAnimationOnUi(const std::string& sharedKey, bool invokeCallback)
    {
        auto animation = animations_.find(sharedKey);
        if (animation == animations_.end()) return;

        ContextState* state = FindContext(animation->second.contextKey);
        if (invokeCallback && state)
        {
            v8::Local<v8::Context> context = state->context.Get(isolate_);
            v8::Context::Scope contextScope(context);
            v8::TryCatch tryCatch(isolate_);

            v8::Local<v8::Value> cancelValue;
            if (context->Global()
                    ->Get(context, ToV8String(isolate_, "__spotifyplusCancelAnimationState"))
                    .ToLocal(&cancelValue) &&
                cancelValue->IsFunction())
            {
                v8::Local<v8::Value> argument = animation->second.state.Get(isolate_);
                ContextState* previousContext = activeContext_;
                activeContext_ = state;
                bool called = cancelValue.As<v8::Function>()
                                  ->Call(context, context->Global(), 1, &argument)
                                  .IsEmpty() == false;
                activeContext_ = previousContext;
                if (!called && tryCatch.HasCaught()) PushV8Error(*state, "", "animation:cancel", tryCatch);
            }
        }

        animations_.erase(animation);
    }

    void CancelAnimationsForContext(const std::string& contextKey)
    {
        std::vector<std::string> sharedKeys;
        for (const auto& animation : animations_)
        {
            if (animation.second.contextKey == contextKey) sharedKeys.push_back(animation.first);
        }

        for (const std::string& sharedKey : sharedKeys) CancelAnimationOnUi(sharedKey, true);
    }

    void StepAnimations(int64_t frameTimeNanos)
    {
        if (animations_.empty()) return;

        std::vector<std::string> finished;
        for (auto& animationEntry : animations_)
        {
            AnimationInstance& animation = animationEntry.second;
            ContextState* state = FindContext(animation.contextKey);
            if (!state)
            {
                finished.push_back(animation.sharedKey);
                continue;
            }

            v8::Local<v8::Context> context = state->context.Get(isolate_);
            v8::Context::Scope contextScope(context);
            v8::TryCatch tryCatch(isolate_);
            v8::Local<v8::Value> stepValue;
            if (!context->Global()
                     ->Get(context, ToV8String(isolate_, "__spotifyplusStepAnimationState"))
                     .ToLocal(&stepValue) ||
                !stepValue->IsFunction())
            {
                finished.push_back(animation.sharedKey);
                continue;
            }

            v8::Local<v8::Object> animationState = animation.state.Get(isolate_);
            v8::Local<v8::Value> args[] = {
                animationState,
                v8::Number::New(isolate_, static_cast<double>(frameTimeNanos) / 1000000.0),
                v8::Boolean::New(isolate_, EffectiveReducedMotion(*state)),
            };
            v8::Local<v8::Value> result;
            ContextState* previousContext = activeContext_;
            activeContext_ = state;
            bool called = stepValue.As<v8::Function>()
                              ->Call(context, context->Global(), 3, args)
                              .ToLocal(&result);
            activeContext_ = previousContext;

            if (!called || !result->IsObject())
            {
                if (tryCatch.HasCaught()) PushV8Error(*state, "", "animation:frame", tryCatch);
                finished.push_back(animation.sharedKey);
                continue;
            }

            v8::Local<v8::Object> resultState = result.As<v8::Object>();
            v8::Local<v8::Value> current;
            v8::Local<v8::Value> isFinished;
            if (!resultState->Get(context, ToV8String(isolate_, "current")).ToLocal(&current) ||
                !resultState->Get(context, ToV8String(isolate_, "finished")).ToLocal(&isFinished))
            {
                finished.push_back(animation.sharedKey);
                continue;
            }

            std::shared_ptr<const NativeShareable> nativeValue;
            if (!SerializeShareableToNative(
                    *state,
                    current,
                    "animation:frame",
                    &nativeValue))
            {
                finished.push_back(animation.sharedKey);
                continue;
            }

            bool changed = false;
            {
                std::lock_guard<std::mutex> lock(sharedMutex_);
                SharedValue& shared = sharedValues_[animation.sharedKey];
                if (!shared.nativeValue || !NativeShareablesEqual(*shared.nativeValue, *nativeValue))
                {
                    shared.nativeValue = std::move(nativeValue);
                    shared.json.clear();
                    shared.version++;
                    changed = true;
                }
            }

            if (changed) MarkMappersDirty(animation.contextKey, animation.sharedKey);
            if (isFinished->BooleanValue(isolate_)) finished.push_back(animation.sharedKey);
        }

        for (const std::string& sharedKey : finished) CancelAnimationOnUi(sharedKey, true);
    }

    void MarkMappersDirty(const std::string& contextKey, const std::string& sharedKey)
    {
        ContextState* state = FindContext(contextKey);
        if (!state) return;

        for (auto& entry : state->mappers)
        {
            RegisteredMapper& mapper = entry.second;
            if (mapper.dependencies.find(sharedKey) != mapper.dependencies.end()) mapper.dirty = true;
        }
    }

    bool TransformShareable(
        ContextState& state,
        const char* functionName,
        const std::string& phase,
        v8::Local<v8::Value> input,
        v8::Local<v8::Value>* output)
    {
        v8::Local<v8::Context> context = state.context.Get(isolate_);
        v8::Context::Scope contextScope(context);
        v8::Local<v8::Value> transformValue;
        if (!context->Global()
                 ->Get(context, ToV8String(isolate_, functionName))
                 .ToLocal(&transformValue) ||
            !transformValue->IsFunction())
        {
            PushError({state.scriptId, state.generation, "", phase, "Shareable transformer was not installed", ""});
            return false;
        }

        v8::TryCatch tryCatch(isolate_);
        ContextState* previousContext = activeContext_;
        activeContext_ = &state;
        bool called = transformValue.As<v8::Function>()
                          ->Call(context, context->Global(), 1, &input)
                          .ToLocal(output);
        activeContext_ = previousContext;
        if (!called)
        {
            PushV8Error(state, "", phase, tryCatch);
            return false;
        }
        return true;
    }

    bool SerializeShareableToJson(
        ContextState& state,
        v8::Local<v8::Value> value,
        const std::string& phase,
        std::string* json)
    {
        v8::Local<v8::Context> context = state.context.Get(isolate_);
        v8::Local<v8::Value> serializedValue;
        if (!TransformShareable(
                state,
                "__spotifyplusSerializeShareable",
                phase,
                value,
                &serializedValue))
        {
            return false;
        }

        v8::Local<v8::String> serializedJson;
        if (!v8::JSON::Stringify(context, serializedValue).ToLocal(&serializedJson))
        {
            PushError({state.scriptId, state.generation, "", phase, "Shareable value could not be encoded as JSON", ""});
            return false;
        }
        *json = ToStdString(isolate_, serializedJson);
        return true;
    }

    bool V8ValueToNativeShareable(
        v8::Local<v8::Context> context,
        v8::Local<v8::Value> value,
        NativeShareable* output,
        size_t depth = 0)
    {
        if (!output || depth > 128) return false;
        if (value->IsNull() || value->IsUndefined())
        {
            output->kind = NativeShareable::Kind::Null;
            return true;
        }
        if (value->IsBoolean())
        {
            output->kind = NativeShareable::Kind::Boolean;
            output->booleanValue = value->BooleanValue(isolate_);
            return true;
        }
        if (value->IsNumber())
        {
            const double number = value.As<v8::Number>()->Value();
            if (!std::isfinite(number))
            {
                output->kind = NativeShareable::Kind::Null;
            }
            else
            {
                output->kind = NativeShareable::Kind::Number;
                output->numberValue = number;
            }
            return true;
        }
        if (value->IsString())
        {
            output->kind = NativeShareable::Kind::String;
            output->stringValue = ToStdString(isolate_, value);
            return true;
        }
        if (value->IsArray())
        {
            output->kind = NativeShareable::Kind::Array;
            v8::Local<v8::Array> array = value.As<v8::Array>();
            output->arrayValue.reserve(array->Length());
            for (uint32_t index = 0; index < array->Length(); index++)
            {
                v8::Local<v8::Value> child;
                if (!array->Get(context, index).ToLocal(&child)) return false;
                NativeShareable nativeChild;
                if (!V8ValueToNativeShareable(context, child, &nativeChild, depth + 1)) return false;
                output->arrayValue.push_back(std::move(nativeChild));
            }
            return true;
        }
        if (!value->IsObject()) return false;

        output->kind = NativeShareable::Kind::Object;
        v8::Local<v8::Object> object = value.As<v8::Object>();
        v8::Local<v8::Array> names;
        if (!object->GetOwnPropertyNames(context).ToLocal(&names)) return false;
        output->objectValue.reserve(names->Length());
        for (uint32_t index = 0; index < names->Length(); index++)
        {
            v8::Local<v8::Value> name;
            v8::Local<v8::Value> child;
            if (!names->Get(context, index).ToLocal(&name) ||
                !object->Get(context, name).ToLocal(&child))
            {
                return false;
            }
            NativeShareable nativeChild;
            if (!V8ValueToNativeShareable(context, child, &nativeChild, depth + 1)) return false;
            output->objectValue.emplace_back(ToStdString(isolate_, name), std::move(nativeChild));
        }
        return true;
    }

    bool NativeShareableToV8Value(
        v8::Local<v8::Context> context,
        const NativeShareable& value,
        v8::Local<v8::Value>* output,
        size_t depth = 0)
    {
        if (!output || depth > 128) return false;
        switch (value.kind)
        {
            case NativeShareable::Kind::Null:
                *output = v8::Null(isolate_);
                return true;
            case NativeShareable::Kind::Boolean:
                *output = v8::Boolean::New(isolate_, value.booleanValue);
                return true;
            case NativeShareable::Kind::Number:
                *output = v8::Number::New(isolate_, value.numberValue);
                return true;
            case NativeShareable::Kind::String:
                *output = ToV8String(isolate_, value.stringValue);
                return true;
            case NativeShareable::Kind::Array:
            {
                v8::Local<v8::Array> array = v8::Array::New(
                    isolate_,
                    static_cast<int>(value.arrayValue.size()));
                for (size_t index = 0; index < value.arrayValue.size(); index++)
                {
                    v8::Local<v8::Value> child;
                    if (!NativeShareableToV8Value(context, value.arrayValue[index], &child, depth + 1) ||
                        !array->Set(context, static_cast<uint32_t>(index), child).FromMaybe(false))
                    {
                        return false;
                    }
                }
                *output = array;
                return true;
            }
            case NativeShareable::Kind::Object:
            {
                v8::Local<v8::Object> object = v8::Object::New(isolate_);
                for (const auto& property : value.objectValue)
                {
                    v8::Local<v8::Value> child;
                    if (!NativeShareableToV8Value(context, property.second, &child, depth + 1) ||
                        !object->Set(context, ToV8String(isolate_, property.first), child).FromMaybe(false))
                    {
                        return false;
                    }
                }
                *output = object;
                return true;
            }
        }
        return false;
    }

    bool SerializeShareableToNative(
        ContextState& state,
        v8::Local<v8::Value> value,
        const std::string& phase,
        std::shared_ptr<const NativeShareable>* nativeValue)
    {
        v8::Local<v8::Value> serializedValue;
        if (!TransformShareable(
                state,
                "__spotifyplusSerializeShareable",
                phase,
                value,
                &serializedValue))
        {
            return false;
        }
        auto converted = std::make_shared<NativeShareable>();
        if (!V8ValueToNativeShareable(state.context.Get(isolate_), serializedValue, converted.get()))
        {
            PushError({state.scriptId, state.generation, "", phase, "Shareable value could not be converted to the native registry", ""});
            return false;
        }
        *nativeValue = std::move(converted);
        return true;
    }

    void ReadSharedValueOnUi(
        const ContextHostData& host,
        const std::string& sharedValueId,
        const v8::FunctionCallbackInfo<v8::Value>& info)
    {
        const std::string sharedKey = MakeSharedKey(host.contextKey, sharedValueId);
        if (activeMapper_) activeMapper_->dependencies.insert(sharedKey);

        std::string json;
        std::shared_ptr<const NativeShareable> nativeValue;
        uint64_t version = 0;
        {
            std::lock_guard<std::mutex> lock(sharedMutex_);
            auto iterator = sharedValues_.find(sharedKey);
            if (iterator == sharedValues_.end())
            {
                info.GetReturnValue().Set(v8::Undefined(info.GetIsolate()));
                return;
            }
            json = iterator->second.json;
            nativeValue = iterator->second.nativeValue;
            version = iterator->second.version;
        }

        v8::Local<v8::Context> context = info.GetIsolate()->GetCurrentContext();
        v8::Local<v8::Value> value;
        if (nativeValue)
        {
            if (!NativeShareableToV8Value(context, *nativeValue, &value)) return;
        }
        else if (!v8::JSON::Parse(context, ToV8String(info.GetIsolate(), json)).ToLocal(&value))
        {
            if (activeContext_)
            {
                PushError({
                    activeContext_->scriptId,
                    activeContext_->generation,
                    activeMapper_ ? activeMapper_->workletId : "",
                    "sharedValueRead",
                    "Shared value contains invalid JSON",
                    ""});
            }
            return;
        }
        else
        {
            auto converted = std::make_shared<NativeShareable>();
            if (V8ValueToNativeShareable(context, value, converted.get()))
            {
                std::lock_guard<std::mutex> lock(sharedMutex_);
                auto current = sharedValues_.find(sharedKey);
                if (current != sharedValues_.end() && current->second.version == version)
                {
                    current->second.nativeValue = std::move(converted);
                }
            }
        }

        ContextState* state = FindContext(host.contextKey);
        if (!state) return;
        v8::Local<v8::Value> revived;
        if (TransformShareable(
                *state,
                "__spotifyplusReviveShareable",
                "sharedValueRead",
                value,
                &revived))
        {
            info.GetReturnValue().Set(revived);
        }
    }

    bool WriteSharedValueOnUi(
        const ContextHostData& host,
        const std::string& sharedValueId,
        v8::Local<v8::Value> value)
    {
        if (sharedValueId.empty() || !activeContext_) return false;

        const std::string sharedKey = MakeSharedKey(host.contextKey, sharedValueId);
        bool isAnimation = false;
        if (value->IsObject())
        {
            v8::Local<v8::Value> marker;
            if (value.As<v8::Object>()
                    ->Get(
                        isolate_->GetCurrentContext(),
                        ToV8String(isolate_, "__spotifyPlusAnimation"))
                    .ToLocal(&marker))
            {
                isAnimation = marker->BooleanValue(isolate_);
            }
        }
        if (isAnimation)
        {
            std::string serialized;
            if (!SerializeShareableToJson(*activeContext_, value, "sharedValueWrite", &serialized)) return false;
            StartAnimationOnUi(host.contextKey, sharedKey, serialized);
            return true;
        }

        std::shared_ptr<const NativeShareable> nativeValue;
        if (!SerializeShareableToNative(*activeContext_, value, "sharedValueWrite", &nativeValue)) return false;

        CancelAnimationOnUi(sharedKey, true);
        bool changed = false;

        {
            std::lock_guard<std::mutex> lock(sharedMutex_);
            SharedValue& sharedValue = sharedValues_[sharedKey];
            if (!sharedValue.nativeValue || !NativeShareablesEqual(*sharedValue.nativeValue, *nativeValue))
            {
                sharedValue.nativeValue = std::move(nativeValue);
                sharedValue.json.clear();
                sharedValue.version++;
                cancelledAnimations_.erase(sharedKey);
                changed = true;
            }
        }

        if (changed) MarkMappersDirty(host.contextKey, sharedKey);
        return true;
    }

    bool CallWorklet(
        ContextState& state,
        const std::string& workletId,
        const std::string& argsJson,
        const std::string& phase,
        v8::Local<v8::Value>* result)
    {
        v8::Local<v8::Context> context = state.context.Get(isolate_);
        v8::Context::Scope contextScope(context);
        v8::TryCatch tryCatch(isolate_);

        v8::Local<v8::Value> parsedArgs;
        if (!v8::JSON::Parse(context, ToV8String(isolate_, argsJson.empty() ? "[]" : argsJson)).ToLocal(&parsedArgs) ||
            !parsedArgs->IsArray())
        {
            if (tryCatch.HasCaught())
            {
                PushV8Error(state, workletId, phase + ":args", tryCatch);
            }
            else
            {
                PushError({state.scriptId, state.generation, workletId, phase, "Worklet arguments must be a JSON array", ""});
            }
            return false;
        }

        v8::Local<v8::Value> revivedArgs;
        if (!TransformShareable(
                state,
                "__spotifyplusReviveShareable",
                phase + ":args",
                parsedArgs,
                &revivedArgs) ||
            !revivedArgs->IsArray())
        {
            return false;
        }

        v8::Local<v8::Array> args = revivedArgs.As<v8::Array>();
        std::vector<v8::Local<v8::Value>> argv;
        argv.reserve(args->Length());
        for (uint32_t i = 0; i < args->Length(); i++)
        {
            v8::Local<v8::Value> value;
            if (!args->Get(context, i).ToLocal(&value)) return false;
            argv.push_back(value);
        }

        return InvokeWorklet(state, workletId, argv, phase, result);
    }

    bool InvokeWorklet(
        ContextState& state,
        const std::string& workletId,
        std::vector<v8::Local<v8::Value>>& argv,
        const std::string& phase,
        v8::Local<v8::Value>* result)
    {
        auto iterator = state.worklets.find(workletId);
        if (iterator == state.worklets.end())
        {
            PushError({state.scriptId, state.generation, workletId, phase, "Worklet was not registered", ""});
            return false;
        }

        v8::Local<v8::Context> context = state.context.Get(isolate_);
        v8::Context::Scope contextScope(context);
        v8::TryCatch tryCatch(isolate_);
        v8::Local<v8::Function> function = iterator->second.function.Get(isolate_);
        ContextState* previousContext = activeContext_;
        activeContext_ = &state;
        bool called = function
                          ->Call(
                              context,
                              function,
                              static_cast<int>(argv.size()),
                              argv.empty() ? nullptr : argv.data())
                          .ToLocal(result);
        activeContext_ = previousContext;

        if (!called)
        {
            PushV8Error(state, workletId, phase, tryCatch);
            return false;
        }

        return true;
    }

    void DrainAnimationFrames(int64_t frameTimeNanos)
    {
        if (!hasPendingAnimationFrame_) return;
        hasPendingAnimationFrame_ = false;

        const double timestamp = static_cast<double>(frameTimeNanos) / 1000000.0;
        for (auto& entry : contexts_)
        {
            ContextState& state = *entry.second;
            v8::Local<v8::Context> context = state.context.Get(isolate_);
            v8::Context::Scope contextScope(context);
            v8::TryCatch tryCatch(isolate_);

            v8::Local<v8::Value> drainValue;
            if (!context->Global()
                     ->Get(context, ToV8String(isolate_, "__spotifyplusDrainAnimationFrames"))
                     .ToLocal(&drainValue) ||
                !drainValue->IsFunction())
            {
                continue;
            }

            v8::Local<v8::Function> drain = drainValue.As<v8::Function>();
            v8::Local<v8::Value> argument = v8::Number::New(isolate_, timestamp);
            v8::Local<v8::Value> result;
            ContextState* previousContext = activeContext_;
            activeContext_ = &state;
            bool called = drain->Call(context, context->Global(), 1, &argument).ToLocal(&result);
            activeContext_ = previousContext;

            if (!called)
            {
                PushV8Error(state, "", "requestAnimationFrame", tryCatch);
            }
            else if (result->BooleanValue(isolate_))
            {
                hasPendingAnimationFrame_ = true;
            }
        }
    }

    void ExecuteScheduled(int64_t)
    {
        std::vector<ScheduledExecution> executions;
        executions.swap(pendingExecutions_);

        for (const ScheduledExecution& execution : executions)
        {
            ContextState* state = FindContext(execution.contextKey);
            if (!state) continue;

            v8::Local<v8::Value> result;
            if (CallWorklet(*state, execution.workletId, execution.argsJson, "schedule", &result))
            {
                const size_t updateStart = pendingUpdates_.size();
                if (!AppendMapperResult(*state, nullptr, result, execution.workletId, nullptr))
                {
                    pendingUpdates_.resize(updateStart);
                }
            }

            // scheduleOnUI assigns this prefix to one-shot registrations. Drop
            // the V8 Global even when execution throws so repeated scheduling
            // cannot retain closure graphs for the lifetime of the context.
            if (execution.workletId.rfind("scheduled:", 0) == 0)
            {
                state->worklets.erase(execution.workletId);
            }
        }
    }

    void ExecuteMappers(int64_t frameTimeNanos)
    {
        struct MapperRef
        {
            ContextState* context;
            RegisteredMapper* mapper;
        };

        const double timestamp = static_cast<double>(frameTimeNanos) / 1000000.0;
        const double delta = previousFrameTimeNanos_ > 0
                                 ? static_cast<double>(frameTimeNanos - previousFrameTimeNanos_) / 1000000.0
                                 : 0.0;

        size_t mapperCount = 0;
        for (const auto& contextEntry : contexts_) mapperCount += contextEntry.second->mappers.size();
        if (mapperCount == 0) return;

        std::unordered_map<RegisteredMapper*, size_t> executionCounts;
        const size_t executionLimit = std::max<size_t>(64, mapperCount * 8);
        size_t totalExecutions = 0;

        while (totalExecutions < executionLimit)
        {
            MapperRef next{nullptr, nullptr};
            for (auto& contextEntry : contexts_)
            {
                ContextState* context = contextEntry.second.get();
                for (auto& mapperEntry : context->mappers)
                {
                    RegisteredMapper* candidate = &mapperEntry.second;
                    const size_t executions = executionCounts[candidate];
                    if (!candidate->dirty && !(candidate->runEveryFrame && executions == 0)) continue;

                    const size_t nextExecutions = next.mapper ? executionCounts[next.mapper] : 0;
                    if (!next.mapper ||
                        executions < nextExecutions ||
                        (executions == nextExecutions && candidate->priority < next.mapper->priority) ||
                        (executions == nextExecutions && candidate->priority == next.mapper->priority && context->key < next.context->key) ||
                        (executions == nextExecutions && candidate->priority == next.mapper->priority && context->key == next.context->key && candidate->id < next.mapper->id))
                    {
                        next = {context, candidate};
                    }
                }
            }

            if (!next.mapper) break;

            RegisteredMapper& mapper = *next.mapper;
            std::unordered_set<std::string> previousDependencies = mapper.dependencies;
            mapper.dirty = false;
            mapper.dependencies.clear();
            activeMapper_ = &mapper;

            v8::Local<v8::Context> mapperContext = next.context->context.Get(isolate_);
            v8::Context::Scope mapperContextScope(mapperContext);
            v8::Local<v8::Object> frameInfo = v8::Object::New(isolate_);
            frameInfo->Set(
                mapperContext,
                ToV8String(isolate_, "timestamp"),
                v8::Number::New(isolate_, timestamp)).Check();
            frameInfo->Set(
                mapperContext,
                ToV8String(isolate_, "timeSincePreviousFrame"),
                v8::Number::New(isolate_, delta)).Check();
            std::vector<v8::Local<v8::Value>> mapperArgs = {frameInfo};
            v8::Local<v8::Value> result;
            bool succeeded = InvokeWorklet(
                *next.context,
                mapper.workletId,
                mapperArgs,
                "mapper",
                &result);

            activeMapper_ = nullptr;
            const size_t updateStart = pendingUpdates_.size();
            MapperOutputCollection currentOutputs;
            if (succeeded)
            {
                succeeded = AppendMapperResult(
                    *next.context,
                    &mapper,
                    result,
                    mapper.workletId,
                    &currentOutputs);
            }

            if (succeeded)
            {
                ApplyMapperOutputDiff(*next.context, mapper, std::move(currentOutputs), updateStart);
            }
            else
            {
                pendingUpdates_.resize(updateStart);
                mapper.dependencies = std::move(previousDependencies);
            }

            executionCounts[&mapper]++;
            totalExecutions++;
        }

        bool cycleDetected = false;
        for (auto& contextEntry : contexts_)
        {
            for (auto& mapperEntry : contextEntry.second->mappers)
            {
                if (!mapperEntry.second.dirty) continue;
                mapperEntry.second.dirty = false;
                cycleDetected = true;
            }
        }
        if (cycleDetected)
        {
            PushError({
                "",
                0,
                "",
                "mapperCycle",
                "Mapper dependency graph did not stabilize within " + std::to_string(executionLimit) + " executions",
                ""});
        }
    }

    bool AppendPrimitiveUpdate(
        ContextState* state,
        const std::string& surfaceId,
        int32_t nodeId,
        const std::string& property,
        v8::Local<v8::Value> value,
        const std::string& phase)
    {
        if (!state || surfaceId.empty() || property.empty()) return false;

        WorkletViewUpdate update;
        update.surfaceId = surfaceId;
        update.nodeId = nodeId;
        update.property = property;
        update.numberValue = 0.0;

        if (value->IsNull() || value->IsUndefined())
        {
            update.type = WorkletUpdateType::Null;
        }
        else if (value->IsNumber())
        {
            update.type = WorkletUpdateType::Number;
            update.numberValue = value.As<v8::Number>()->Value();
        }
        else if (value->IsBoolean())
        {
            update.type = WorkletUpdateType::Boolean;
            update.numberValue = value->BooleanValue(isolate_) ? 1.0 : 0.0;
        }
        else if (value->IsString())
        {
            update.type = WorkletUpdateType::String;
            update.stringValue = ToStdString(isolate_, value);
        }
        else
        {
            PushError({
                state->scriptId,
                state->generation,
                activeMapper_ ? activeMapper_->workletId : "",
                phase,
                "Animated prop '" + property + "' must be null, a number, a boolean, or a string",
                ""});
            return false;
        }

        pendingUpdates_.push_back(std::move(update));
        return true;
    }

    bool AppendValueUpdates(
        ContextState* state,
        const std::string& surfaceId,
        int32_t nodeId,
        const std::string& propertyPath,
        v8::Local<v8::Value> value,
        const std::string& phase)
    {
        if (!state || propertyPath.empty()) return false;
        if (!value->IsObject())
        {
            return AppendPrimitiveUpdate(state, surfaceId, nodeId, propertyPath, value, phase);
        }

        v8::Local<v8::Context> context = state->context.Get(isolate_);
        WorkletViewUpdate container;
        container.surfaceId = surfaceId;
        container.nodeId = nodeId;
        container.property = propertyPath;
        container.type = value->IsArray() ? WorkletUpdateType::Array : WorkletUpdateType::Object;
        container.numberValue = 0.0;
        pendingUpdates_.push_back(std::move(container));

        if (value->IsArray())
        {
            v8::Local<v8::Array> array = value.As<v8::Array>();
            for (uint32_t i = 0; i < array->Length(); i++)
            {
                v8::Local<v8::Value> child;
                if (!array->Get(context, i).ToLocal(&child)) continue;
                AppendValueUpdates(
                    state,
                    surfaceId,
                    nodeId,
                    propertyPath + "/" + std::to_string(i),
                    child,
                    phase);
            }
            return true;
        }

        v8::Local<v8::Object> object = value.As<v8::Object>();
        v8::Local<v8::Array> names;
        if (!object->GetOwnPropertyNames(context).ToLocal(&names)) return false;

        for (uint32_t i = 0; i < names->Length(); i++)
        {
            v8::Local<v8::Value> name;
            v8::Local<v8::Value> child;
            if (!names->Get(context, i).ToLocal(&name) || !object->Get(context, name).ToLocal(&child)) continue;

            AppendValueUpdates(
                state,
                surfaceId,
                nodeId,
                propertyPath + "/" + EscapePointerSegment(ToStdString(isolate_, name)),
                child,
                phase);
        }
        return true;
    }

    bool AppendMapperResult(
        ContextState& state,
        RegisteredMapper* mapper,
        v8::Local<v8::Value> result,
        const std::string& workletId,
        MapperOutputCollection* outputs)
    {
        if (result.IsEmpty() || result->IsNull() || result->IsUndefined()) return true;

        v8::Local<v8::Context> context = state.context.Get(isolate_);
        v8::Context::Scope contextScope(context);

        if (result->IsArray())
        {
            v8::Local<v8::Array> array = result.As<v8::Array>();
            bool valid = true;
            for (uint32_t i = 0; i < array->Length(); i++)
            {
                v8::Local<v8::Value> entry;
                if (!array->Get(context, i).ToLocal(&entry))
                {
                    valid = false;
                    continue;
                }
                valid = AppendEnvelope(state, entry, workletId, outputs) && valid;
            }
            return valid;
        }

        if (!result->IsObject())
        {
            PushError({state.scriptId, state.generation, workletId, "mapperResult", "Mapper result must be a props object or update envelope", ""});
            return false;
        }

        v8::Local<v8::Object> object = result.As<v8::Object>();
        v8::Local<v8::Value> propsValue;
        if (object->Get(context, ToV8String(isolate_, "props")).ToLocal(&propsValue) && propsValue->IsObject())
        {
            return AppendEnvelope(state, result, workletId, outputs);
        }

        if (!mapper)
        {
            PushError({state.scriptId, state.generation, workletId, "mapperResult", "An unbound scheduled worklet must return an update envelope", ""});
            return false;
        }

        return AppendProps(state, mapper->surfaceId, mapper->nodeId, object, workletId, outputs);
    }

    bool AppendEnvelope(
        ContextState& state,
        v8::Local<v8::Value> value,
        const std::string& workletId,
        MapperOutputCollection* outputs)
    {
        if (!value->IsObject())
        {
            PushError({state.scriptId, state.generation, workletId, "mapperResult", "Update envelope must be an object", ""});
            return false;
        }

        v8::Local<v8::Context> context = state.context.Get(isolate_);
        v8::Local<v8::Object> envelope = value.As<v8::Object>();
        v8::Local<v8::Value> surfaceValue;
        v8::Local<v8::Value> nodeValue;
        v8::Local<v8::Value> propsValue;

        if (!envelope->Get(context, ToV8String(isolate_, "surfaceId")).ToLocal(&surfaceValue) ||
            !envelope->Get(context, ToV8String(isolate_, "nodeId")).ToLocal(&nodeValue) ||
            !envelope->Get(context, ToV8String(isolate_, "props")).ToLocal(&propsValue) ||
            !surfaceValue->IsString() || !nodeValue->IsNumber() || !propsValue->IsObject())
        {
            PushError({state.scriptId, state.generation, workletId, "mapperResult", "Update envelope requires surfaceId, nodeId, and props", ""});
            return false;
        }

        int32_t nodeId = 0;
        if (!nodeValue->Int32Value(context).To(&nodeId)) return false;
        return AppendProps(
            state,
            ToStdString(isolate_, surfaceValue),
            nodeId,
            propsValue.As<v8::Object>(),
            workletId,
            outputs);
    }

    bool AppendProps(
        ContextState& state,
        const std::string& surfaceId,
        int32_t nodeId,
        v8::Local<v8::Object> props,
        const std::string& workletId,
        MapperOutputCollection* outputs)
    {
        v8::Local<v8::Context> context = state.context.Get(isolate_);
        v8::Local<v8::Array> names;
        if (!props->GetOwnPropertyNames(context).ToLocal(&names)) return false;

        MapperOutputSnapshot* snapshot = nullptr;
        if (outputs)
        {
            MapperOutputSnapshot& target = (*outputs)[MakeMapperTargetKey(surfaceId, nodeId)];
            target.surfaceId = surfaceId;
            target.nodeId = nodeId;
            snapshot = &target;
        }

        bool valid = true;
        for (uint32_t i = 0; i < names->Length(); i++)
        {
            v8::Local<v8::Value> name;
            v8::Local<v8::Value> value;
            if (!names->Get(context, i).ToLocal(&name) || !props->Get(context, name).ToLocal(&value))
            {
                valid = false;
                continue;
            }

            const std::string property = ToStdString(isolate_, name);
            if (property.empty()) continue;
            const size_t propertyUpdateStart = pendingUpdates_.size();
            const bool appended = AppendValueUpdates(
                &state,
                surfaceId,
                nodeId,
                property,
                value,
                "mapperResult:" + workletId);
            valid = appended && valid;
            if (snapshot && appended)
            {
                snapshot->properties[property] = std::vector<WorkletViewUpdate>(
                    pendingUpdates_.begin() + static_cast<std::ptrdiff_t>(propertyUpdateStart),
                    pendingUpdates_.end());
            }
        }
        return valid;
    }

    void ApplyMapperOutputDiff(
        ContextState& state,
        RegisteredMapper& mapper,
        MapperOutputCollection currentOutputs,
        size_t updateStart)
    {
        pendingUpdates_.resize(updateStart);
        for (const auto& currentEntry : currentOutputs)
        {
            const MapperOutputSnapshot& current = currentEntry.second;
            auto previous = mapper.lastOutputs.find(currentEntry.first);
            for (const auto& propertyEntry : current.properties)
            {
                bool unchanged = false;
                if (previous != mapper.lastOutputs.end())
                {
                    auto oldProperty = previous->second.properties.find(propertyEntry.first);
                    unchanged = oldProperty != previous->second.properties.end() &&
                                WorkletUpdateSequencesEqual(oldProperty->second, propertyEntry.second);
                }
                if (!unchanged)
                {
                    pendingUpdates_.insert(
                        pendingUpdates_.end(),
                        propertyEntry.second.begin(),
                        propertyEntry.second.end());
                }
            }
        }

        for (const auto& previousEntry : mapper.lastOutputs)
        {
            const MapperOutputSnapshot& previous = previousEntry.second;
            auto current = currentOutputs.find(previousEntry.first);
            for (const auto& propertyEntry : previous.properties)
            {
                if (current != currentOutputs.end() &&
                    current->second.properties.find(propertyEntry.first) != current->second.properties.end())
                {
                    continue;
                }

                WorkletViewUpdate removal;
                removal.surfaceId = previous.surfaceId;
                removal.nodeId = previous.nodeId;
                removal.property = propertyEntry.first;
                removal.type = WorkletUpdateType::Null;
                removal.numberValue = 0.0;
                pendingUpdates_.push_back(std::move(removal));
                MarkOverlappingMappersDirty(state, &mapper, previous, propertyEntry.first);
            }
        }

        mapper.lastOutputs = std::move(currentOutputs);
    }

    static bool WorkletUpdateSequencesEqual(
        const std::vector<WorkletViewUpdate>& left,
        const std::vector<WorkletViewUpdate>& right)
    {
        if (left.size() != right.size()) return false;
        for (size_t index = 0; index < left.size(); index++)
        {
            const WorkletViewUpdate& a = left[index];
            const WorkletViewUpdate& b = right[index];
            const bool numbersEqual = a.numberValue == b.numberValue ||
                                      (std::isnan(a.numberValue) && std::isnan(b.numberValue));
            if (a.surfaceId != b.surfaceId || a.nodeId != b.nodeId ||
                a.property != b.property || a.type != b.type ||
                !numbersEqual || a.stringValue != b.stringValue)
            {
                return false;
            }
        }
        return true;
    }

    void MarkOverlappingMappersDirty(
        ContextState& state,
        const RegisteredMapper* removedMapper,
        const MapperOutputSnapshot& target,
        const std::string& property)
    {
        for (auto& mapperEntry : state.mappers)
        {
            RegisteredMapper& candidate = mapperEntry.second;
            if (&candidate == removedMapper) continue;
            auto candidateTarget = candidate.lastOutputs.find(
                MakeMapperTargetKey(target.surfaceId, target.nodeId));
            if (candidateTarget == candidate.lastOutputs.end()) continue;
            if (candidateTarget->second.properties.find(property) == candidateTarget->second.properties.end()) continue;
            candidate.dirty = true;
        }
    }

    bool HasDirtyOrContinuousMappers() const
    {
        for (const auto& contextEntry : contexts_)
        {
            for (const auto& mapperEntry : contextEntry.second->mappers)
            {
                const RegisteredMapper& mapper = mapperEntry.second;
                if (mapper.dirty || mapper.runEveryFrame) return true;
            }
        }
        return false;
    }

    bool HasContinuousSourceBindings() const
    {
        std::lock_guard<std::mutex> lock(sourceMutex_);
        for (const char* sourceId : {"frame", "frameTimestamp", "playbackClock"})
        {
            auto source = sourceBindings_.find(sourceId);
            if (source != sourceBindings_.end() && !source->second.empty()) return true;
        }
        return false;
    }

    void FlushUpdates(int64_t frameTimeNanos)
    {
        if (pendingUpdates_.empty()) return;
        std::vector<WorkletViewUpdate> updates;
        updates.swap(pendingUpdates_);
        SpotifyPlusEngine::Get().DispatchWorkletUpdates(frameTimeNanos, updates);
    }

    void PushV8Error(
        ContextState& state,
        const std::string& workletId,
        const std::string& phase,
        v8::TryCatch& tryCatch)
    {
        std::string message = tryCatch.HasCaught()
                                  ? ToStdString(isolate_, tryCatch.Exception())
                                  : "V8 operation failed";
        std::string stack;
        v8::Local<v8::Value> stackValue;
        v8::Local<v8::Context> context = state.context.Get(isolate_);
        if (tryCatch.StackTrace(context).ToLocal(&stackValue) && stackValue->IsString())
        {
            stack = ToStdString(isolate_, stackValue);
        }

        PushError({state.scriptId, state.generation, workletId, phase, message, stack});
    }

    void PushError(WorkletError error)
    {
        __android_log_print(
            ANDROID_LOG_ERROR,
            TAG,
            "%s/%llu/%s [%s]: %s",
            error.scriptId.c_str(),
            static_cast<unsigned long long>(error.generation),
            error.workletId.c_str(),
            error.phase.c_str(),
            error.message.c_str());

        std::lock_guard<std::mutex> lock(errorMutex_);
        constexpr size_t MAX_BUFFERED_ERRORS = 128;
        if (errors_.size() == MAX_BUFFERED_ERRORS) errors_.pop_front();
        errors_.push_back(std::move(error));
    }

    mutable std::mutex platformMutex_;
    node::MultiIsolatePlatform* platform_ = nullptr;

    mutable std::mutex commandMutex_;
    std::deque<Command> commands_;

    mutable std::mutex sharedMutex_;
    std::unordered_map<std::string, SharedValue> sharedValues_;
    std::unordered_set<std::string> cancelledAnimations_;

    mutable std::mutex sourceMutex_;
    std::unordered_map<std::string, std::vector<SourceBinding>> sourceBindings_;
    std::unordered_map<std::string, std::string> sourceLatestValues_;

    std::mutex errorMutex_;
    std::deque<WorkletError> errors_;

    std::thread::id uiThreadId_;
    bool inFrame_ = false;
    int64_t previousFrameTimeNanos_ = 0;
    int64_t currentFrameTimeNanos_ = 0;
    double lastPlaybackPositionMs_ = std::numeric_limits<double>::quiet_NaN();
    double pendingBackwardPlaybackPositionMs_ = std::numeric_limits<double>::quiet_NaN();
    bool hasPendingAnimationFrame_ = false;

    std::unique_ptr<node::ArrayBufferAllocator> allocator_;
    std::shared_ptr<MainThreadTaskRunner> taskRunner_;
    std::unique_ptr<MainThreadPlatformDelegate> platformDelegate_;
    v8::Isolate* isolate_ = nullptr;

    std::unordered_map<std::string, std::unique_ptr<ContextState>> contexts_;
    std::unordered_map<std::string, AnimationInstance> animations_;
    std::vector<ScheduledExecution> pendingExecutions_;
    std::vector<WorkletViewUpdate> pendingUpdates_;
    ContextState* activeContext_ = nullptr;
    RegisteredMapper* activeMapper_ = nullptr;
    std::atomic<bool> systemReducedMotion_{false};
};

std::string JStringToString(JNIEnv* env, jstring value)
{
    if (!env || !value) return "";
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return "";
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}
}  // namespace

extern "C" bool SpotifyPlus_WorkletConfigurePlatform(void* platform)
{
    return WorkletRuntime::Get().ConfigurePlatform(static_cast<node::MultiIsolatePlatform*>(platform));
}

extern "C" bool SpotifyPlus_WorkletIsAvailable()
{
    return WorkletRuntime::Get().IsAvailable();
}

extern "C" bool SpotifyPlus_WorkletCreateContext(const char* scriptId, uint64_t generation)
{
    return WorkletRuntime::Get().QueueCreateContext(scriptId ? scriptId : "", generation);
}

extern "C" bool SpotifyPlus_WorkletDisposeContext(const char* scriptId, uint64_t generation)
{
    return WorkletRuntime::Get().QueueDisposeContext(scriptId ? scriptId : "", generation);
}

extern "C" bool SpotifyPlus_WorkletDisposeScript(const char* scriptId)
{
    return WorkletRuntime::Get().QueueDisposeScript(scriptId ? scriptId : "");
}

extern "C" bool SpotifyPlus_WorkletRegister(
    const char* scriptId,
    uint64_t generation,
    const char* workletId,
    const char* source,
    const char* closureJson)
{
    return WorkletRuntime::Get().QueueRegisterWorklet(
        scriptId ? scriptId : "",
        generation,
        workletId ? workletId : "",
        source ? source : "",
        closureJson ? closureJson : "{}");
}

extern "C" bool SpotifyPlus_WorkletUnregister(
    const char* scriptId,
    uint64_t generation,
    const char* workletId)
{
    return WorkletRuntime::Get().QueueUnregisterWorklet(
        scriptId ? scriptId : "",
        generation,
        workletId ? workletId : "");
}

extern "C" bool SpotifyPlus_WorkletInstallGlobals(
    const char* scriptId,
    uint64_t generation,
    const char* moduleName,
    const char* source)
{
    return WorkletRuntime::Get().QueueInstallGlobals(
        scriptId ? scriptId : "",
        generation,
        moduleName ? moduleName : "",
        source ? source : "");
}

extern "C" bool SpotifyPlus_WorkletSchedule(
    const char* scriptId,
    uint64_t generation,
    const char* workletId,
    const char* argsJson)
{
    return WorkletRuntime::Get().QueueScheduleWorklet(
        scriptId ? scriptId : "",
        generation,
        workletId ? workletId : "",
        argsJson ? argsJson : "[]");
}

extern "C" bool SpotifyPlus_WorkletRegisterMapper(
    const char* scriptId,
    uint64_t generation,
    const char* mapperId,
    const char* workletId,
    const char* surfaceId,
    int32_t nodeId,
    int32_t priority,
    bool runEveryFrame)
{
    return WorkletRuntime::Get().QueueRegisterMapper(
        scriptId ? scriptId : "",
        generation,
        mapperId ? mapperId : "",
        workletId ? workletId : "",
        surfaceId ? surfaceId : "",
        nodeId,
        priority,
        runEveryFrame);
}

extern "C" bool SpotifyPlus_WorkletUnregisterMapper(
    const char* scriptId,
    uint64_t generation,
    const char* mapperId)
{
    return WorkletRuntime::Get().QueueUnregisterMapper(
        scriptId ? scriptId : "",
        generation,
        mapperId ? mapperId : "");
}

extern "C" bool SpotifyPlus_WorkletSetSharedValue(
    const char* scriptId,
    uint64_t generation,
    const char* sharedValueId,
    const char* valueJson)
{
    return WorkletRuntime::Get().SetSharedValue(
        scriptId ? scriptId : "",
        generation,
        sharedValueId ? sharedValueId : "",
        valueJson ? valueJson : "");
}

extern "C" char* SpotifyPlus_WorkletGetSharedValue(
    const char* scriptId,
    uint64_t generation,
    const char* sharedValueId)
{
    bool found = false;
    std::string value = WorkletRuntime::Get().GetSharedValue(
        scriptId ? scriptId : "",
        generation,
        sharedValueId ? sharedValueId : "",
        &found);
    return found ? CopyForAbi(value) : nullptr;
}

extern "C" bool SpotifyPlus_WorkletDeleteSharedValue(
    const char* scriptId,
    uint64_t generation,
    const char* sharedValueId)
{
    return WorkletRuntime::Get().DeleteSharedValue(
        scriptId ? scriptId : "",
        generation,
        sharedValueId ? sharedValueId : "");
}

extern "C" bool SpotifyPlus_WorkletCancelAnimation(
    const char* scriptId,
    uint64_t generation,
    const char* sharedValueId)
{
    return WorkletRuntime::Get().CancelAnimation(
        scriptId ? scriptId : "",
        generation,
        sharedValueId ? sharedValueId : "");
}

extern "C" bool SpotifyPlus_WorkletGetReducedMotion()
{
    return WorkletRuntime::Get().GetReducedMotion();
}

extern "C" bool SpotifyPlus_WorkletSetReducedMotionOverride(
    const char* scriptId,
    uint64_t generation,
    const char* modeJson)
{
    return WorkletRuntime::Get().SetReducedMotionOverride(
        scriptId ? scriptId : "",
        generation,
        modeJson ? modeJson : "null");
}

extern "C" bool SpotifyPlus_WorkletRegisterSource(
    const char* scriptId,
    uint64_t generation,
    const char* sourceId,
    const char* sharedValueId,
    const char* configJson)
{
    return WorkletRuntime::Get().RegisterSource(
        scriptId ? scriptId : "",
        generation,
        sourceId ? sourceId : "",
        sharedValueId ? sharedValueId : "",
        configJson ? configJson : "{}");
}

extern "C" bool SpotifyPlus_WorkletUnregisterSource(
    const char* scriptId,
    uint64_t generation,
    const char* sourceId,
    const char* sharedValueId)
{
    return WorkletRuntime::Get().UnregisterSource(
        scriptId ? scriptId : "",
        generation,
        sourceId ? sourceId : "",
        sharedValueId ? sharedValueId : "");
}

extern "C" bool SpotifyPlus_WorkletPublishSourceValue(const char* sourceId, const char* valueJson)
{
    return WorkletRuntime::Get().PublishSourceValue(
        sourceId ? sourceId : "",
        valueJson ? valueJson : "");
}

extern "C" char* SpotifyPlus_WorkletTakeErrorJson()
{
    return WorkletRuntime::Get().TakeErrorJson();
}

extern "C" void SpotifyPlus_WorkletFreeString(char* value)
{
    std::free(value);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_lenerd_spotifyplus_module_scripting_WorkletRuntimeManager_nativeDoFrame(
    JNIEnv*,
    jclass,
    jlong frameTimeNanos)
{
    return static_cast<jlong>(WorkletRuntime::Get().DoFrame(static_cast<int64_t>(frameTimeNanos)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lenerd_spotifyplus_module_scripting_WorkletRuntimeManager_nativeExecuteNow(
    JNIEnv* env,
    jclass,
    jstring scriptId,
    jlong generation,
    jstring workletId,
    jstring argsJson,
    jlong frameTimeNanos)
{
    bool success = false;
    std::string result = WorkletRuntime::Get().ExecuteNow(
        JStringToString(env, scriptId),
        static_cast<uint64_t>(generation),
        JStringToString(env, workletId),
        JStringToString(env, argsJson),
        static_cast<int64_t>(frameTimeNanos),
        &success);
    return success ? env->NewStringUTF(result.c_str()) : nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_lenerd_spotifyplus_module_scripting_WorkletRuntimeManager_nativeShutdown(JNIEnv*, jclass)
{
    WorkletRuntime::Get().Shutdown();
}

extern "C" JNIEXPORT void JNICALL
Java_com_lenerd_spotifyplus_module_scripting_WorkletRuntimeManager_nativePublishSourceValue(
    JNIEnv* env,
    jclass,
    jstring sourceId,
    jstring valueJson)
{
    WorkletRuntime::Get().PublishSourceValue(
        JStringToString(env, sourceId),
        JStringToString(env, valueJson));
}
