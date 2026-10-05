import React, { useEffect, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { ScreenState } from '../../src/components/ScreenState';
import { FoodCard } from '../../src/components/cards';
import { vendors } from '../../api/vendors';
import { useDiscoveryStore } from '../../src/store/discoveryStore';
import { useCartStore } from '../../src/store/cartStore';

export default function CookDetailScreen() {
  const { id } = useLocalSearchParams() as { id: string };
  const router = useRouter();
  const { menu, isLoading, error, loadMenu } = useDiscoveryStore();
  const add = useCartStore((s) => s.add);
  const [name, setName] = useState('');

  useEffect(() => {
    void loadMenu(String(id));
    vendors.get(String(id)).then((v) => setName(v.name)).catch(() => setName(''));
  }, [id]);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 22, fontWeight: '800' }}>{name || 'Kitchen'}</Text>
      <View style={{ height: 12 }} />
      <ScreenState loading={isLoading} error={error} empty={menu.length ? null : 'No dishes published'}>
        {menu.map((m) => (
          <FoodCard key={m.id} item={m} onPress={() => router.push(`/food/${m.id}`)} onAdd={() => void add(m.id, 1)} />
        ))}
      </ScreenState>
      <View style={{ height: 24 }} />
    </ScrollView>
  );
}
