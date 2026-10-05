import React, { useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';
import { BrandMark } from '../../src/components/BrandMark';
import { Colors } from '../../src/constants/colors';
import { useAuthStore } from '../../src/store/authStore';

export default function SignInScreen() {
  const router = useRouter();
  const [phone, setPhone] = useState('');
  const sendOtp = useAuthStore((s) => s.sendOtp);
  const isLoading = useAuthStore((s) => s.isLoading);
  const error = useAuthStore((s) => s.error);

  async function next() {
    const ok = await sendOtp(phone);
    if (ok) router.push('/(auth)/verify-otp');
  }

  return (
    <View style={{ flex: 1, backgroundColor: Colors.background, padding: 24, justifyContent: 'center' }}>
      <BrandMark />
      <Text style={{ fontSize: 22, fontWeight: '800', marginTop: 24 }}>Welcome to FEAZTO</Text>
      <Text style={{ color: Colors.textSecondary, marginTop: 6 }}>Regional homemade food, delivered with love.</Text>
      {error ? (
        <Text role="alert" style={{ color: Colors.error, marginTop: 12 }}>{error}</Text>
      ) : null}
      <TextInput
        accessibilityLabel="Mobile number"
        keyboardType="phone-pad"
        placeholder="10-digit mobile number"
        value={phone}
        onChangeText={setPhone}
        style={{ backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 12, padding: 14, marginTop: 20 }}
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Send OTP"
        onPress={next}
        disabled={isLoading}
        style={{ backgroundColor: Colors.darkPill, borderRadius: 999, padding: 16, marginTop: 16, alignItems: 'center' }}
      >
        <Text style={{ color: Colors.textWhite, fontWeight: '800' }}>{isLoading ? 'Sending…' : 'Send OTP'}</Text>
      </Pressable>
      <Text style={{ fontSize: 11, color: Colors.textMuted, marginTop: 16 }}>
        OTP verification is server-issued. No demo bypass in production builds.
      </Text>
    </View>
  );
}
