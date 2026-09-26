import type { T } from '@deltachat/jsonrpc-client'
import type { TranslationKey } from '@deltachat-desktop/shared/translationKeyType'

import type { Credentials } from '../../Settings/DefaultCredentials'

/**
 * Nova Chat: e-mail provider presets.
 *
 * The wizard uses them to pre-fill the IMAP/SMTP settings, so users do not
 * have to know anything about server addresses, ports or encryption.
 */
export type NovaProviderPreset = {
  id: string
  /** brand name, not translated */
  label: string
  domains: string[]
  /** translation key hinting at what the user needs (e.g. an app password) */
  noteKey?: TranslationKey
  imap: { server: string; port: number; security: T.Socket }
  smtp: { server: string; port: number; security: T.Socket }
}

export const NOVA_PROVIDER_PRESETS: NovaProviderPreset[] = [
  {
    id: 'qq',
    label: 'QQ Mail',
    domains: ['qq.com', 'foxmail.com'],
    noteKey: 'nova_provider_note_qq',
    imap: { server: 'imap.qq.com', port: 993, security: 'ssl' },
    smtp: { server: 'smtp.qq.com', port: 465, security: 'ssl' },
  },
  {
    id: '163',
    label: '163 Mail',
    domains: ['163.com'],
    noteKey: 'nova_provider_note_163',
    imap: { server: 'imap.163.com', port: 993, security: 'ssl' },
    smtp: { server: 'smtp.163.com', port: 465, security: 'ssl' },
  },
  {
    id: '126',
    label: '126 Mail',
    domains: ['126.com'],
    noteKey: 'nova_provider_note_163',
    imap: { server: 'imap.126.com', port: 993, security: 'ssl' },
    smtp: { server: 'smtp.126.com', port: 465, security: 'ssl' },
  },
  {
    id: 'sina',
    label: 'Sina Mail',
    domains: ['sina.com', 'sina.cn'],
    imap: { server: 'imap.sina.com', port: 993, security: 'ssl' },
    smtp: { server: 'smtp.sina.com', port: 465, security: 'ssl' },
  },
  {
    id: 'gmail',
    label: 'Gmail',
    domains: ['gmail.com', 'googlemail.com'],
    noteKey: 'nova_provider_note_gmail',
    imap: { server: 'imap.gmail.com', port: 993, security: 'ssl' },
    smtp: { server: 'smtp.gmail.com', port: 465, security: 'ssl' },
  },
  {
    id: 'outlook',
    label: 'Outlook',
    domains: ['outlook.com', 'hotmail.com', 'live.com', 'msn.com'],
    imap: { server: 'outlook.office365.com', port: 993, security: 'ssl' },
    smtp: { server: 'smtp.office365.com', port: 587, security: 'starttls' },
  },
  {
    id: 'icloud',
    label: 'iCloud',
    domains: ['icloud.com', 'me.com', 'mac.com'],
    noteKey: 'nova_provider_note_icloud',
    imap: { server: 'imap.mail.me.com', port: 993, security: 'ssl' },
    smtp: { server: 'smtp.mail.me.com', port: 587, security: 'starttls' },
  },
  {
    id: 'yahoo',
    label: 'Yahoo',
    domains: ['yahoo.com', 'yahoo.com.cn'],
    imap: { server: 'imap.mail.yahoo.com', port: 993, security: 'ssl' },
    smtp: { server: 'smtp.mail.yahoo.com', port: 465, security: 'ssl' },
  },
  {
    id: 'custom',
    label: 'Other / custom',
    domains: [],
    noteKey: 'nova_provider_note_custom',
    imap: { server: '', port: 993, security: 'automatic' },
    smtp: { server: '', port: 465, security: 'automatic' },
  },
]

export function getPresetById(id: string): NovaProviderPreset | undefined {
  return NOVA_PROVIDER_PRESETS.find(preset => preset.id === id)
}

export function getDomainOfAddress(addr: string): string {
  const atIndex = addr.lastIndexOf('@')
  if (atIndex === -1) {
    return ''
  }
  return addr
    .slice(atIndex + 1)
    .trim()
    .toLowerCase()
}

export function detectPresetForAddress(
  addr: string
): NovaProviderPreset | undefined {
  const domain = getDomainOfAddress(addr)
  if (!domain) {
    return undefined
  }
  return NOVA_PROVIDER_PRESETS.find(
    preset =>
      preset.id !== 'custom' &&
      preset.domains.some(
        presetDomain =>
          domain === presetDomain || domain.endsWith(`.${presetDomain}`)
      )
  )
}

/** Merges the server settings of a preset into the credentials. */
export function applyPresetToCredentials(
  credentials: Credentials,
  preset: NovaProviderPreset
): Credentials {
  return {
    ...credentials,
    imapServer: preset.imap.server || null,
    imapPort: preset.imap.server ? preset.imap.port : null,
    imapSecurity: preset.imap.security,
    smtpServer: preset.smtp.server || null,
    smtpPort: preset.smtp.server ? preset.smtp.port : null,
    smtpSecurity: preset.smtp.security,
  }
}
