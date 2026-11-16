package com.amar.snapmapper

import android.app.admin.DeviceAdminReceiver

/**
 * Minimal device-admin receiver, used only so the "Lock screen" action can call
 * DevicePolicyManager.lockNow() without an accessibility service. The user grants it (and can
 * revoke it) in SnapMapper; SnapMapper requests no other admin powers.
 */
class LockAdminReceiver : DeviceAdminReceiver()
