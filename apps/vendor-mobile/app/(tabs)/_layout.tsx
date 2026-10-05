import React from 'react';
import { Tabs } from 'expo-router';
import { Colors } from '../../src/constants/colors';

export default function VendorTabs() {
  return (
    <Tabs screenOptions={{ headerShown: false, tabBarActiveTintColor: Colors.ink, tabBarStyle: { backgroundColor: 'white' } }}>
      <Tabs.Screen name="index" options={{ title: 'Dashboard' }} />
      <Tabs.Screen name="orders" options={{ title: 'Orders' }} />
      <Tabs.Screen name="menu" options={{ title: 'Menu' }} />
      <Tabs.Screen name="earnings" options={{ title: 'Earnings' }} />
      <Tabs.Screen name="profile" options={{ title: 'Profile' }} />
    </Tabs>
  );
}
