/**
 * Nova Chat: WebDAV account sync.
 *
 * Exports an encrypted core backup, encrypts it with the user's backup
 * passphrase and uploads it to the user's WebDAV server. Restoring works the
 * other way round: download, decrypt and hand the plain backup over to the
 * core (`importBackup`).
 *
 * File layout on the server (compatible with the Android client):
 *   <webdav-url>/nova-chat/latest-backup.ac
 */
import { mkdir, readFile, readdir, rm, stat, writeFile } from 'fs/promises'
import { join } from 'path'

import { getLogger } from '@deltachat-desktop/shared/logger.js'
import { getConfigPath, getTempDir } from '../application-constants.js'
import {
  NovaWebdavSettings,
  decryptBackupContainer,
  downloadBackupFile,
  emptyWebdavSettings,
  encryptBackupContainer,
  testConnection,
  uploadBackupFile,
} from './webdav-client.js'

const log = getLogger('main/nova-webdav')

/** Minimal subset of the jsonrpc client we need here. */
export interface NovaBackupRpc {
  exportBackup(
    accountId: number,
    path: string,
    passphrase: string | null
  ): Promise<unknown>
  importBackup(
    accountId: number,
    path: string,
    passphrase: string | null
  ): Promise<unknown>
}

export type NovaSyncProgress = { phase: string; percent: number }
export type NovaSyncResult = { ok: boolean; message: string }

const SETTINGS_FILE_NAME = 'nova-webdav.json'

function settingsFilePath() {
  return join(getConfigPath(), SETTINGS_FILE_NAME)
}

export async function loadWebdavSettings(): Promise<NovaWebdavSettings> {
  try {
    const raw = await readFile(settingsFilePath(), 'utf-8')
    const parsed = JSON.parse(raw)
    return { ...emptyWebdavSettings(), ...parsed }
  } catch (_error) {
    // no settings stored yet
    return emptyWebdavSettings()
  }
}

export async function saveWebdavSettings(
  settings: NovaWebdavSettings
): Promise<void> {
  await mkdir(getConfigPath(), { recursive: true })
  await writeFile(settingsFilePath(), JSON.stringify(settings, null, 2), {
    encoding: 'utf-8',
    mode: 0o600,
  })
}

export async function testWebdavConnection(
  settings: NovaWebdavSettings
): Promise<NovaSyncResult> {
  return testConnection(settings)
}

function assertConfigured(settings: NovaWebdavSettings) {
  if (!settings.url.trim()) {
    throw new Error('请先填写 WebDAV 服务器地址')
  }
  if (!settings.passphrase) {
    throw new Error('请先设置备份口令（用于加密云端备份）')
  }
}

/** Returns the newest file inside a directory (used for the exported backup). */
async function newestFileInDir(dir: string): Promise<string> {
  const entries = await readdir(dir)
  if (entries.length === 0) {
    throw new Error('没有生成备份文件')
  }
  let newest: { path: string; mtime: number } | null = null
  for (const entry of entries) {
    const path = join(dir, entry)
    const info = await stat(path)
    if (!info.isFile()) {
      continue
    }
    if (newest === null || info.mtimeMs > newest.mtime) {
      newest = { path, mtime: info.mtimeMs }
    }
  }
  if (newest === null) {
    throw new Error('没有生成备份文件')
  }
  return newest.path
}

/** Exports the account, encrypts the backup and uploads it to WebDAV. */
export async function backupAccountToWebdav(
  rpc: NovaBackupRpc,
  accountId: number,
  onProgress: (progress: NovaSyncProgress) => void
): Promise<NovaSyncResult> {
  let exportDir: string | null = null
  try {
    const settings = await loadWebdavSettings()
    assertConfigured(settings)

    exportDir = join(getTempDir(), `nova-export-${Date.now()}`)
    await mkdir(exportDir, { recursive: true })

    onProgress({ phase: '正在导出账号备份…', percent: 20 })
    await rpc.exportBackup(accountId, exportDir, null)
    const backupFile = await newestFileInDir(exportDir)

    onProgress({ phase: '正在加密备份…', percent: 50 })
    const plain = await readFile(backupFile)
    const encrypted = encryptBackupContainer(plain, settings.passphrase)

    onProgress({ phase: '正在上传到 WebDAV…', percent: 75 })
    await uploadBackupFile(settings, encrypted)

    onProgress({ phase: '同步完成', percent: 100 })
    log.info(
      `uploaded encrypted backup (${encrypted.length} bytes) for account ${accountId}`
    )
    return { ok: true, message: '备份已上传到 WebDAV' }
  } catch (error) {
    log.error('webdav backup failed', error)
    return {
      ok: false,
      message: error instanceof Error ? error.message : String(error),
    }
  } finally {
    if (exportDir) {
      await rm(exportDir, { recursive: true, force: true }).catch(() => {})
    }
  }
}

/** Downloads the backup from WebDAV, decrypts it and imports it. */
export async function restoreAccountFromWebdav(
  rpc: NovaBackupRpc,
  accountId: number,
  onProgress: (progress: NovaSyncProgress) => void
): Promise<NovaSyncResult> {
  let workDir: string | null = null
  try {
    const settings = await loadWebdavSettings()
    assertConfigured(settings)

    workDir = join(getTempDir(), `nova-restore-${Date.now()}`)
    await mkdir(workDir, { recursive: true })

    onProgress({ phase: '正在从 WebDAV 下载备份…', percent: 30 })
    const encrypted = await downloadBackupFile(settings)

    onProgress({ phase: '正在解密备份…', percent: 55 })
    const plain = decryptBackupContainer(encrypted, settings.passphrase)
    const backupFile = join(workDir, 'nova-restore.tar')
    await writeFile(backupFile, plain)

    onProgress({ phase: '正在导入备份…', percent: 75 })
    await rpc.importBackup(accountId, backupFile, null)

    onProgress({ phase: '恢复完成', percent: 100 })
    return { ok: true, message: '已从 WebDAV 恢复账号数据' }
  } catch (error) {
    log.error('webdav restore failed', error)
    return {
      ok: false,
      message: error instanceof Error ? error.message : String(error),
    }
  } finally {
    if (workDir) {
      await rm(workDir, { recursive: true, force: true }).catch(() => {})
    }
  }
}
