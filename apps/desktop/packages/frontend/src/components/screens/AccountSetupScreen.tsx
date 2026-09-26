import React from 'react'

import NovaLoginWizard from './NovaWizard'

import type ScreenController from '../../ScreenController'

/**
 * Nova Chat: the login screen hosts the optimized first-run wizard
 * (provider presets, real-time connection test, optional E2EE and
 * optional WebDAV account sync).
 *
 * The actual wizard lives in ./NovaWizard/index.tsx
 */
export default function AccountSetupScreen({
  selectAccount,
  accountId,
}: {
  selectAccount: typeof ScreenController.prototype.selectAccount
  accountId: number
}) {
  return <NovaLoginWizard accountId={accountId} selectAccount={selectAccount} />
}
