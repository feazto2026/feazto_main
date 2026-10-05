import React, { useEffect } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { useKitchenStore } from '../../src/store/kitchenStore';

export default function VendorOrders() {
  const router = useRouter();
  const { queue, isLoading, loadQueue, accept, markReady } = useKitchenStore();
  useEffect(() => {
    void loadQueue();
  }, []);
  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 20, fontWeight: '800' }}>Orders queue {isLoading ? '(loading…)' : ''}</Text>
      {queue.map((o) => (
        <View key={o.id} style={{ backgroundColor: 'white', borderRadius: 12, padding: 12, marginTop: 8 }}>
          <Text style={{ fontWeight: '700' }}>{o.id.slice(0, 8)}… {'\u2022'} {o.status}</Text>
          <View style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
            <Pressable onPress={() => void accept(o.id)} style={{ backgroundColor: Colors.ink, borderRadius: 999, paddingHorizontal: 12, paddingVertical: 8 }}>
              <Text style={{ color: 'white' }}>Accept</Text>
            </Pressable>
            <Pressable onPress={() => void markReady(o.id)} style={{ backgroundColor: Colors.green, borderRadius: 999, paddingHorizontal: 12, paddingVertical: 8 }}>
              <Text style={{ color: 'white' }}>Ready</Text>
            </Pressable>
            <Pressable onPress={() => router.push(`/order/${o.id}`)} style={{ paddingHorizontal: 12, paddingVertical: 8 }}>
              <Text>Details →</Text>
            </Pressable>
          </View>
        </View>
      ))}
    </ScrollView>
  );
}
