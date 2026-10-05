import React from 'react';
import { Pressable, Text, View } from 'react-native';
import { Colors } from '../constants/colors';
import type { MenuItem, VendorSummary } from '../../../../packages/mobile-shared/src/index.js';

export function FoodCard({ item, onPress, onAdd }: { key?: string | number; item: MenuItem; onPress?: () => void; onAdd?: () => void }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`Open ${item.name}`}
      onPress={onPress}
      style={{ backgroundColor: Colors.surface, borderRadius: 16, padding: 12, borderWidth: 1, borderColor: Colors.border, marginBottom: 10 }}
    >
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: 10 }}>
        <View style={{ flex: 1 }}>
          <Text style={{ fontWeight: '800', fontSize: 14 }}>{item.name}</Text>
          <Text style={{ fontWeight: '700', marginVertical: 4 }}>{'\u20B9'}{item.price}</Text>
          <Text style={{ fontSize: 11, color: item.available ? '#2E7D32' : '#888888', fontWeight: '700' }}>
            {item.available ? 'Available' : 'Unavailable'}
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`Add ${item.name} to cart`}
          onPress={onAdd}
          disabled={!item.available}
          style={{ backgroundColor: item.available ? Colors.darkPill : '#cccccc', borderRadius: 999, paddingHorizontal: 14, paddingVertical: 8, alignSelf: 'flex-start' }}
        >
          <Text style={{ color: Colors.textWhite, fontWeight: '800', fontSize: 12 }}>ADD</Text>
        </Pressable>
      </View>
    </Pressable>
  );
}

export function CookCard({ cook, onPress }: { key?: string | number; cook: VendorSummary; onPress?: () => void }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`Open cook ${cook.name}`}
      onPress={onPress}
      style={{ backgroundColor: Colors.surface, borderRadius: 16, padding: 12, borderWidth: 1, borderColor: Colors.borderWarm, marginBottom: 10 }}
    >
      <Text style={{ fontWeight: '800', fontSize: 14 }}>{cook.name}</Text>
      <Text style={{ fontSize: 12, color: Colors.textSecondary, marginTop: 2 }}>
        {(cook.specialtyRegions ?? []).join(' \u2022 ') || cook.city || 'Home kitchen'}
      </Text>
      {typeof cook.rating === 'number' ? (
        <Text style={{ fontSize: 12, marginTop: 4 }}>{'\u2605'} {cook.rating.toFixed(1)}</Text>
      ) : null}
    </Pressable>
  );
}
