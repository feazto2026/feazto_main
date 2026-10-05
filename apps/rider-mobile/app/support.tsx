import React, { useState } from 'react';
import { Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { Colors } from '../src/constants/theme';
import { support } from '../api/support';

export default function RiderSupport() {
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  return (
    <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16 }}>
      <Text style={{ fontSize: 20, fontWeight: '800' }}>Support</Text>
      {notice ? <Text style={{ marginTop: 8 }}>{notice}</Text> : null}
      <TextInput accessibilityLabel="Subject" placeholder="Subject" value={subject} onChangeText={setSubject} style={{ backgroundColor: 'white', borderRadius: 12, padding: 12, marginTop: 12 }} />
      <TextInput accessibilityLabel="Description" placeholder="What happened?" value={body} onChangeText={setBody} multiline style={{ backgroundColor: 'white', borderRadius: 12, padding: 12, marginTop: 8, minHeight: 90 }} />
      <Pressable
        onPress={() => { void support.createTicket(subject, body).then(() => setNotice('Ticket raised.')).catch((e) => setNotice(e instanceof Error ? e.message : 'Failed')); }}
        style={{ backgroundColor: Colors.darkPill, borderRadius: 999, padding: 14, marginTop: 12, alignItems: 'center' }}
      >
        <Text style={{ color: 'white', fontWeight: '800' }}>Raise ticket</Text>
      </Pressable>
      <Text style={{ color: Colors.textMuted, marginTop: 12 }}>SOS: { '+91 112' }</Text>
    </ScrollView>
  );
}
