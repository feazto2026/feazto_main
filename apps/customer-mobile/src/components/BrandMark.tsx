import React from 'react';
import { Text, View } from 'react-native';
import { Colors } from '../constants/colors';

export function BrandMark({ size = 'md' }: { size?: 'sm' | 'md' | 'lg' }) {
  const fontSize = size === 'lg' ? 40 : size === 'sm' ? 22 : 30;
  return (
    <View style={{ alignItems: 'center' }}>
      <Text accessibilityRole="header" style={{ fontSize, fontWeight: '900', fontStyle: 'italic', color: Colors.textPrimary }}>
        FEA<Text style={{ color: Colors.yellowPrimary }}>Z</Text>TO
      </Text>
      <Text style={{ fontSize: 11, color: Colors.textMuted, marginTop: 2 }}>food {'\u2022'} people {'\u2022'} culture</Text>
    </View>
  );
}
