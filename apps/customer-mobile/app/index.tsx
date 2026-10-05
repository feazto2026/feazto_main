import React, { useEffect } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { useRouter } from 'expo-router';
import { BrandMark } from '../src/components/BrandMark';
import { Colors } from '../src/constants/colors';
import { useAuthStore } from '../src/store/authStore';

export default function SplashScreen() {
  const router = useRouter();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const restore = useAuthStore((s) => s.restore);

  useEffect(() => {
    let live = true;
    (async () => {
      const timer = setTimeout(() => {}, 0);
      clearTimeout(timer);
      await restore().catch(() => false);
      await new Promise((r) => setTimeout(r, 900));
      if (!live) return;
      router.replace(isAuthenticated || useAuthStore.getState().isAuthenticated ? '/(tabs)' : '/(auth)/sign-in');
    })();
    return () => {
      live = false;
    };
  }, []);

  return (
    <View style={{ flex: 1, backgroundColor: Colors.darkPill, alignItems: 'center', justifyContent: 'center' }}>
      <BrandMark size="lg" />
      <View style={{ position: 'absolute', bottom: 80 }}>
        <ActivityIndicator size="small" color={Colors.yellowPrimary} />
      </View>
    </View>
  );
}
