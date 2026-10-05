import React, { useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../../src/constants/theme';
import { useRiderAuthStore } from '../../src/store/riderStore';

export default function RiderSignIn() {
  const router = useRouter();
  const [phone, setPhone] = useState('');
  const sendOtp = useRiderAuthStore((s) => s.sendOtp);
  const isLoading = useRiderAuthStore((s) => s.isLoading);
  const error = useRiderAuthStore((s) => s.error);
  return (
    <View style={{ flex: 1, backgroundColor: Colors.background, padding: 24, justifyContent: 'center' }}>
      <Text style={{ fontSize: 24, fontWeight: '900' }}>Ride with FEAZTO</Text>
      <Text style={{ color: Colors.textMuted, marginTop: 6 }}>Delivery partner sign-in with OTP.</Text>
      {error ? <Text role="alert" style={{ color: Colors.error, marginTop: 12 }}>{error}</Text> : null}
      <TextInput accessibilityLabel="Mobile number" keyboardType="phone-pad" placeholder="10-digit mobile number" value={phone} onChangeText={setPhone} style={{ backgroundColor: 'white', borderWidth: 1, borderColor: '#EAE5D8', borderRadius: 12, padding: 14, marginTop: 20 }} />
      <Pressable accessibilityRole="button" disabled={isLoading} onPress={async () => { if (await sendOtp(phone)) router.push('/(auth)/verify-otp'); }} style={{ backgroundColor: Colors.darkPill, borderRadius: 999, padding: 16, marginTop: 16, alignItems: 'center' }}>
        <Text style={{ color: 'white', fontWeight: '800' }}>{isLoading ? 'Sending…' : 'Send OTP'}</Text>
      </Pressable>
    </View>
  );
}
