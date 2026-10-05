import React, { useEffect, useState } from 'react';
import { Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { Colors } from '../src/constants/colors';
import { ScreenState } from '../src/components/ScreenState';
import { bookings } from '../api/bookings';
import { vendors } from '../api/vendors';
import type { VendorSummary } from '../../../packages/mobile-shared/src/index.js';

export default function BookACookScreen() {
  const [cooks, setCooks] = useState<VendorSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [cookId, setCookId] = useState('');
  const [date, setDate] = useState('');
  const [guests, setGuests] = useState('4');
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const page = await vendors.list({ page: 0, pageSize: 20 });
        setCooks(page.content ?? []);
        if (page.content?.[0]) setCookId(page.content[0].id);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Could not load cooks');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  async function submit() {
    setNotice(null);
    if (!cookId || !date) {
      setNotice('Pick a cook and a date (YYYY-MM-DD).');
      return;
    }
    try {
      const b = await bookings.create({ cookId, date, slot: 'DINNER', addressId: 'home', guests: Number(guests) || 2 });
      setNotice(`Booking ${b.id} ${b.status}. The cook confirms in-app.`);
    } catch (e) {
      setNotice(e instanceof Error ? e.message : 'Booking failed');
    }
  }

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 22, fontWeight: '800' }}>Book a cook</Text>
      <Text style={{ color: Colors.textSecondary, marginTop: 4 }}>At-home chef, server-confirmed — no demo bookings.</Text>
      {notice ? <Text style={{ marginTop: 8 }}>{notice}</Text> : null}
      <ScreenState loading={loading} error={error} empty={cooks.length ? null : 'No cooks available'}>
        {cooks.map((c) => (
          <Pressable key={c.id} onPress={() => setCookId(c.id)} style={{ backgroundColor: Colors.surface, borderRadius: 12, padding: 12, marginTop: 8, borderWidth: 2, borderColor: cookId === c.id ? Colors.yellowPrimary : Colors.border }}>
            <Text style={{ fontWeight: '700' }}>{c.name}</Text>
          </Pressable>
        ))}
      </ScreenState>
      <TextInput accessibilityLabel="Booking date" placeholder="YYYY-MM-DD" value={date} onChangeText={setDate} style={{ backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 12, padding: 12, marginTop: 12 }} />
      <TextInput accessibilityLabel="Guests" keyboardType="number-pad" placeholder="Guests" value={guests} onChangeText={setGuests} style={{ backgroundColor: Colors.surface, borderWidth: 1, borderColor: Colors.border, borderRadius: 12, padding: 12, marginTop: 8 }} />
      <Pressable accessibilityRole="button" onPress={submit} style={{ backgroundColor: Colors.yellowPrimary, borderRadius: 999, padding: 14, marginTop: 12, alignItems: 'center' }}>
        <Text style={{ fontWeight: '800' }}>Request booking</Text>
      </Pressable>
      <View style={{ height: 24 }} />
    </ScrollView>
  );
}
