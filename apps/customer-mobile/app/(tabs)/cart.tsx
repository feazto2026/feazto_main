import React, { useEffect } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { ScreenState } from '../../src/components/ScreenState';
import { cartCount, useCartStore } from '../../src/store/cartStore';

export default function CartScreen() {
  const router = useRouter();
  const { cart, isLoading, error, refresh, setQty } = useCartStore();

  useEffect(() => {
    void refresh();
  }, []);

  const count = cartCount(cart);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 22, fontWeight: '800' }}>Cart {count ? `(${count})` : ''}</Text>
      <ScreenState loading={isLoading} error={error} empty={cart?.lines?.length ? null : 'Your cart is empty'}>
        {(cart?.lines ?? []).map((l) => (
          <View key={l.itemId} style={{ backgroundColor: Colors.surface, borderRadius: 12, padding: 12, marginBottom: 8, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <View style={{ flex: 1 }}>
              <Text style={{ fontWeight: '700' }}>{l.name}</Text>
              <Text style={{ color: Colors.textSecondary }}>{'\u20B9'}{l.unitPrice} × {l.qty}</Text>
            </View>
            <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center' }}>
              <Pressable accessibilityLabel={`Decrease ${l.name}`} onPress={() => void setQty(l.itemId, l.qty - 1)} style={{ padding: 8, backgroundColor: Colors.background, borderRadius: 999 }}>
                <Text>−</Text>
              </Pressable>
              <Text>{l.qty}</Text>
              <Pressable accessibilityLabel={`Increase ${l.name}`} onPress={() => void setQty(l.itemId, l.qty + 1)} style={{ padding: 8, backgroundColor: Colors.background, borderRadius: 999 }}>
                <Text>+</Text>
              </Pressable>
            </View>
          </View>
        ))}
      </ScreenState>
      {cart && cart.lines.length ? (
        <View style={{ backgroundColor: Colors.surface, borderRadius: 12, padding: 14, marginTop: 8 }}>
          <Text>Subtotal: {'\u20B9'}{cart.subtotal}</Text>
          <Text>Delivery: {'\u20B9'}{cart.deliveryFee} {'\u2022'} Packaging: {'\u20B9'}{cart.packagingFee}</Text>
          <Text style={{ fontWeight: '800', marginTop: 6 }}>Total: {'\u20B9'}{cart.total}</Text>
          <Pressable
            accessibilityRole="button"
            onPress={() => router.push('/checkout')}
            style={{ backgroundColor: Colors.darkPill, borderRadius: 999, padding: 14, marginTop: 12, alignItems: 'center' }}
          >
            <Text style={{ color: Colors.textWhite, fontWeight: '800' }}>Checkout</Text>
          </Pressable>
        </View>
      ) : null}
    </ScrollView>
  );
}
