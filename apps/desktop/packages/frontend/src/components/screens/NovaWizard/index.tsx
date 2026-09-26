import React, { useCallback, useEffect, useMemo, useState } from 'react'
import type { DcEventType } from '@deltachat/jsonrpc-client'

import Dialog, {
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogHeader,
  FooterActionButton,
  FooterActions,
} from '../../Dialog'
import ImageBackdrop from '../../ImageBackdrop'
import { DeltaInput, DeltaProgressBar, DeltaSelect } from '../../Login-Styles'
import Button from '../../Button'
import Switch from '../../Switch'
import useTranslationFunction from '../../../hooks/useTranslationFunction'
import { BackendRemote } from '../../../backend-com'
import { getDeviceChatId, saveLastChatId } from '../../../backend/chat'
import {
  defaultCredentials,
  Credentials,
} from '../../Settings/DefaultCredentials'
import { runtime } from '@deltachat-desktop/runtime-interface'
import { unknownErrorToString } from '@deltachat-desktop/shared/unknownErrorToString'
import { getLogger } from '@deltachat-desktop/shared/logger'
import {
  NOVA_PROVIDER_PRESETS,
  NovaProviderPreset,
  applyPresetToCredentials,
  detectPresetForAddress,
  getPresetById,
} from './providerPresets'

import type ScreenController from '../../../ScreenController'
import type { NovaWebdavSettings } from '@deltachat-desktop/runtime-interface'
import type { TranslationKey } from '@deltachat-desktop/shared/translationKeyType'

const log = getLogger('renderer/novaLoginWizard')

type Props = {
  accountId: number
  selectAccount: typeof ScreenController.prototype.selectAccount
}

type WizardStep = 'account' | 'security' | 'connect' | 'sync'

const STEP_TITLE_KEYS = {
  account: 'nova_step_account',
  security: 'nova_step_security',
  connect: 'nova_step_connect',
  sync: 'nova_step_sync',
} as const satisfies Record<WizardStep, TranslationKey>

const Socket = {
  automatic: 'automatic',
  ssl: 'ssl',
  starttls: 'starttls',
  plain: 'plain',
} as const

const CertificateChecks = {
  automatic: 'automatic',
  strict: 'strict',
  acceptInvalidCertificates: 'acceptInvalidCertificates',
} as const

/**
 * Nova Chat: optimized first-run login wizard.
 *
 *  account  -> e-mail address + provider presets (server settings auto-filled)
 *  security -> password, optional advanced server settings, optional E2EE
 *  connect  -> real-time connection test (IMAP/SMTP configuration progress)
 *  sync     -> optional WebDAV account sync, then finish
 */
