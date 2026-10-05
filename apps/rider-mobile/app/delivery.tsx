import React, { useState } from 'react';
import { Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { Colors } from '../src/constants/theme';
import { useRiderWorkStore } from '../src/store/riderStore';

export default function DeliveryScreen() {
  const { mine, pickup, complete } = useRiderWorkStore();
  const [code, setCode] = useState('');
  const current = mine[0];
  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 20, fontWeight: '800' }}>Active delivery</Text>
      {!current ? <Text style={{ color: Colors.textMuted, marginTop: 12 }}>No active delivery. Accept one from Home.</Text> : (
        <View style={{ backgroundColor: 'white', borderRadius: 16, padding: 16, marginTop: 12 }}>
          <Text style={{ fontWeight: '800' }}>{current.orderId} {'\u2022'} {current.status}</Text>
          <Text style={{ color: Colors.textMuted, marginTop: 4 }}>{current.address ?? ''}</Text>
          <Pressable onPress={() => void pickup(current.id)} style={{ backgroundColor: Colors.darkPill, borderRadius: 999, padding: 12, marginTop: 12, alignItems: 'center' }}>
            <Text style={{ color: 'white', fontWeight: '800' }}>Confirm pickup</Text>
          </Pressable>
          <TextInput accessibilityLabel="Delivery OTP" keyboardType="number-pad" placeholder="Customer OTP" value={code} onChangeText={setCode} style={{ backgroundColor: Colors.background, borderRadius: 12, padding: 12, marginTop: 12, textAlign: 'center', letterSpacing: 4 }} />
          <Pressable onPress={() => void complete(current.id, code)} style={{ backgroundColor: Colors.success, borderRadius: 999, padding: 12, marginTop: 8, alignItems: 'center' }}>
            <Text style={{ color: 'white', fontWeight: '800' }}>Complete delivery</Text>
          </Pressable>
        </View>
      )}
    </ScrollView>
  );
}
