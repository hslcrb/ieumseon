package life.rhee.ieumseon

import android.app.admin.DeviceAdminReceiver

// A separately managed Android deployment may provision this component as its Device Owner.
// Ordinary app installs are not prompted to become administrators.
class DeviceAdmin : DeviceAdminReceiver()
