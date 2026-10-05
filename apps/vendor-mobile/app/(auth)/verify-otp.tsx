import React, { useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { useVendorAuthStore } from '../../src/store/authStore';

export default function VendorVerify() {
  const router = useRouter();
  const [otp, setOtp] = useState('');
  const verifyOtp = useVendorAuthStore((s) => s.verifyOtp);
  const isLoading = useVendorAuthStore((s) => s.isLoading);
  const error = useVendorAuthStore((s) => s.error);
  return (
    <View style={{ flex: 1, backgroundColor: Colors.background, padding: 24, justifyContent: 'center' }}>
      <Text style={{ fontSize: 22, fontWeight: '800' }}>Enter OTP</Text>
      {error ? <Text role="alert" style={{ color: Colors.error, marginTop: 12 }}>{error}</Text> : null}
      <TextInput accessibilityLabel="One-time passcode" keyboardType="number-pad" maxLength={6} value={otp} onChangeText={setOtp} placeholder="4–6 digit code" style={{ backgroundColor: 'white', borderWidth: 1, borderColor: Colors.border, borderRadius: 12, padding: 14, marginTop: 20, textAlign: 'center', fontSize: 20, letterSpacing: 4 }} />
      <Pressable accessibilityRole="button" disabled={isLoading} onPress={async () => { if (await verifyOtp(otp)) router.replace('/(tabs)'); }} style={{ backgroundColor: Colors.yellow, borderRadius: 999, padding: 16, marginTop: 16, alignItems: 'center' }}>
        <Text style={{ fontWeight: '800' }}>{isLoading ? 'Verifying…' : 'Verify & continue'}</Text>
      </Pressable>
    </View>
  );
}