export default function NovaLoginWizard({ accountId, selectAccount }: Props) {
  const tx = useTranslationFunction()

  const [step, setStep] = useState<WizardStep>('account')
  const [credentials, setCredentials] =
    useState<Credentials>(defaultCredentials())
  const [selectedPresetId, setSelectedPresetId] = useState<string | null>(null)
  const [showAdvanced, setShowAdvanced] = useState(false)
  const [forceEncryption, setForceEncryption] = useState(false)

  const [progress, setProgress] = useState(0)
  const [progressComment, setProgressComment] = useState('')
  const [connectError, setConnectError] = useState<string | null>(null)

  const [webdavEnabled, setWebdavEnabled] = useState(false)
  const [webdavSettings, setWebdavSettings] = useState<NovaWebdavSettings>({
    url: '',
    username: '',
    password: '',
    passphrase: '',
    autoSync: false,
  })
  const [webdavStatus, setWebdavStatus] = useState<string | null>(null)
  const [webdavBusy, setWebdavBusy] = useState(false)

  const webdavAvailable = runtime.novaWebdav !== undefined

  const detectedPreset = useMemo(
    () => detectPresetForAddress(credentials.addr),
    [credentials.addr]
  )
  const activePreset: NovaProviderPreset | undefined =
    (selectedPresetId ? getPresetById(selectedPresetId) : undefined) ??
    detectedPreset

  const applyPreset = useCallback((preset: NovaProviderPreset) => {
    setSelectedPresetId(preset.id)
    setCredentials(credentials => applyPresetToCredentials(credentials, preset))
  }, [])

  const handleEmailChange = (
    event: React.FormEvent<HTMLElement> & React.ChangeEvent<HTMLInputElement>
  ) => {
    const addr = event.target.value
    setCredentials(credentials => {
      const next = { ...credentials, addr }
      const preset = detectPresetForAddress(addr)
      return preset ? applyPresetToCredentials(next, preset) : next
    })
    setSelectedPresetId(null)
  }

  const handleCredentialsChange = (
    event: React.FormEvent<HTMLElement> & React.ChangeEvent<HTMLInputElement>
  ) => {
    const { id, value } = event.target
    if (!Object.keys(credentials).includes(id)) {
      log.error('unknown credentials key', id)
      return
    }
    let typeSafeValue: string | number | null = value === '' ? null : value
    if ((id === 'smtpPort' || id === 'imapPort') && typeSafeValue !== null) {
      typeSafeValue = Number(value)
    }
    if ((id === 'addr' || id === 'password') && typeSafeValue === null) {
      typeSafeValue = ''
    }
    setCredentials({ ...credentials, [id]: typeSafeValue })
  }

  const isEmailValid = credentials.addr.includes('@')

  /** 2. step: configure the account, showing the real-time progress. */
  const startConnect = useCallback(async () => {
    setStep('connect')
    setProgress(0)
    setProgressComment('')
    setConnectError(null)

    const emitter = BackendRemote.getContextEvents(accountId)
    const onConfigureProgress = ({
      progress: progressValue,
      comment,
    }: DcEventType<'ConfigureProgress'>) => {
      if (progressValue) {
        setProgress(progressValue)
      }
      setProgressComment(comment || '')
    }
    emitter.on('ConfigureProgress', onConfigureProgress)

    try {
      const transports = await BackendRemote.rpc.listTransports(accountId)
      const isInitialOnboarding =
        transports.length === 1 && transports[0]?.addr === ''

      // configures the account and emits ConfigureProgress events
      await BackendRemote.rpc.addOrUpdateTransport(accountId, credentials)

      // Nova Chat: end-to-end encryption is optional, default off
      await BackendRemote.rpc.setConfig(
        accountId,
        'force_encryption',
        forceEncryption ? '1' : '0'
      )

      if (isInitialOnboarding) {
        try {
          const deviceChatId = await getDeviceChatId(accountId)
          if (deviceChatId) {
            await saveLastChatId(accountId, deviceChatId)
          }
        } catch (error) {
          log.warn('could not select device chat', error)
        }
      }
      setStep('sync')
    } catch (error) {
      log.error('configure error', error)
      setConnectError(unknownErrorToString(error))
    } finally {
      emitter.off('ConfigureProgress', onConfigureProgress)
    }
  }, [accountId, credentials, forceEncryption])

  const cancelConnect = useCallback(async () => {
    try {
      await BackendRemote.rpc.stopOngoingProcess(accountId)
    } catch (error) {
      log.warn('failed to stop ongoing process', error)
    }
    setStep('security')
  }, [accountId])

  const finish = useCallback(async () => {
    if (webdavEnabled && runtime.novaWebdav) {
      setWebdavBusy(true)
      setWebdavStatus(null)
      try {
        await runtime.novaWebdav.saveSettings({
          ...webdavSettings,
          autoSync: true,
        })
        const result = await runtime.novaWebdav.backupNow(accountId)
        if (!result.ok) {
          setWebdavStatus(result.message)
          setWebdavBusy(false)
          return
        }
      } catch (error) {
        setWebdavStatus(unknownErrorToString(error))
        setWebdavBusy(false)
        return
      }
      setWebdavBusy(false)
    }
    selectAccount(accountId)
  }, [accountId, selectAccount, webdavEnabled, webdavSettings])

  const testWebdav = useCallback(async () => {
    if (!runtime.novaWebdav) {
      return
    }
    setWebdavBusy(true)
    setWebdavStatus(tx('nova_webdav_testing'))
    try {
      const result = await runtime.novaWebdav.testConnection(webdavSettings)
      setWebdavStatus(result.message)
    } catch (error) {
      setWebdavStatus(unknownErrorToString(error))
    } finally {
      setWebdavBusy(false)
    }
  }, [tx, webdavSettings])

  // subscribe to sync progress while the wizard is open
  useEffect(() => {
    if (!runtime.novaWebdav) {
      return
    }
    const unsubscribe = runtime.novaWebdav.onProgress(({ phase, percent }) => {
      setWebdavStatus(`${phase} (${percent}%)`)
    })
    return unsubscribe
  }, [])

  const onCancel = () => selectAccount(accountId)

  return (
    <ImageBackdrop variant='welcome'>
      <Dialog
        fixed
        width={420}
        canOutsideClickClose={false}
        onClose={() => {}}
        dataTestid='nova-login-wizard'
      >
        <DialogHeader
          title={tx('nova_login_title')}
          onClose={onCancel}
          dataTestid='nova-wizard-header'
        />
        <DialogBody>
          <DialogContent>
            <div
              style={{ marginBottom: '12px', opacity: 0.75, fontSize: '13px' }}
            >
              {tx(STEP_TITLE_KEYS[step])}
            </div>

            {step === 'account' && (
              <AccountStep
                credentials={credentials}
                activePreset={activePreset}
                onEmailChange={handleEmailChange}
                onPickPreset={applyPreset}
              />
            )}

            {step === 'security' && (
              <SecurityStep
                credentials={credentials}
                onCredentialsChange={handleCredentialsChange}
                showAdvanced={showAdvanced}
                setShowAdvanced={setShowAdvanced}
                forceEncryption={forceEncryption}
                setForceEncryption={setForceEncryption}
              />
            )}

            {step === 'connect' && (
              <ConnectStep
                progress={progress}
                comment={progressComment}
                error={connectError}
              />
            )}

            {step === 'sync' && (
              <SyncStep
                forceEncryption={forceEncryption}
                webdavAvailable={webdavAvailable}
                webdavEnabled={webdavEnabled}
                setWebdavEnabled={setWebdavEnabled}
                webdavSettings={webdavSettings}
                setWebdavSettings={setWebdavSettings}
                webdavStatus={webdavStatus}
                webdavBusy={webdavBusy}
                onTestWebdav={testWebdav}
              />
            )}
          </DialogContent>
        </DialogBody>
        <DialogFooter>
          <FooterActions>
            {step === 'account' && (
              <>
                <FooterActionButton onClick={onCancel}>
                  {tx('cancel')}
                </FooterActionButton>
                <FooterActionButton
                  disabled={!isEmailValid}
                  onClick={() => setStep('security')}
                  data-testid='nova-wizard-next'
                >
                  {tx('next')}
                </FooterActionButton>
              </>
            )}
            {step === 'security' && (
              <>
                <FooterActionButton onClick={() => setStep('account')}>
                  {tx('back')}
                </FooterActionButton>
                <FooterActionButton
                  onClick={startConnect}
                  data-testid='login-with-credentials'
                >
                  {tx('nova_connect_now')}
                </FooterActionButton>
              </>
            )}
            {step === 'connect' && (
              <>
                <FooterActionButton onClick={cancelConnect}>
                  {tx('cancel')}
                </FooterActionButton>
                {connectError !== null && (
                  <FooterActionButton onClick={() => setStep('security')}>
                    {tx('nova_edit_again')}
                  </FooterActionButton>
                )}
              </>
            )}
            {step === 'sync' && (
              <>
                <FooterActionButton
                  disabled={webdavBusy}
                  onClick={() => selectAccount(accountId)}
                >
                  {tx('nova_skip')}
                </FooterActionButton>
                <FooterActionButton
                  disabled={webdavBusy}
                  onClick={finish}
                  data-testid='nova-wizard-finish'
                >
                  {tx('nova_finish')}
                </FooterActionButton>
              </>
            )}
          </FooterActions>
        </DialogFooter>
      </Dialog>
    </ImageBackdrop>
  )
}

