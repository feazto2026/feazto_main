import React, { useEffect, useState } from 'react';
import { Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../../src/constants/colors';
import { ScreenState } from '../../src/components/ScreenState';
import { CookCard } from '../../src/components/cards';
import { useDiscoveryStore } from '../../src/store/discoveryStore';

const CATS = ['All', 'Breakfast', 'Main Course', 'Snacks'];

export default function SearchScreen() {
  const router = useRouter();
  const { vendors, search, category, isLoading, error, setSearch, setCategory, loadVendors } = useDiscoveryStore();
  const [q, setQ] = useState(search);

  useEffect(() => {
    const t = setTimeout(() => {
      setSearch(q);
      void loadVendors({ search: q || undefined });
    }, 400);
    return () => clearTimeout(t);
  }, [q]);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <TextInput
        accessibilityLabel="Search dishes or kitchens"
        placeholder="Search dishes or kitchens…"
        value={q}
        onChangeText={setQ}
        style={{ backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 12, padding: 12 }}
      />
      <View style={{ flexDirection: 'row', gap: 8, marginVertical: 12 }}>
        {CATS.map((c) => (
          <Pressable
            key={c}
            accessibilityRole="button"
            onPress={() => setCategory(c)}
            style={{ borderRadius: 999, paddingHorizontal: 12, paddingVertical: 8, backgroundColor: category === c ? Colors.darkPill : Colors.surface, borderWidth: 1, borderColor: Colors.border }}
          >
            <Text style={{ color: category === c ? Colors.textWhite : Colors.textPrimary, fontSize: 12, fontWeight: '700' }}>{c}</Text>
          </Pressable>
        ))}
      </View>
      <ScreenState loading={isLoading} error={error} empty={vendors.length ? null : 'No matches'}>
        {vendors.map((v) => (
          <CookCard key={v.id} cook={v} onPress={() => router.push(`/cook/${v.id}`)} />
        ))}
      </ScreenState>
    </ScrollView>
  );
}
