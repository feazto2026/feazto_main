import React from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { Colors } from '../../src/constants/colors';
import { useVendorAuthStore } from '../../src/store/authStore';
import { useKitchenStore } from '../../src/store/kitchenStore';

export default function VendorProfile() {
  const logout = useVendorAuthStore((s) => s.logout);
  const online = useKitchenStore((s) => s.online);
  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 20, fontWeight: '800' }}>Kitchen profile</Text>
      <View style={{ backgroundColor: 'white', borderRadius: 12, padding: 14, marginTop: 12 }}>
        <Text>Status: {online ? 'Online' : 'Offline'}</Text>
        <Text style={{ color: Colors.muted, marginTop: 4 }}>Profile edits save to /api/v1/vendor/profile.</Text>
      </View>
      <Pressable onPress={() => void logout()} style={{ backgroundColor: 'white', borderRadius: 999, padding: 14, marginTop: 16, alignItems: 'center', borderWidth: 1, borderColor: Colors.border }}>
        <Text style={{ fontWeight: '800' }}>Sign out</Text>
      </Pressable>
    </ScrollView>
  );
}
