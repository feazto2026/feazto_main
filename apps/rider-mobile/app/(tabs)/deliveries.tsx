import React, { useEffect } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../../src/constants/theme';
import { useRiderWorkStore } from '../../src/store/riderStore';

export default function RiderDeliveries() {
  const router = useRouter();
  const { mine, loadMine, pickup } = useRiderWorkStore();
  useEffect(() => {
    void loadMine();
  }, []);
  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 20, fontWeight: '800' }}>My deliveries</Text>
      {mine.map((d) => (
        <View key={d.id} style={{ backgroundColor: 'white', borderRadius: 12, padding: 12, marginTop: 8 }}>
          <Text style={{ fontWeight: '700' }}>{d.orderId.slice(0, 8)}… {'\u2022'} {d.status}</Text>
          <View style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
            <Pressable onPress={() => void pickup(d.id)} style={{ backgroundColor: Colors.darkPill, borderRadius: 999, paddingHorizontal: 14, paddingVertical: 8 }}>
              <Text style={{ color: 'white' }}>Confirm pickup</Text>
            </Pressable>
            <Pressable onPress={() => router.push('/delivery')} style={{ paddingHorizontal: 12, paddingVertical: 8 }}>
              <Text>Open →</Text>
            </Pressable>
          </View>
        </View>
      ))}
      {mine.length === 0 ? <Text style={{ color: Colors.textMuted, marginTop: 12 }}>No active deliveries.</Text> : null}
    </ScrollView>
  );
}
