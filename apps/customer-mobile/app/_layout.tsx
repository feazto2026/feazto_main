import React from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { Colors } from '../src/constants/colors';

export default function RootLayout() {
  return (
    <>
      <StatusBar style="dark" />
      <Stack
        screenOptions={{
          headerShown: false,
          contentStyle: { backgroundColor: Colors.background },
          animation: 'fade',
        }}
      >
        <Stack.Screen name="index" />
        <Stack.Screen name="(auth)" />
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="food/[id]" />
        <Stack.Screen name="cook/[id]" />
        <Stack.Screen name="checkout" />
        <Stack.Screen name="tracking/[id]" />
        <Stack.Screen name="orders/[id]" />
        <Stack.Screen name="book-a-cook" />
      </Stack>
    </>
  );
}