function AccountStep({
  credentials,
  activePreset,
  onEmailChange,
  onPickPreset,
}: {
  credentials: Credentials
  activePreset: NovaProviderPreset | undefined
  onEmailChange: (
    event: React.FormEvent<HTMLElement> & React.ChangeEvent<HTMLInputElement>
  ) => void
  onPickPreset: (preset: NovaProviderPreset) => void
}) {
  const tx = useTranslationFunction()
  return (
    <>
      <p className='delta-headline'>{tx('nova_account_step_title')}</p>
      <DeltaInput
        key='addr'
        id='addr'
        type='email'
        autoFocus
        placeholder='name@example.com'
        label={tx('email_address')}
        value={credentials.addr}
        onChange={onEmailChange}
        dataTestId='nova-wizard-email'
      />
      <p className='text'>{tx('nova_provider_pick')}</p>
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: '6px',
          marginBottom: '12px',
        }}
      >
        {NOVA_PROVIDER_PRESETS.map(preset => (
          <Button
            key={preset.id}
            styling={activePreset?.id === preset.id ? 'primary' : undefined}
            onClick={() => onPickPreset(preset)}
            data-testid={`nova-preset-${preset.id}`}
          >
            {preset.label}
          </Button>
        ))}
      </div>
      {activePreset && activePreset.id !== 'custom' && (
        <p className='text' data-testid='nova-provider-detected'>
          {tx('nova_provider_detected', activePreset.label)}
        </p>
      )}
      {activePreset?.noteKey && (
        <p className='text'>{tx(activePreset.noteKey)}</p>
      )}
      <p className='text'>{tx('nova_account_step_hint')}</p>
    </>
  )
}

