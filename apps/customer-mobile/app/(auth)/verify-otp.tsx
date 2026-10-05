import React, { useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { useAuthStore } from '../../src/store/authStore';

export default function VerifyOtpScreen() {
  const router = useRouter();
  const [otp, setOtp] = useState('');
  const verifyOtp = useAuthStore((s) => s.verifyOtp);
  const isLoading = useAuthStore((s) => s.isLoading);
  const error = useAuthStore((s) => s.error);
  const phone = useAuthStore((s) => s.phone);

  async function next() {
    const ok = await verifyOtp(otp);
    if (ok) router.replace('/(tabs)');
  }

  return (
    <View style={{ flex: 1, backgroundColor: Colors.background, padding: 24, justifyContent: 'center' }}>
      <Text style={{ fontSize: 22, fontWeight: '800' }}>Enter OTP</Text>
      <Text style={{ color: Colors.textSecondary, marginTop: 6 }}>Sent to {phone || 'your number'}</Text>
      {error ? (
        <Text role="alert" style={{ color: Colors.error, marginTop: 12 }}>{error}</Text>
      ) : null}
      <TextInput
        accessibilityLabel="One-time passcode"
        keyboardType="number-pad"
        maxLength={6}
        placeholder="4–6 digit code"
        value={otp}
        onChangeText={setOtp}
        style={{ backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 12, padding: 14, marginTop: 20, letterSpacing: 4, fontSize: 20, textAlign: 'center' }}
      />
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Verify OTP"
        onPress={next}
        disabled={isLoading}
        style={{ backgroundColor: Colors.yellowPrimary, borderRadius: 999, padding: 16, marginTop: 16, alignItems: 'center' }}
      >
        <Text style={{ color: Colors.textPrimary, fontWeight: '800' }}>{isLoading ? 'Verifying…' : 'Verify & continue'}</Text>
      </Pressable>
    </View>
  );
}
