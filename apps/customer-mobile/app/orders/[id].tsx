import React, { useEffect } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { ScreenState } from '../../src/components/ScreenState';
import { useOrdersStore } from '../../src/store/ordersStore';

export default function OrderDetailScreen() {
  const { id } = useLocalSearchParams() as { id: string };
  const router = useRouter();
  const { active, isLoading, error, loadDetail, cancel } = useOrdersStore();

  useEffect(() => {
    void loadDetail(String(id));
  }, [id]);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 22, fontWeight: '800' }}>Order detail</Text>
      <ScreenState loading={isLoading} error={error} empty={active ? null : 'Order not found'}>
        {active ? (
          <View style={{ backgroundColor: Colors.surface, borderRadius: 16, padding: 16, marginTop: 12 }}>
            <Text style={{ fontWeight: '800' }}>{active.id}</Text>
            <Text style={{ marginTop: 4 }}>{active.status} {'\u2022'} {'\u20B9'}{active.total}</Text>
            {(active.items ?? []).map((i, n) => (
              <Text key={n} style={{ marginTop: 6 }}>{i.qty} × {i.name} — {'\u20B9'}{i.unitPrice * i.qty}</Text>
            ))}
            <View style={{ flexDirection: 'row', gap: 8, marginTop: 16 }}>
              <Pressable
                accessibilityRole="button"
                onPress={() => router.push(`/tracking/${active.id}`)}
                style={{ flex: 1, backgroundColor: Colors.darkPill, borderRadius: 999, padding: 12, alignItems: 'center' }}
              >
                <Text style={{ color: Colors.textWhite, fontWeight: '800' }}>Track</Text>
              </Pressable>
              <Pressable
                accessibilityRole="button"
                onPress={() => void cancel(active.id, 'Customer requested cancellation')}
                style={{ flex: 1, backgroundColor: Colors.surface, borderRadius: 999, padding: 12, alignItems: 'center', borderWidth: 1, borderColor: Colors.border }}
              >
                <Text style={{ fontWeight: '800' }}>Cancel</Text>
              </Pressable>
            </View>
          </View>
        ) : null}
      </ScreenState>
    </ScrollView>
  );
}
