import React, { useEffect } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { ScreenState } from '../../src/components/ScreenState';
import { useOrdersStore } from '../../src/store/ordersStore';

const STEPS = ['PLACED', 'PREPARING', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED'];

export default function TrackingScreen() {
  const { id } = useLocalSearchParams() as { id: string };
  const { active, isLoading, error, loadDetail } = useOrdersStore();

  useEffect(() => {
    void loadDetail(String(id));
  }, [id]);

  const idx = active ? STEPS.indexOf(active.status) : -1;

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 22, fontWeight: '800' }}>Tracking</Text>
      <ScreenState loading={isLoading} error={error} empty={active ? null : 'Order not found'}>
        {active ? (
          <View style={{ backgroundColor: Colors.surface, borderRadius: 16, padding: 16, marginTop: 12 }}>
            <Text style={{ fontWeight: '800' }}>Order {active.id.slice(0, 8)}…</Text>
            <Text style={{ color: Colors.textSecondary, marginTop: 4 }}>ETA: {active.eta ?? '—'}</Text>
            <View style={{ marginTop: 12 }}>
              {STEPS.map((s, i) => (
                <View key={s} style={{ flexDirection: 'row', gap: 8, paddingVertical: 6, alignItems: 'center' }}>
                  <Text>{i <= idx ? '●' : '○'}</Text>
                  <Text style={{ fontWeight: i <= idx ? '800' : '400' }}>{s.replace(/_/g, ' ')}</Text>
                </View>
              ))}
            </View>
            <Text style={{ fontSize: 11, color: Colors.textMuted, marginTop: 12 }}>
              Live status only — the backend state machine is authoritative.
            </Text>
          </View>
        ) : null}
      </ScreenState>
    </ScrollView>
  );
}
