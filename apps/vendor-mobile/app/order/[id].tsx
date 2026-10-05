import React, { useEffect, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { orders } from '../../api/orders';
import type { OrderDetail } from '../../../../packages/mobile-shared/src/index.js';

export default function VendorOrderDetail() {
  const { id } = useLocalSearchParams() as { id: string };
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    orders.get(String(id)).then(setOrder).catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed'));
  }, [id]);
  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 20, fontWeight: '800' }}>Order {String(id).slice(0, 8)}…</Text>
      {error ? <Text role="alert" style={{ color: Colors.error }}>{error}</Text> : null}
      {order ? (
        <View style={{ backgroundColor: 'white', borderRadius: 12, padding: 14, marginTop: 12 }}>
          <Text>{order.status} {'\u2022'} {'\u20B9'}{order.total}</Text>
          {(order.items ?? []).map((i, n) => (
            <Text key={n} style={{ marginTop: 6 }}>{i.qty} × {i.name}</Text>
          ))}
          <View style={{ flexDirection: 'row', gap: 8, marginTop: 12 }}>
            <Pressable onPress={() => { void orders.vendorAccept(order.id).then(() => setOrder({ ...order, status: 'PREPARING' })); }} style={{ backgroundColor: Colors.ink, borderRadius: 999, paddingHorizontal: 14, paddingVertical: 10 }}>
              <Text style={{ color: 'white', fontWeight: '700' }}>Accept</Text>
            </Pressable>
            <Pressable onPress={() => { void orders.vendorReady(order.id).then(() => setOrder({ ...order, status: 'READY_FOR_PICKUP' })); }} style={{ backgroundColor: Colors.green, borderRadius: 999, paddingHorizontal: 14, paddingVertical: 10 }}>
              <Text style={{ color: 'white', fontWeight: '700' }}>Mark ready</Text>
            </Pressable>
          </View>
        </View>
      ) : null}
    </ScrollView>
  );
}