function SecurityStep({
  credentials,
  onCredentialsChange,
  showAdvanced,
  setShowAdvanced,
  forceEncryption,
  setForceEncryption,
}: {
  credentials: Credentials
  onCredentialsChange: (
    event: React.FormEvent<HTMLElement> & React.ChangeEvent<HTMLInputElement>
  ) => void
  showAdvanced: boolean
  setShowAdvanced: (show: boolean) => void
  forceEncryption: boolean
  setForceEncryption: (value: boolean) => void
}) {
  const tx = useTranslationFunction()
  const {
    password,
    imapUser,
    imapServer,
    imapPort,
    imapSecurity,
    certificateChecks,
    smtpUser,
    smtpPassword,
    smtpServer,
    smtpPort,
    smtpSecurity,
  } = credentials

  return (
    <>
      <p className='delta-headline'>{tx('nova_security_step_title')}</p>
      <DeltaInput
        key='password'
        id='password'
        type='password'
        autoFocus
        label={tx('existing_password')}
        placeholder={tx('nova_password_hint')}
        value={password || ''}
        onChange={onCredentialsChange}
        dataTestId='nova-wizard-password'
      />

      <div className='delta-form-group delta-switch'>
        <label>
          <span>{tx('nova_e2ee_switch')}</span>
          <Switch
            checked={forceEncryption}
            onChange={() => setForceEncryption(!forceEncryption)}
          />
        </label>
      </div>
      <p className='text' data-testid='nova-e2ee-hint'>
        {tx('nova_e2ee_switch_hint')}
      </p>

      <button
        type='button'
        className='advanced'
        aria-controls='nova-advanced-collapse'
        onClick={() => setShowAdvanced(!showAdvanced)}
        id='nova-show-advanced-button'
      >
        <div className={`advanced-icon ${showAdvanced && 'opened'}`} />
        <p>{tx('nova_advanced_servers')}</p>
      </button>

      {showAdvanced && (
        <div id='nova-advanced-collapse'>
          <br />
          <p className='delta-headline'>{tx('login_inbox')}</p>
          <DeltaInput
            key='imapUser'
            id='imapUser'
            label={tx('login_imap_login')}
            placeholder={tx('automatic')}
            value={imapUser}
            onChange={onCredentialsChange}
          />
          <DeltaInput
            key='imapServer'
            id='imapServer'
            label={tx('login_imap_server')}
            placeholder={tx('automatic')}
            value={imapServer}
            onChange={onCredentialsChange}
          />
          <DeltaInput
            key='imapPort'
            id='imapPort'
            type='number'
            min='0'
            max='65535'
            label={tx('login_imap_port')}
            placeholder={tx('def')}
            value={imapPort}
            onChange={onCredentialsChange}
          />
          <DeltaSelect
            id='imapSecurity'
            label={tx('login_imap_security')}
            value={imapSecurity}
            onChange={
              onCredentialsChange as unknown as (
                ev: React.ChangeEvent<HTMLSelectElement>
              ) => void
            }
          >
            <option value={Socket.automatic}>{tx('automatic')}</option>
            <option value={Socket.ssl}>SSL/TLS</option>
            <option value={Socket.starttls}>STARTTLS</option>
            <option value={Socket.plain}>{tx('off')}</option>
          </DeltaSelect>

          <p className='delta-headline'>{tx('login_outbox')}</p>
          <DeltaInput
            key='smtpUser'
            id='smtpUser'
            label={tx('login_smtp_login')}
            placeholder={tx('automatic')}
            value={smtpUser}
            onChange={onCredentialsChange}
          />
          <DeltaInput
            key='smtpPassword'
            id='smtpPassword'
            type='password'
            label={tx('login_smtp_password')}
            placeholder={tx('automatic')}
            value={smtpPassword || ''}
            onChange={onCredentialsChange}
          />
          <DeltaInput
            key='smtpServer'
            id='smtpServer'
            label={tx('login_smtp_server')}
            placeholder={tx('automatic')}
            value={smtpServer}
            onChange={onCredentialsChange}
          />
          <DeltaInput
            key='smtpPort'
            id='smtpPort'
            type='number'
            min='0'
            max='65535'
            label={tx('login_smtp_port')}
            placeholder={tx('def')}
            value={smtpPort}
            onChange={onCredentialsChange}
          />
          <DeltaSelect
            id='smtpSecurity'
            label={tx('login_smtp_security')}
            value={smtpSecurity}
            onChange={
              onCredentialsChange as unknown as (
                ev: React.ChangeEvent<HTMLSelectElement>
              ) => void
            }
          >
            <option value={Socket.automatic}>{tx('automatic')}</option>
            <option value={Socket.ssl}>SSL/TLS</option>
            <option value={Socket.starttls}>STARTTLS</option>
            <option value={Socket.plain}>{tx('off')}</option>
          </DeltaSelect>
          <DeltaSelect
            id='certificateChecks'
            label={tx('login_certificate_checks')}
            value={certificateChecks}
            onChange={
              onCredentialsChange as unknown as (
                ev: React.ChangeEvent<HTMLSelectElement>
              ) => void
            }
          >
            <option value={CertificateChecks.automatic}>
              {tx('automatic')}
            </option>
            <option value={CertificateChecks.strict}>{tx('strict')}</option>
            <option value={CertificateChecks.acceptInvalidCertificates}>
              {tx('accept_invalid_certificates')}
            </option>
          </DeltaSelect>
        </div>
      )}
    </>
  )
}

