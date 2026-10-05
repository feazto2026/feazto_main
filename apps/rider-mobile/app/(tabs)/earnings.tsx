import React from 'react';
import { ScrollView, Text, View } from 'react-native';
import { Colors } from '../../src/constants/theme';
import { useRiderWorkStore } from '../../src/store/riderStore';

export default function RiderEarnings() {
  const { mine } = useRiderWorkStore();
  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 20, fontWeight: '800' }}>Earnings</Text>
      <View style={{ backgroundColor: 'white', borderRadius: 16, padding: 16, marginTop: 12 }}>
        <Text style={{ fontSize: 11, color: Colors.textMuted }}>ACTIVE DELIVERIES</Text>
        <Text style={{ fontSize: 28, fontWeight: '800', marginTop: 8 }}>{mine.length}</Text>
        <Text style={{ color: Colors.textMuted, marginTop: 8, fontSize: 12 }}>Payouts settle server-side; this screen never fabricates amounts.</Text>
      </View>
    </ScrollView>
  );
}
