import React, { useEffect } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../src/constants/theme';
import { useRiderAuthStore } from '../src/store/riderStore';

export default function Splash() {
  const router = useRouter();
  const isAuthenticated = useRiderAuthStore((s) => s.isAuthenticated);
  useEffect(() => {
    const t = setTimeout(() => {
      router.replace(isAuthenticated ? '/(tabs)' : '/(auth)/sign-in');
    }, 900);
    return () => clearTimeout(t);
  }, [isAuthenticated]);
  return (
    <View style={{ flex: 1, backgroundColor: Colors.background, alignItems: 'center', justifyContent: 'center' }}>
      <Text style={{ fontSize: 36, fontWeight: '900', fontStyle: 'italic' }}>
        FEA<Text style={{ color: Colors.yellowPrimary }}>Z</Text>TO <Text style={{ fontSize: 14 }}>Rider</Text>
      </Text>
      <ActivityIndicator color={Colors.yellowPrimary} style={{ marginTop: 20 }} />
    </View>
  );
}