function ConnectStep({
  progress,
  comment,
  error,
}: {
  progress: number
  comment: string
  error: string | null
}) {
  const tx = useTranslationFunction()
  return (
    <>
      <p className='delta-headline'>
        {error === null ? tx('nova_connecting') : tx('nova_connect_error')}
      </p>
      <DeltaProgressBar
        progress={progress}
        intent={error === null ? 'primary' : 'danger'}
      />
      <p className='text'>{comment}</p>
      {error !== null && (
        <p className='text' data-testid='nova-connect-error'>
          {error}
        </p>
      )}
    </>
  )
}

function SyncStep({
  forceEncryption,
  webdavAvailable,
  webdavEnabled,
  setWebdavEnabled,
  webdavSettings,
  setWebdavSettings,
  webdavStatus,
  webdavBusy,
  onTestWebdav,
}: {
  forceEncryption: boolean
  webdavAvailable: boolean
  webdavEnabled: boolean
  setWebdavEnabled: (value: boolean) => void
  webdavSettings: NovaWebdavSettings
  setWebdavSettings: (settings: NovaWebdavSettings) => void
  webdavStatus: string | null
  webdavBusy: boolean
  onTestWebdav: () => void
}) {
  const tx = useTranslationFunction()
  const setField = (key: keyof NovaWebdavSettings, value: string) =>
    setWebdavSettings({ ...webdavSettings, [key]: value })

  return (
    <>
      <p className='delta-headline' data-testid='nova-connected'>
        {tx('nova_connected_title')}
      </p>
      <p className='text'>
        {tx('nova_connected_hint')} ·{' '}
        {forceEncryption ? tx('nova_e2ee_on') : tx('nova_e2ee_off')}
      </p>

      <div className='delta-form-group delta-switch'>
        <label>
          <span>{tx('nova_webdav_enable')}</span>
          <Switch
            checked={webdavEnabled}
            disabled={!webdavAvailable}
            onChange={() => setWebdavEnabled(!webdavEnabled)}
          />
        </label>
      </div>

      {!webdavAvailable ? (
        <p className='text'>{tx('nova_webdav_desktop_only')}</p>
      ) : (
        <>
          <p className='text'>{tx('nova_webdav_enable_hint')}</p>
          {webdavEnabled && (
            <div data-testid='nova-webdav-fields'>
              <DeltaInput
                key='nova-webdav-url'
                id='nova-webdav-url'
                type='text'
                label={tx('nova_webdav_url')}
                placeholder='https://example.com/remote.php/dav/files/user/'
                value={webdavSettings.url}
                onChange={ev => setField('url', ev.target.value)}
              />
              <DeltaInput
                key='nova-webdav-username'
                id='nova-webdav-username'
                type='text'
                label={tx('nova_webdav_username')}
                value={webdavSettings.username}
                onChange={ev => setField('username', ev.target.value)}
              />
              <DeltaInput
                key='nova-webdav-password'
                id='nova-webdav-password'
                type='password'
                label={tx('nova_webdav_password')}
                value={webdavSettings.password}
                onChange={ev => setField('password', ev.target.value)}
              />
              <DeltaInput
                key='nova-webdav-passphrase'
                id='nova-webdav-passphrase'
                type='password'
                label={tx('nova_webdav_passphrase')}
                value={webdavSettings.passphrase}
                onChange={ev => setField('passphrase', ev.target.value)}
              />
              <Button disabled={webdavBusy} onClick={onTestWebdav}>
                {tx('nova_webdav_test')}
              </Button>
            </div>
          )}
          {webdavStatus && (
            <p className='text' data-testid='nova-webdav-status'>
              {webdavStatus}
            </p>
          )}
        </>
      )}
    </>
  )
}
