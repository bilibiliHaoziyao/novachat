/**
 * Nova Chat: minimal WebDAV client and backup container encryption.
 *
 * The container format is shared with the Android client so that a backup
 * uploaded from one device can be restored on the other one
 * (see apps/android/.../connect/WebDavSyncManager.java):
 *
 *   MAGIC('NC1') | salt(16) | iv(12) | AES-256-GCM(ciphertext + tag)
 *
 * Key derivation: PBKDF2-HMAC-SHA256, 120000 iterations, 256 bit key.
 * The WebDAV server only ever sees the encrypted container.
 */
import {
  createCipheriv,
  createDecipheriv,
  pbkdf2Sync,
  randomBytes,
} from 'crypto'

export const NOVA_WEBDAV_FOLDER = 'nova-chat'
export const NOVA_WEBDAV_FILE = 'latest-backup.ac'

const MAGIC = Buffer.from('NC1', 'ascii')
const SALT_BYTES = 16
const IV_BYTES = 12
const PBKDF2_ITERATIONS = 120_000
const KEY_BYTES = 32
const GCM_TAG_BYTES = 16
const REQUEST_TIMEOUT_MS = 120_000

export interface NovaWebdavSettings {
  url: string
  username: string
  password: string
  passphrase: string
  autoSync: boolean
}

export function emptyWebdavSettings(): NovaWebdavSettings {
  return {
    url: '',
    username: '',
    password: '',
    passphrase: '',
    autoSync: false,
  }
}

function deriveKey(passphrase: string, salt: Buffer): Buffer {
  return pbkdf2Sync(passphrase, salt, PBKDF2_ITERATIONS, KEY_BYTES, 'sha256')
}

export function encryptBackupContainer(
  plain: Buffer,
  passphrase: string
): Buffer {
  const salt = randomBytes(SALT_BYTES)
  const iv = randomBytes(IV_BYTES)
  const cipher = createCipheriv(
    'aes-256-gcm',
    deriveKey(passphrase, salt),
    iv,
    {
      authTagLength: GCM_TAG_BYTES,
    }
  )
  const ciphertext = Buffer.concat([cipher.update(plain), cipher.final()])
  return Buffer.concat([MAGIC, salt, iv, ciphertext, cipher.getAuthTag()])
}

export function decryptBackupContainer(
  encrypted: Buffer,
  passphrase: string
): Buffer {
  if (encrypted.length < MAGIC.length + SALT_BYTES + IV_BYTES + GCM_TAG_BYTES) {
    throw new Error('备份文件不完整')
  }
  if (!encrypted.subarray(0, MAGIC.length).equals(MAGIC)) {
    throw new Error('不是 Nova Chat 备份文件（magic 不匹配）')
  }
  let offset = MAGIC.length
  const salt = encrypted.subarray(offset, (offset += SALT_BYTES))
  const iv = encrypted.subarray(offset, (offset += IV_BYTES))
  const authTag = encrypted.subarray(encrypted.length - GCM_TAG_BYTES)
  const ciphertext = encrypted.subarray(
    offset,
    encrypted.length - GCM_TAG_BYTES
  )

  const decipher = createDecipheriv(
    'aes-256-gcm',
    deriveKey(passphrase, salt),
    iv,
    { authTagLength: GCM_TAG_BYTES }
  )
  decipher.setAuthTag(authTag)
  try {
    return Buffer.concat([decipher.update(ciphertext), decipher.final()])
  } catch (_error) {
    throw new Error('解密失败，备份口令可能不正确')
  }
}

function normalizeBaseUrl(url: string): string {
  return url.trim().replace(/\/+$/, '')
}

export function remoteFolderUrl(baseUrl: string): string {
  return `${normalizeBaseUrl(baseUrl)}/${NOVA_WEBDAV_FOLDER}`
}

export function remoteBackupUrl(baseUrl: string): string {
  return `${remoteFolderUrl(baseUrl)}/${NOVA_WEBDAV_FILE}`
}

function authorizationHeader(settings: NovaWebdavSettings): string {
  const credentials = `${settings.username}:${settings.password ?? ''}`
  return `Basic ${Buffer.from(credentials, 'utf-8').toString('base64')}`
}

async function webdavRequest(
  settings: NovaWebdavSettings,
  url: string,
  method: string,
  body?: Buffer
): Promise<Response> {
  return fetch(url, {
    method,
    headers: {
      Authorization: authorizationHeader(settings),
      ...(body ? { 'Content-Type': 'application/octet-stream' } : {}),
    },
    body: body as unknown as BodyInit | undefined,
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  })
}

function assertUrl(url: string) {
  if (!/^https?:\/\//i.test(url.trim())) {
    throw new Error('WebDAV 地址必须以 http:// 或 https:// 开头')
  }
}

/** Creates the remote `nova-chat` collection if it does not exist yet. */
export async function ensureRemoteFolder(
  settings: NovaWebdavSettings
): Promise<void> {
  assertUrl(settings.url)
  const response = await webdavRequest(
    settings,
    remoteFolderUrl(settings.url),
    'MKCOL'
  )
  // 201 = created, 405 = already exists, 200/204 = some servers answer this way
  if (![200, 201, 204, 301, 302, 405].includes(response.status)) {
    throw new Error(
      `无法创建远程目录，HTTP ${response.status} ${response.statusText}`
    )
  }
}

export async function uploadBackupFile(
  settings: NovaWebdavSettings,
  data: Buffer
): Promise<void> {
  await ensureRemoteFolder(settings)
  const response = await webdavRequest(
    settings,
    remoteBackupUrl(settings.url),
    'PUT',
    data
  )
  if (response.status < 200 || response.status >= 300) {
    throw new Error(`上传失败，HTTP ${response.status} ${response.statusText}`)
  }
}

export async function downloadBackupFile(
  settings: NovaWebdavSettings
): Promise<Buffer> {
  assertUrl(settings.url)
  const response = await webdavRequest(
    settings,
    remoteBackupUrl(settings.url),
    'GET'
  )
  if (response.status === 404) {
    throw new Error('云端还没有备份文件')
  }
  if (response.status !== 200) {
    throw new Error(`下载失败，HTTP ${response.status} ${response.statusText}`)
  }
  return Buffer.from(await response.arrayBuffer())
}

export interface NovaWebdavCheckResult {
  ok: boolean
  message: string
}

/**
 * Checks that the server is reachable and that the credentials
 * allow creating the folder and writing files into it.
 */
export async function testConnection(
  settings: NovaWebdavSettings
): Promise<NovaWebdavCheckResult> {
  try {
    await ensureRemoteFolder(settings)
    const probeUrl = `${remoteFolderUrl(settings.url)}/.nova-probe`
    const putResponse = await webdavRequest(
      settings,
      probeUrl,
      'PUT',
      Buffer.from('nova-chat')
    )
    if (putResponse.status < 200 || putResponse.status >= 300) {
      return {
        ok: false,
        message: `无法写入远程目录，HTTP ${putResponse.status}`,
      }
    }
    await webdavRequest(settings, probeUrl, 'DELETE')
    return { ok: true, message: '连接成功，目录可读写' }
  } catch (error) {
    return {
      ok: false,
      message: error instanceof Error ? error.message : String(error),
    }
  }
}
