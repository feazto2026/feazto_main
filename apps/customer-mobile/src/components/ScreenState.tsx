import React from 'react';
import type { ReactNode } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { Colors } from '../constants/colors';

export function ScreenState({ loading, error, empty, children }: { loading?: boolean; error?: string | null; empty?: string | null; children?: ReactNode }) {
  if (loading) {
    return (
      <View style={{ padding: 24, alignItems: 'center' }}>
        <ActivityIndicator color={Colors.yellowPrimary} />
        <Text style={{ marginTop: 8, color: Colors.textMuted }}>Loading…</Text>
      </View>
    );
  }
  if (error) {
    return (
      <View style={{ padding: 16, backgroundColor: Colors.errorSoft, borderRadius: 12, marginBottom: 12 }}>
        <Text role="alert" style={{ color: '#B3261E' }}>{error}</Text>
      </View>
    );
  }
  if (empty && !children) {
    return (
      <View style={{ padding: 24, alignItems: 'center' }}>
        <Text style={{ color: Colors.textMuted }}>{empty}</Text>
      </View>
    );
  }
  return <>{children}</>;
}
