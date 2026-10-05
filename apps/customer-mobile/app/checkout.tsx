import React, { useEffect, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../src/constants/colors';
import { ScreenState } from '../src/components/ScreenState';
import { cart } from '../api/cart';
import { useCartStore } from '../src/store/cartStore';
import { useUiStore } from '../src/store/uiStore';
import type { Address } from '../../../packages/mobile-shared/src/index.js';

export default function CheckoutScreen() {
  const router = useRouter();
  const { cart: c, refresh, clear } = useCartStore();
  const { deliveryMode, paymentMode, setDeliveryMode, setPaymentMode } = useUiStore();
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [addressId, setAddressId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void refresh();
    cart.addresses().then((a) => {
      setAddresses(a ?? []);
      if (a?.[0]) setAddressId(a[0].id);
    }).catch(() => setAddresses([]));
  }, []);

  async function place() {
    if (!addressId) {
      setError('Choose a delivery address');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const order = await cart.checkout(addressId, deliveryMode === 'express' ? 'EXPRESS' : 'STANDARD', paymentMode);
      await clear();
      router.replace(`/tracking/${order.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Checkout failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 22, fontWeight: '800' }}>Checkout</Text>
      {error ? <Text role="alert" style={{ color: Colors.error, marginTop: 8 }}>{error}</Text> : null}
      <Text style={{ fontWeight: '800', marginTop: 16 }}>ADDRESS</Text>
      <ScreenState loading={false} error={null} empty={addresses.length ? null : 'No saved addresses'}>
        {addresses.map((a) => (
          <Pressable key={a.id} accessibilityRole="radio" accessibilityState={{ selected: addressId === a.id }} onPress={() => setAddressId(a.id)} style={{ backgroundColor: Colors.surface, borderRadius: 12, padding: 12, marginTop: 8, borderWidth: 2, borderColor: addressId === a.id ? Colors.yellowPrimary : Colors.border }}>
            <Text style={{ fontWeight: '700' }}>{a.label}</Text>
            <Text style={{ color: Colors.textSecondary }}>{a.line1}, {a.city}</Text>
          </Pressable>
        ))}
      </ScreenState>
      <Text style={{ fontWeight: '800', marginTop: 16 }}>DELIVERY SPEED</Text>
      <View style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
        {(['standard', 'express'] as const).map((m) => (
          <Pressable key={m} onPress={() => setDeliveryMode(m)} style={{ flex: 1, borderRadius: 12, padding: 12, backgroundColor: deliveryMode === m ? Colors.darkPill : Colors.surface, borderWidth: 1, borderColor: Colors.border }}>
            <Text style={{ color: deliveryMode === m ? Colors.textWhite : Colors.textPrimary, fontWeight: '700', textTransform: 'capitalize' }}>{m}</Text>
          </Pressable>
        ))}
      </View>
      <Text style={{ fontWeight: '800', marginTop: 16 }}>PAYMENT</Text>
      <View style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
        {(['upi', 'card'] as const).map((m) => (
          <Pressable key={m} onPress={() => setPaymentMode(m)} style={{ flex: 1, borderRadius: 12, padding: 12, backgroundColor: paymentMode === m ? Colors.yellowPrimary : Colors.surface, borderWidth: 1, borderColor: Colors.border }}>
            <Text style={{ fontWeight: '700', textTransform: 'uppercase' }}>{m}</Text>
          </Pressable>
        ))}
      </View>
      <View style={{ backgroundColor: Colors.surface, borderRadius: 12, padding: 14, marginTop: 16 }}>
        <Text>Total: {'\u20B9'}{c?.total ?? 0} (priced server-side at checkout)</Text>
        <Pressable accessibilityRole="button" onPress={place} disabled={busy} style={{ backgroundColor: Colors.darkPill, borderRadius: 999, padding: 14, marginTop: 12, alignItems: 'center' }}>
          <Text style={{ color: Colors.textWhite, fontWeight: '800' }}>{busy ? 'Placing…' : 'Place order'}</Text>
        </Pressable>
      </View>
    </ScrollView>
  );
}
