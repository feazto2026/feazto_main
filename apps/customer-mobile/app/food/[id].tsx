import React, { useEffect, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { ScreenState } from '../../src/components/ScreenState';
import { menus } from '../../api/menus';
import { useCartStore } from '../../src/store/cartStore';
import type { MenuItem } from '../../../../packages/mobile-shared/src/index.js';

export default function FoodDetailScreen() {
  const { id } = useLocalSearchParams() as { id: string };
  const [item, setItem] = useState<MenuItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const add = useCartStore((s) => s.add);

  useEffect(() => {
    (async () => {
      try {
        const m = await menus.getItem(String(id));
        setItem(m);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Could not load dish');
      } finally {
        setLoading(false);
      }
    })();
  }, [id]);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <ScreenState loading={loading} error={error} empty={item ? null : 'Dish not found'}>
        {item ? (
          <View style={{ backgroundColor: Colors.surface, borderRadius: 16, padding: 16 }}>
            <Text style={{ fontSize: 22, fontWeight: '800' }}>{item.name}</Text>
            {item.description ? <Text style={{ color: Colors.textSecondary, marginTop: 6 }}>{item.description}</Text> : null}
            <Text style={{ fontWeight: '800', marginTop: 10 }}>{'\u20B9'}{item.price}</Text>
            <Text style={{ fontSize: 12, color: item.available ? '#2E7D32' : '#888', fontWeight: '700', marginTop: 4 }}>
              {item.available ? 'Available' : 'Currently unavailable'}
            </Text>
            <Pressable
              accessibilityRole="button"
              disabled={!item.available}
              onPress={() => void add(item.id, 1)}
              style={{ backgroundColor: item.available ? Colors.darkPill : '#ccc', borderRadius: 999, padding: 14, marginTop: 16, alignItems: 'center' }}
            >
              <Text style={{ color: Colors.textWhite, fontWeight: '800' }}>Add to cart</Text>
            </Pressable>
          </View>
        ) : null}
      </ScreenState>
    </ScrollView>
  );
}
