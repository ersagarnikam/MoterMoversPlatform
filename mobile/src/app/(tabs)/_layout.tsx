import Ionicons from '@expo/vector-icons/Ionicons'
import { Tabs } from 'expo-router'
import type { ComponentProps } from 'react'
import { Platform, type ColorValue } from 'react-native'
import { usePalette } from '@/lib/theme'

type IconName = ComponentProps<typeof Ionicons>['name']

function icon(name: IconName, focusedName: IconName) {
  return ({ color, focused, size }: { color: ColorValue; focused: boolean; size: number }) => (
    <Ionicons name={focused ? focusedName : name} color={color as string} size={size} />
  )
}

export default function TabsLayout() {
  const p = usePalette()
  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: p.header },
        headerTintColor: p.headerInk,
        headerTitleStyle: { fontWeight: '700' },
        headerShadowVisible: false,
        tabBarActiveTintColor: p.teal,
        tabBarInactiveTintColor: p.tabInactive,
        // Native tab bars size themselves around the safe area; the browser preview needs a little more room.
        tabBarStyle: [
          { backgroundColor: p.paper, borderTopColor: p.line },
          Platform.OS === 'web' ? { height: 64, paddingBottom: 8 } : null,
        ],
        sceneStyle: { backgroundColor: p.page },
      }}
    >
      <Tabs.Screen name="index" options={{ title: 'Today', tabBarIcon: icon('sunny-outline', 'sunny') }} />
      <Tabs.Screen
        name="progress"
        options={{ title: 'Progress', tabBarIcon: icon('trending-up-outline', 'trending-up') }}
      />
      <Tabs.Screen name="guidance" options={{ title: 'Guidance', tabBarIcon: icon('book-outline', 'book') }} />
      <Tabs.Screen name="course" options={{ title: 'Course', tabBarIcon: icon('people-outline', 'people') }} />
      <Tabs.Screen
        name="account"
        options={{ title: 'Account', tabBarIcon: icon('person-circle-outline', 'person-circle') }}
      />
    </Tabs>
  )
}
