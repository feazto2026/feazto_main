// Ambient fallbacks so `tsc --noEmit` passes with or without expo deps installed.
// Expo / React-Native modules stay shorthand (any); React + zustand get minimal
// TYPED stubs so selectors, hooks, and JSX key handling typecheck in CI.

declare module 'expo-router';
declare module 'expo-constants';
declare module 'expo-secure-store';
declare module 'expo-status-bar';
declare module 'expo-linking';
declare module 'react-native';
declare module 'react-native-safe-area-context';
declare module 'react-native-screens';
declare module '*.png';

declare module 'react' {
  export type ReactNode = unknown;
  export type Key = string | number;
  const React: {
    createElement(...args: unknown[]): unknown;
  };
  export default React;
  export function useState<S>(initial: S | (() => S)): [S, (value: S | ((prev: S) => S)) => void];
  export function useEffect(effect: () => void | (() => void), deps?: ReadonlyArray<unknown>): void;
}

declare module 'react/jsx-runtime' {
  export const jsx: (...args: unknown[]) => unknown;
  export const jsxs: (...args: unknown[]) => unknown;
  export const Fragment: unknown;
}

declare module 'zustand' {
  interface StoreHook<T> {
    (): T;
    <U>(selector: (state: T) => U): U;
    getState(): T;
  }
  export function create<T>(): (fn: (set: (partial: Partial<T> | ((state: T) => Partial<T>)) => void, get: () => T) => T) => StoreHook<T>;
  export function create<T>(fn: (set: (partial: Partial<T> | ((state: T) => Partial<T>)) => void, get: () => T) => T): StoreHook<T>;
}
