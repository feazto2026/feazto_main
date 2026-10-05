import React, { useEffect, useState } from 'react';
import { ScrollView, Switch, Text, View } from 'react-native';
import { Colors } from '../../src/constants/colors';
import { useKitchenStore } from '../../src/store/kitchenStore';

const VENDOR_ID_FALLBACK = 'mine';

export default function VendorMenu() {
  const { menu, isLoading, error, loadMenu, toggleItem } = useKitchenStore();
  const [vendorId] = useState(VENDOR_ID_FALLBACK);
  useEffect(() => {
    void loadMenu(vendorId);
  }, [vendorId]);
  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 20, fontWeight: '800' }}>Menu & availability {isLoading ? '(loading…)' : ''}</Text>
      {error ? <Text role="alert" style={{ color: Colors.error }}>{error}</Text> : null}
      {menu.map((m) => (
        <View key={m.id} style={{ backgroundColor: 'white', borderRadius: 12, padding: 12, marginTop: 8, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <View style={{ flex: 1 }}>
            <Text style={{ fontWeight: '700' }}>{m.name}</Text>
            <Text>{'\u20B9'}{m.price}</Text>
          </View>
          <Switch accessibilityLabel={`Availability ${m.name}`} value={m.available} onValueChange={(v: boolean) => void toggleItem(m.id, v)} />
        </View>
      ))}
      {menu.length === 0 && !isLoading ? <Text style={{ color: Colors.muted, marginTop: 12 }}>No menu items.</Text> : null}
    </ScrollView>
  );
}
