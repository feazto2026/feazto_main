import React from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { Colors } from '../../src/constants/theme';
import { useRiderAuthStore } from '../../src/store/riderStore';

export default function RiderProfile() {
  const logout = useRiderAuthStore((s) => s.logout);
  const phone = useRiderAuthStore((s) => s.phone);
  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 20, fontWeight: '800' }}>Profile</Text>
      <View style={{ backgroundColor: 'white', borderRadius: 12, padding: 14, marginTop: 12 }}>
        <Text style={{ fontWeight: '700' }}>{phone || 'Delivery partner'}</Text>
      </View>
      <Pressable onPress={() => void logout()} style={{ backgroundColor: 'white', borderRadius: 999, padding: 14, marginTop: 16, alignItems: 'center', borderWidth: 1, borderColor: '#EAE5D8' }}>
        <Text style={{ fontWeight: '800' }}>Sign out</Text>
      </Pressable>
    </ScrollView>
  );
}
