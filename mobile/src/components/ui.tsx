import type { ReactNode } from 'react'
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  Text,
  TextInput,
  View,
  type TextInputProps,
  type TextStyle,
  type ViewStyle,
} from 'react-native'
import type { ColourStage } from '@/lib/api'
import { radius, space, STAGES, stageInfo, usePalette } from '@/lib/theme'

export function Screen({
  children,
  refreshing,
  onRefresh,
}: {
  children: ReactNode
  refreshing?: boolean
  onRefresh?: () => void
}) {
  const p = usePalette()
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: p.page }}
      contentContainerStyle={{ padding: space.lg, gap: space.md, paddingBottom: space.xl * 2 }}
      keyboardShouldPersistTaps="handled"
      refreshControl={
        onRefresh ? <RefreshControl refreshing={!!refreshing} onRefresh={onRefresh} tintColor={p.teal} /> : undefined
      }
    >
      <View style={{ width: '100%', maxWidth: 640, alignSelf: 'center', gap: space.md }}>{children}</View>
    </ScrollView>
  )
}

export function Card({ children, accent, style }: { children: ReactNode; accent?: string; style?: ViewStyle }) {
  const p = usePalette()
  return (
    <View
      style={[
        {
          backgroundColor: p.paper,
          borderRadius: radius,
          borderWidth: 1,
          borderColor: p.line,
          padding: space.lg,
          gap: space.sm,
        },
        accent ? { borderLeftWidth: 5, borderLeftColor: accent } : null,
        style,
      ]}
    >
      {children}
    </View>
  )
}

type TextProps = { children: ReactNode; style?: TextStyle; numberOfLines?: number }

export function H1({ children, style }: TextProps) {
  const p = usePalette()
  return <Text style={[{ fontSize: 24, fontWeight: '700', color: p.ink }, style]}>{children}</Text>
}

export function H2({ children, style }: TextProps) {
  const p = usePalette()
  return <Text style={[{ fontSize: 18, fontWeight: '700', color: p.ink }, style]}>{children}</Text>
}

export function Body({ children, style, numberOfLines }: TextProps) {
  const p = usePalette()
  return (
    <Text numberOfLines={numberOfLines} style={[{ fontSize: 16, lineHeight: 23, color: p.ink }, style]}>
      {children}
    </Text>
  )
}

export function Muted({ children, style, numberOfLines }: TextProps) {
  const p = usePalette()
  return (
    <Text numberOfLines={numberOfLines} style={[{ fontSize: 14, lineHeight: 20, color: p.muted }, style]}>
      {children}
    </Text>
  )
}

export function Button({
  label,
  onPress,
  variant = 'primary',
  disabled,
  busy,
}: {
  label: string
  onPress: () => void
  variant?: 'primary' | 'secondary' | 'danger'
  disabled?: boolean
  busy?: boolean
}) {
  const p = usePalette()
  const colours = {
    primary: { bg: p.teal, ink: p.onTeal, border: p.teal },
    secondary: { bg: 'transparent', ink: p.ink, border: p.line },
    danger: { bg: 'transparent', ink: p.error, border: p.error },
  }[variant]
  const off = disabled || busy
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: off, busy }}
      disabled={off}
      onPress={onPress}
      style={({ pressed }) => ({
        backgroundColor: colours.bg,
        borderColor: colours.border,
        borderWidth: 1,
        borderRadius: radius,
        paddingVertical: 14,
        paddingHorizontal: space.lg,
        alignItems: 'center',
        opacity: off ? 0.5 : pressed ? 0.85 : 1,
      })}
    >
      {busy ? (
        <ActivityIndicator color={colours.ink} />
      ) : (
        <Text style={{ color: colours.ink, fontSize: 16, fontWeight: '600' }}>{label}</Text>
      )}
    </Pressable>
  )
}

