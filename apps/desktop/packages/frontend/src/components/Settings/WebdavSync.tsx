import React, { useEffect, useState } from 'react'

import Dialog, {
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogHeader,
  FooterActionButton,
  FooterActions,
} from '../Dialog'
import { DeltaInput, DeltaProgressBar } from '../Login-Styles'
import Switch from '../Switch'
import Button from '../Button'
import useTranslationFunction from '../../hooks/useTranslationFunction'
import useConfirmationDialog from '../../hooks/dialog/useConfirmationDialog'
import { selectedAccountId } from '../../ScreenController'
import { runtime } from '@deltachat-desktop/runtime-interface'
import { unknownErrorToString } from '@deltachat-desktop/shared/unknownErrorToString'
import { getLogger } from '@deltachat-desktop/shared/logger'

import type { DialogProps } from '../../contexts/DialogContext'
import type { NovaWebdavSettings } from '@deltachat-desktop/runtime-interface'

const log = getLogger('renderer/Settings/WebdavSync')

/**
 * Nova Chat: WebDAV account sync settings.
 *
 * The backup is encrypted locally with the user's passphrase
 * before it is uploaded, the WebDAV server never sees plaintext.
 */
export default function WebdavSync({ onClose }: DialogProps) {
  const tx = useTranslationFunction()
  const openConfirmationDialog = useConfirmationDialog()
  const accountId = selectedAccountId()
  const bridge = runtime.novaWebdav

  const [settings, setSettings] = useState<NovaWebdavSettings | null>(null)
  const [status, setStatus] = useState<string | null>(null)
  const [progress, setProgress] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!bridge) {
      return
    }
    bridge
      .getSettings()
      .then(setSettings)
      .catch(error => {
        log.error('failed to load webdav settings', error)
        setStatus(unknownErrorToString(error))
      })
  }, [bridge])

  useEffect(() => {
    if (!bridge) {
      return
    }
    return bridge.onProgress(({ phase, percent }) => {
      setStatus(`${phase} (${percent}%)`)
      setProgress(percent)
    })
  }, [bridge])

  const updateSetting = (
    key: keyof NovaWebdavSettings,
    value: string | boolean
  ) => {
    if (!settings) {
      return
    }
    setSettings({ ...settings, [key]: value })
  }

  const save = async (): Promise<boolean> => {
    if (!bridge || !settings) {
      return false
    }
    try {
      await bridge.saveSettings(settings)
      setStatus(tx('nova_webdav_saved'))
      return true
    } catch (error) {
      setStatus(unknownErrorToString(error))
      return false
    }
  }

  const test = async () => {
    if (!bridge || !settings) {
      return
    }
    setBusy(true)
    setProgress(null)
    setStatus(tx('nova_webdav_testing'))
    try {
      await bridge.saveSettings(settings)
      const result = await bridge.testConnection(settings)
      setStatus(result.message)
    } catch (error) {
      setStatus(unknownErrorToString(error))
    } finally {
      setBusy(false)
    }
  }

  const backupNow = async () => {
    if (!bridge || !settings) {
      return
    }
    setBusy(true)
    setProgress(0)
    try {
      await bridge.saveSettings(settings)
      const result = await bridge.backupNow(accountId)
      setStatus(result.message)
    } catch (error) {
      setStatus(unknownErrorToString(error))
    } finally {
      setBusy(false)
    }
  }

  const restore = async () => {
    if (!bridge || !settings) {
      return
    }
    const confirmed = await openConfirmationDialog({
      message: tx('nova_webdav_restore_warn'),
      confirmLabel: tx('nova_webdav_restore'),
    })
    if (!confirmed) {
      return
    }
    setBusy(true)
    setProgress(0)
    try {
      await bridge.saveSettings(settings)
      const result = await bridge.restoreLast(accountId)
      setStatus(result.message)
    } catch (error) {
      setStatus(unknownErrorToString(error))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog onClose={onClose} fixed width={420}>
      <DialogHeader title={tx('nova_webdav_sync')} onClose={onClose} />
      <DialogBody>
        <DialogContent>
          {!bridge || !settings ? (
            <p className='text'>{tx('nova_webdav_desktop_only')}</p>
          ) : (
            <>
              <p className='text'>{tx('nova_webdav_explain')}</p>
              <DeltaInput
                key='webdav-url'
                id='webdav-url'
                type='text'
                label={tx('nova_webdav_url')}
                placeholder='https://example.com/remote.php/dav/files/user/'
                value={settings.url}
                onChange={ev => updateSetting('url', ev.target.value)}
              />
              <DeltaInput
                key='webdav-username'
                id='webdav-username'
                type='text'
                label={tx('nova_webdav_username')}
                value={settings.username}
                onChange={ev => updateSetting('username', ev.target.value)}
              />
              <DeltaInput
                key='webdav-password'
                id='webdav-password'
                type='password'
                label={tx('nova_webdav_password')}
                value={settings.password}
                onChange={ev => updateSetting('password', ev.target.value)}
              />
              <DeltaInput
                key='webdav-passphrase'
                id='webdav-passphrase'
                type='password'
                label={tx('nova_webdav_passphrase')}
                value={settings.passphrase}
                onChange={ev => updateSetting('passphrase', ev.target.value)}
              />
              <div className='delta-form-group delta-switch'>
                <label>
                  <span>{tx('nova_webdav_auto_sync')}</span>
                  <Switch
                    checked={settings.autoSync}
                    onChange={() =>
                      updateSetting('autoSync', !settings.autoSync)
                    }
                  />
                </label>
              </div>
              <div
                style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: '6px',
                  marginTop: '10px',
                }}
              >
                <Button disabled={busy} onClick={save}>
                  {tx('save')}
                </Button>
                <Button disabled={busy} onClick={test}>
                  {tx('nova_webdav_test')}
                </Button>
                <Button disabled={busy} onClick={backupNow}>
                  {tx('nova_webdav_backup_now')}
                </Button>
                <Button disabled={busy} onClick={restore}>
                  {tx('nova_webdav_restore')}
                </Button>
              </div>
              {progress !== null && (
                <DeltaProgressBar progress={progress} intent='primary' />
              )}
              {status && (
                <p className='text' data-testid='nova-webdav-settings-status'>
                  {status}
                </p>
              )}
            </>
          )}
        </DialogContent>
        <DialogFooter>
          <FooterActions>
            <FooterActionButton onClick={onClose}>
              {tx('close')}
            </FooterActionButton>
          </FooterActions>
        </DialogFooter>
      </DialogBody>
    </Dialog>
  )
}
