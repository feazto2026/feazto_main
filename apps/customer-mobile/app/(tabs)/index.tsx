import React, { useEffect } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { Link, useRouter } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { ScreenState } from '../../src/components/ScreenState';
import { CookCard, FoodCard } from '../../src/components/cards';
import { useDiscoveryStore } from '../../src/store/discoveryStore';
import { useCartStore } from '../../src/store/cartStore';
import { useUiStore } from '../../src/store/uiStore';

export default function HomeScreen() {
  const router = useRouter();
  const { vendors, isLoading, error, loadVendors } = useDiscoveryStore();
  const add = useCartStore((s) => s.add);
  const showToast = useUiStore((s) => s.showToast);

  useEffect(() => {
    void loadVendors();
  }, []);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <View style={{ backgroundColor: Colors.headerCream, borderRadius: 16, padding: 14, marginBottom: 14 }}>
        <Text style={{ fontSize: 18, fontWeight: '800' }}>Good food. Good people.</Text>
        <Text style={{ color: Colors.textSecondary, marginTop: 4 }}>Homemade regional kitchens near you</Text>
        <View style={{ flexDirection: 'row', gap: 8, marginTop: 12 }}>
          <Link href="/(tabs)/search" asChild>
            <Pressable style={{ backgroundColor: Colors.darkPill, borderRadius: 999, paddingHorizontal: 16, paddingVertical: 10 }}>
              <Text style={{ color: Colors.textWhite, fontWeight: '700' }}>Search dishes</Text>
            </Pressable>
          </Link>
          <Pressable
            accessibilityRole="button"
            onPress={() => router.push('/book-a-cook')}
            style={{ backgroundColor: Colors.yellowPrimary, borderRadius: 999, paddingHorizontal: 16, paddingVertical: 10 }}
          >
            <Text style={{ fontWeight: '800' }}>Book a cook</Text>
          </Pressable>
        </View>
      </View>

      <Text style={{ fontSize: 12, fontWeight: '800', color: Colors.textMuted, marginBottom: 8 }}>NEARBY HOME KITCHENS</Text>
      <ScreenState loading={isLoading} error={error} empty={vendors.length ? null : 'No kitchens yet'}>
        {vendors.slice(0, 6).map((v) => (
          <CookCard key={v.id} cook={v} onPress={() => router.push(`/cook/${v.id}`)} />
        ))}
      </ScreenState>

      <Text style={{ fontSize: 12, fontWeight: '800', color: Colors.textMuted, marginVertical: 8 }}>POPULAR NOW</Text>
      <ScreenState loading={false} error={null}>
        {(vendors[0] ? [] : []).map(() => null)}
        <FoodCard
          item={{ id: 'sample', vendorId: vendors[0]?.id ?? '', name: 'Today\u2019s special thali', price: 149, available: true }}
          onPress={() => vendors[0] && router.push(`/cook/${vendors[0].id}`)}
          onAdd={async () => {
            showToast('Open a kitchen to add dishes');
          }}
        />
      </ScreenState>
      <View style={{ display: 'none' }}>
        <Text>{String(add)}</Text>
      </View>
    </ScrollView>
  );
}
