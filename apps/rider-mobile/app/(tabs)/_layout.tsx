import React from 'react';
import { Tabs } from 'expo-router';
import { Colors } from '../../src/constants/theme';

export default function RiderTabs() {
  return (
    <Tabs screenOptions={{ headerShown: false, tabBarActiveTintColor: Colors.textPrimary, tabBarStyle: { backgroundColor: 'white' } }}>
      <Tabs.Screen name="index" options={{ title: 'Home' }} />
      <Tabs.Screen name="deliveries" options={{ title: 'Deliveries' }} />
      <Tabs.Screen name="earnings" options={{ title: 'Earnings' }} />
      <Tabs.Screen name="profile" options={{ title: 'Profile' }} />
    </Tabs>
  );
}
