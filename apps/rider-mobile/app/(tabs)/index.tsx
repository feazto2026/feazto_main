import React, { useEffect } from 'react';
import { Pressable, ScrollView, Switch, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Colors } from '../../src/constants/theme';
import { useRiderWorkStore } from '../../src/store/riderStore';

export default function RiderHome() {
  const router = useRouter();
  const { online, available, isLoading, error, setOnline, loadAvailable, accept } = useRiderWorkStore();

  useEffect(() => {
    if (online) void loadAvailable();
  }, [online]);

  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <View style={{ backgroundColor: 'white', borderRadius: 16, padding: 14, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <View>
          <Text style={{ fontWeight: '800', fontSize: 16 }}>Hello, partner</Text>
          <Text style={{ color: online ? Colors.online : Colors.offline, fontWeight: '800', marginTop: 4 }}>
            {online ? '● ONLINE — ready for deliveries' : '● OFFLINE'}
          </Text>
        </View>
        <Switch accessibilityLabel="Rider online" value={online} onValueChange={setOnline} />
      </View>
      {error ? <Text role="alert" style={{ color: Colors.error, marginTop: 8 }}>{error}</Text> : null}
      <Text style={{ fontWeight: '800', marginVertical: 12 }}>AVAILABLE DELIVERIES {isLoading ? '(loading…)' : ''}</Text>
      {!online ? <Text style={{ color: Colors.textMuted }}>Go online to receive delivery offers.</Text> : null}
      {online && available.map((d) => (
        <View key={d.id} style={{ backgroundColor: 'white', borderRadius: 12, padding: 12, marginBottom: 8 }}>
          <Text style={{ fontWeight: '800' }}>{d.orderId.slice(0, 8)}… {'\u2022'} {d.status}</Text>
          <Text style={{ color: Colors.textMuted }}>{d.address ?? ''}</Text>
          <View style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
            <Pressable onPress={() => void accept(d.id).then((ok) => { if (ok) router.push('/delivery'); })} style={{ backgroundColor: Colors.darkPill, borderRadius: 999, paddingHorizontal: 14, paddingVertical: 8 }}>
              <Text style={{ color: 'white', fontWeight: '700' }}>Accept</Text>
            </Pressable>
          </View>
        </View>
      ))}
      {online && available.length === 0 && !isLoading ? <Text style={{ color: Colors.textMuted }}>No deliveries right now.</Text> : null}
    </ScrollView>
  );
}
