package dev.dinolite.widget;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** One toggle: connect, read the light's real state, send the opposite, confirm, disconnect. */
final class LightClient extends BluetoothGattCallback {
    interface Done { void onDone(Result result); }
    enum Result { ON, OFF, NOT_FOUND, BLUETOOTH_OFF, NO_PERMISSION }

    private static final String TAG = "Dinolite";
    private static final String MAC = "08:65:F0:F0:4C:87";
    private static final UUID SERVICE = UUID.fromString("0000ffff-0000-1000-8000-00805f9b34fb");
    private static final UUID WRITE = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb");
    private static final UUID NOTIFY = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final byte[] QUERY = {(byte) 0xEA, (byte) 0x81, (byte) 0x8A, (byte) 0x8B, 0x59};
    private static final byte STATE_ON = 0x23;
    private static final byte STATE_OFF = 0x24;
    private static final byte POWER_COMMAND = 0x71;
    private static final int STATE_INDEX = 6;
    private static final int HEADER_SIZE = 8;
    private static final long TIMEOUT_MS = 8000;
    private static final int MAX_ATTEMPTS = 2;

    private final Context context;
    private final Done done;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean finished = new AtomicBoolean();
    private final Runnable timeout = () -> finish(Result.NOT_FOUND);
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private byte target;
    private byte seq;
    private int attempts;

    private LightClient(Context context, Done done) {
        this.context = context.getApplicationContext();
        this.done = done;
    }

    static void toggle(Context context, Done done) {
        new LightClient(context, done).start();
    }

    private void start() {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            finish(Result.BLUETOOTH_OFF);
            return;
        }
        main.postDelayed(timeout, TIMEOUT_MS);
        try {
            connect(adapter.getRemoteDevice(MAC));
        } catch (SecurityException e) {
            finish(Result.NO_PERMISSION);
        }
    }

    private void connect(BluetoothDevice device) {
        attempts++;
        gatt = device.connectGatt(context, false, this, BluetoothDevice.TRANSPORT_LE);
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
        Log.i(TAG, "connection status=" + status + " state=" + newState);
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            g.requestMtu(247); // Status replies are 86 bytes; the default MTU would cut them off.
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED && !finished.get()) {
            BluetoothDevice device = g.getDevice();
            g.close();
            if (attempts < MAX_ATTEMPTS) {
                main.post(() -> connect(device));
            } else {
                finish(Result.NOT_FOUND);
            }
        }
    }

    @Override
    public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
        g.discoverServices();
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt g, int status) {
        BluetoothGattService service = g.getService(SERVICE);
        if (service == null) {
            Log.w(TAG, "light service missing");
            finish(Result.NOT_FOUND);
            return;
        }
        writeChar = service.getCharacteristic(WRITE);
        BluetoothGattCharacteristic notify = service.getCharacteristic(NOTIFY);
        g.setCharacteristicNotification(notify, true);
        g.writeDescriptor(notify.getDescriptor(CCCD), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
        send(g, QUERY);
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
        Byte state = parseState(value);
        if (state == null) return;
        if (target == 0) {
            target = state == STATE_ON ? STATE_OFF : STATE_ON;
            send(g, new byte[] {POWER_COMMAND, target});
        } else if (state == target) {
            finish(target == STATE_ON ? Result.ON : Result.OFF);
        }
    }

    /** Wraps a command in the frame the app uses: 00 seq 80 00 len(2) len+1 0a payload. */
    private void send(BluetoothGatt g, byte[] payload) {
        byte[] frame = new byte[HEADER_SIZE + payload.length];
        frame[1] = ++seq;
        frame[2] = (byte) 0x80;
        frame[5] = (byte) payload.length;
        frame[6] = (byte) (payload.length + 1);
        frame[7] = 0x0A;
        System.arraycopy(payload, 0, frame, HEADER_SIZE, payload.length);
        g.writeCharacteristic(writeChar, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
    }

    /** Replies are a header plus JSON: {"code":0,"payload":"EA81...."}. Returns 0x23, 0x24, or null. */
    private static Byte parseState(byte[] value) {
        if (value.length <= HEADER_SIZE) return null;
        String json = new String(value, HEADER_SIZE, value.length - HEADER_SIZE, StandardCharsets.US_ASCII);
        String key = "\"payload\":\"";
        int start = json.indexOf(key);
        if (start < 0) return null;
        int hexStart = start + key.length();
        int end = hexStart + (STATE_INDEX + 1) * 2;
        if (end > json.length() || !json.startsWith("EA81", hexStart)) return null;
        try {
            int state = Integer.parseInt(json.substring(end - 2, end), 16);
            return state == STATE_ON || state == STATE_OFF ? (byte) state : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void finish(Result result) {
        if (!finished.compareAndSet(false, true)) return;
        main.removeCallbacks(timeout);
        Log.i(TAG, "finished: " + result);
        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (SecurityException ignored) {
                // Permission was revoked mid-toggle; nothing left to release.
            }
        }
        main.post(() -> done.onDone(result));
    }
}
