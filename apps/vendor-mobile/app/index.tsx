import React, { useEffect } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../src/constants/colors';
import { useVendorAuthStore } from '../src/store/authStore';

export default function Splash() {
  const router = useRouter();
  const isAuthenticated = useVendorAuthStore((s) => s.isAuthenticated);
  useEffect(() => {
    const t = setTimeout(() => {
      router.replace(isAuthenticated ? '/(tabs)' : '/(auth)/sign-in');
    }, 900);
    return () => clearTimeout(t);
  }, [isAuthenticated]);
  return (
    <View style={{ flex: 1, backgroundColor: Colors.ink, alignItems: 'center', justifyContent: 'center' }}>
      <Text style={{ fontSize: 36, fontWeight: '900', fontStyle: 'italic', color: 'white' }}>
        FEA<Text style={{ color: Colors.yellow }}>Z</Text>TO <Text style={{ fontSize: 14 }}>Kitchen</Text>
      </Text>
      <ActivityIndicator color={Colors.yellow} style={{ marginTop: 20 }} />
    </View>
  );
}
