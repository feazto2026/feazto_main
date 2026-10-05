import React, { useEffect } from 'react';
import { Pressable, ScrollView, Switch, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { useKitchenStore } from '../../src/store/kitchenStore';

export default function VendorDashboard() {
  const router = useRouter();
  const { online, queue, isLoading, error, loadQueue, setOnline, accept, markReady } = useKitchenStore();

  useEffect(() => {
    void loadQueue();
  }, []);

  const active = queue.filter((o) => o.status !== 'COMPLETED' && o.status !== 'CANCELLED');

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <View style={{ backgroundColor: Colors.cream, borderRadius: 16, padding: 14, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <View>
          <Text style={{ fontWeight: '800', fontSize: 16 }}>Kitchen {online ? 'online' : 'offline'}</Text>
          <Text style={{ color: Colors.muted }}>{active.length} active orders</Text>
        </View>
        <Switch accessibilityLabel="Kitchen online" value={online} onValueChange={(v: boolean) => void setOnline(v)} />
      </View>
      {error ? <Text role="alert" style={{ color: Colors.error, marginTop: 8 }}>{error}</Text> : null}
      <Text style={{ fontWeight: '800', marginVertical: 12 }}>ACTIVE QUEUE {isLoading ? '(loading…)' : ''}</Text>
      {active.map((o) => (
        <View key={o.id} style={{ backgroundColor: 'white', borderRadius: 16, padding: 12, marginBottom: 8, borderWidth: 1, borderColor: Colors.border }}>
          <Text style={{ fontWeight: '800' }}>{o.id.slice(0, 8)}… {'\u2022'} {o.status}</Text>
          <Text>{'\u20B9'}{o.total}</Text>
          <View style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
            {o.status === 'PLACED' ? (
              <Pressable onPress={() => void accept(o.id)} style={{ backgroundColor: Colors.ink, borderRadius: 999, paddingHorizontal: 14, paddingVertical: 8 }}>
                <Text style={{ color: 'white', fontWeight: '700' }}>Accept</Text>
              </Pressable>
            ) : null}
            {o.status === 'PREPARING' ? (
              <Pressable onPress={() => void markReady(o.id)} style={{ backgroundColor: Colors.green, borderRadius: 999, paddingHorizontal: 14, paddingVertical: 8 }}>
                <Text style={{ color: 'white', fontWeight: '700' }}>Mark ready</Text>
              </Pressable>
            ) : null}
            <Pressable onPress={() => router.push(`/order/${o.id}`)} style={{ backgroundColor: Colors.cream, borderRadius: 999, paddingHorizontal: 14, paddingVertical: 8 }}>
              <Text style={{ fontWeight: '700' }}>Details</Text>
            </Pressable>
          </View>
        </View>
      ))}
      {active.length === 0 && !isLoading ? <Text style={{ color: Colors.muted }}>All caught up.</Text> : null}
    </ScrollView>
  );
}
