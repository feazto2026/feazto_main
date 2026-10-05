import React from 'react';
import { ScrollView, Text, View } from 'react-native';
import { Colors } from '../../src/constants/colors';
import { useKitchenStore } from '../../src/store/kitchenStore';

export default function VendorEarnings() {
  const { queue } = useKitchenStore();
  const done = queue.filter((o) => o.status === 'COMPLETED');
  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 20, fontWeight: '800' }}>Earnings & revenue</Text>
      <View style={{ backgroundColor: Colors.ink, borderRadius: 16, padding: 16, marginTop: 12 }}>
        <Text style={{ color: '#aaa', fontSize: 11 }}>SETTLED ORDERS (SERVER-SOURCED)</Text>
        <Text style={{ color: 'white', fontSize: 28, fontWeight: '800', marginTop: 8 }}>{done.length} orders</Text>
      </View>
      <Text style={{ color: Colors.muted, marginTop: 12, fontSize: 12 }}>Payout figures come from the finance backend — this screen never fabricates totals.</Text>
    </ScrollView>
  );
}