export function Banner({ children, tone = 'warn' }: { children: ReactNode; tone?: 'warn' | 'error' | 'ok' }) {
  const p = usePalette()
  const c = {
    warn: { bg: p.warnBg, ink: p.warnInk },
    error: { bg: p.warnBg, ink: p.error },
    ok: { bg: p.tealSoft, ink: p.ink },
  }[tone]
  return (
    <View style={{ backgroundColor: c.bg, borderRadius: radius, padding: space.md }}>
      <Text style={{ color: c.ink, fontSize: 14, lineHeight: 20 }}>{children}</Text>
    </View>
  )
}

/** Always shown with readiness information. Wording comes from the API. */
export function NotADiagnosis({ text }: { text: string }) {
  return <Banner>{text}</Banner>
}

export function StageBadge({ stage, label }: { stage: ColourStage | null | undefined; label?: string | null }) {
  const p = usePalette()
  const info = stageInfo(stage)
  if (!info) return <Muted>Not placed yet</Muted>
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: space.sm }}>
      <View style={{ width: 14, height: 14, borderRadius: 7, backgroundColor: info.colour }} />
      <Text style={{ color: p.ink, fontSize: 15, fontWeight: '600' }}>{label ?? info.label}</Text>
    </View>
  )
}

/** The seven stage colours as a thin band: the Motor Movers mark. */
export function StageStripe({ height = 6 }: { height?: number }) {
  return (
    <View style={{ flexDirection: 'row', height, borderRadius: height / 2, overflow: 'hidden' }}>
      {STAGES.map((s) => (
        <View key={s.key} style={{ flex: 1, backgroundColor: s.colour }} />
      ))}
    </View>
  )
}

export function Chip({
  label,
  selected,
  onPress,
  dot,
}: {
  label: string
  selected?: boolean
  onPress: () => void
  dot?: string
}) {
  const p = usePalette()
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: 6,
        paddingVertical: 8,
        paddingHorizontal: 12,
        borderRadius: 999,
        borderWidth: 1,
        borderColor: selected ? p.ink : p.line,
        backgroundColor: selected ? p.ink : p.paper,
      }}
    >
      {dot && <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: dot }} />}
      <Text style={{ color: selected ? p.page : p.ink, fontSize: 14, fontWeight: '600' }}>{label}</Text>
    </Pressable>
  )
}

export function Field({ label, hint, ...input }: TextInputProps & { label: string; hint?: string }) {
  const p = usePalette()
  return (
    <View style={{ gap: 6 }}>
      <Text style={{ color: p.ink, fontSize: 14, fontWeight: '600' }}>{label}</Text>
      <TextInput
        placeholderTextColor={p.muted}
        {...input}
        style={{
          borderWidth: 1,
          borderColor: p.line,
          borderRadius: radius,
          paddingHorizontal: space.md,
          paddingVertical: 12,
          fontSize: 16,
          color: p.ink,
          backgroundColor: p.paper,
        }}
      />
      {hint && <Muted>{hint}</Muted>}
    </View>
  )
}

export function Loading() {
  const p = usePalette()
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: space.xl, backgroundColor: p.page }}>
      <ActivityIndicator color={p.teal} size="large" />
    </View>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <Screen>
      <Banner tone="error">{message}</Banner>
      <Button label="Try again" variant="secondary" onPress={onRetry} />
    </Screen>
  )
}

/** One square per day, oldest first; filled when something was done that day. */
export function CounterGrid({ grid }: { grid: boolean[] }) {
  const p = usePalette()
  return (
    <View
      style={{ flexDirection: 'row', gap: 4 }}
      accessibilityRole="image"
      accessibilityLabel={`${grid.filter(Boolean).length} of the last ${grid.length} days`}
    >
      {grid.map((done, i) => (
        <View
          key={i}
          style={{
            flex: 1,
            maxWidth: 24,
            aspectRatio: 1,
            borderRadius: 4,
            backgroundColor: done ? p.teal : p.track,
            borderWidth: i === grid.length - 1 ? 2 : 0,
            borderColor: p.ink,
          }}
        />
      ))}
    </View>
  )
}

export function Row({ children, style }: { children: ReactNode; style?: ViewStyle }) {
  return <View style={[{ flexDirection: 'row', alignItems: 'center', gap: space.sm, flexWrap: 'wrap' }, style]}>{children}</View>
}
