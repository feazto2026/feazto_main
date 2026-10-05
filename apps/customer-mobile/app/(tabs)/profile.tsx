import React from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { Colors } from '../../src/constants/colors';
import { useAuthStore } from '../../src/store/authStore';
import { useUiStore } from '../../src/store/uiStore';

export default function ProfileScreen() {
  const logout = useAuthStore((s) => s.logout);
  const phone = useAuthStore((s) => s.phone);
  const addressLabel = useUiStore((s) => s.addressLabel);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 22, fontWeight: '800' }}>Profile</Text>
      <View style={{ backgroundColor: Colors.surface, borderRadius: 12, padding: 14, marginTop: 12 }}>
        <Text style={{ fontWeight: '700' }}>{phone || 'Guest'}</Text>
        <Text style={{ color: Colors.textSecondary, marginTop: 4 }}>{addressLabel}</Text>
      </View>
      <Pressable
        accessibilityRole="button"
        onPress={() => void logout()}
        style={{ backgroundColor: Colors.surface, borderRadius: 999, padding: 14, marginTop: 16, alignItems: 'center', borderWidth: 1, borderColor: Colors.border }}
      >
        <Text style={{ fontWeight: '800' }}>Sign out</Text>
      </Pressable>
      <Text style={{ fontSize: 11, color: Colors.textMuted, marginTop: 16 }}>
        Session: JWT in memory, refresh token in SecureStore. Preferences stay on this device.
      </Text>
    </ScrollView>
  );
}
