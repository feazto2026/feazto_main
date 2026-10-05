import React, { useEffect } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { ScreenState } from '../../src/components/ScreenState';
import { useOrdersStore } from '../../src/store/ordersStore';

export default function OrdersScreen() {
  const router = useRouter();
  const { list, isLoading, error, load } = useOrdersStore();

  useEffect(() => {
    void load();
  }, []);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 22, fontWeight: '800', marginBottom: 12 }}>Orders</Text>
      <ScreenState loading={isLoading} error={error} empty={list.length ? null : 'No orders yet — they appear here after checkout'}>
        {list.map((o) => (
          <Pressable
            key={o.id}
            accessibilityRole="button"
            onPress={() => router.push(`/orders/${o.id}`)}
            style={{ backgroundColor: Colors.surface, borderRadius: 12, padding: 12, marginBottom: 8, borderWidth: 1, borderColor: Colors.border }}
          >
            <Text style={{ fontWeight: '800' }}>{o.id.slice(0, 8)}…</Text>
            <Text style={{ color: Colors.textSecondary }}>{o.vendorName ?? ''} {'\u2022'} {'\u20B9'}{o.total}</Text>
            <Text style={{ fontSize: 12, fontWeight: '700', marginTop: 4 }}>{o.status}</Text>
          </Pressable>
        ))}
      </ScreenState>
      <View style={{ height: 12 }} />
    </ScrollView>
  );
}
