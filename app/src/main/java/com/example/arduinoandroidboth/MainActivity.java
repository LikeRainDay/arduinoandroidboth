package com.example.arduinoandroidboth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.ToggleButton;

import java.nio.ByteBuffer;


/**
 * int prvValue;
 * <p>
 * void setup() {
 * Serial.begin(9600);
 * pinMode(13, OUTPUT);
 * <p>
 * prvValue = 0;
 * }
 * <p>
 * void loop() {
 * if(Serial.available()){
 * byte cmd = Serial.read();
 * if(cmd == 0x02){
 * digitalWrite(13, LOW);
 * }else if(cmd == 0x01){
 * digitalWrite(13, HIGH);
 * }
 * <p>
 * }
 * <p>
 * int sensorValue = analogRead(A0) >> 4;
 * byte dataToSent;
 * if(prvValue != sensorValue){
 * prvValue = sensorValue;
 * <p>
 * if (prvValue==0x00){
 * dataToSent = (byte)0x01;
 * }else{
 * dataToSent = (byte)prvValue;
 * }
 * <p>
 * Serial.write(dataToSent);
 * delay(100);
 * }
 * <p>
 * }
 */
public class MainActivity extends Activity implements Runnable {

    private static final int CMD_LED_OFF = 2;
    private static final int CMD_LED_ON = 1;

    SeekBar bar;
    ToggleButton buttonLed;

    private UsbManager usbManager;
    private UsbDevice deviceFound;
    private UsbDeviceConnection usbDeviceConnection;
    private UsbInterface usbInterfaceFound = null;
    private UsbEndpoint endpointOut = null;
    private UsbEndpoint endpointIn = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bar = (SeekBar) findViewById(R.id.seekbar);
        buttonLed = (ToggleButton) findViewById(R.id.arduinoled);
        buttonLed.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                if (isChecked) {
                    sendCommand(CMD_LED_ON);
                } else {
                    sendCommand(CMD_LED_OFF);
                }
            }
        });

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
    }

    @Override
    public void onResume() {
        super.onResume();

        Intent intent = getIntent();
        String action = intent.getAction();

        UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            setDevice(device);
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            if (deviceFound != null && deviceFound.equals(device)) {
                setDevice(null);
            }
        }
    }

    private void setDevice(UsbDevice device) {
        usbInterfaceFound = null;
        endpointOut = null;
        endpointIn = null;

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface usbif = device.getInterface(i);
            UsbEndpoint tOut = null;
            UsbEndpoint tIn = null;

            int tEndpointCnt = usbif.getEndpointCount();
            if (tEndpointCnt >= 2) {
                for (int j = 0; j < tEndpointCnt; j++) {
                    if (usbif.getEndpoint(j).getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (usbif.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_OUT) {
                            tOut = usbif.getEndpoint(j);
                        } else if (usbif.getEndpoint(j).getDirection() == UsbConstants.USB_DIR_IN) {
                            tIn = usbif.getEndpoint(j);
                        }
                    }
                }

                if (tOut != null && tIn != null) {
                    // This interface have both USB_DIR_OUT
                    // and USB_DIR_IN of USB_ENDPOINT_XFER_BULK
                    usbInterfaceFound = usbif;
                    endpointOut = tOut;
                    endpointIn = tIn;
                }
            }
        }

        if (usbInterfaceFound == null) {
            return;
        }

        deviceFound = device;

        if (device != null) {
            UsbDeviceConnection connection = usbManager.openDevice(device);
            if (connection != null && connection.claimInterface(usbInterfaceFound, true)) {
                usbDeviceConnection = connection;
                Thread thread = new Thread(this);
                thread.start();

            } else {
                usbDeviceConnection = null;
            }
        }
    }

    private void sendCommand(int control) {
        synchronized (this) {
            if (usbDeviceConnection != null) {
                byte[] message = new byte[1];
                message[0] = (byte) control;
                usbDeviceConnection.bulkTransfer(endpointOut, message, message.length, 0);
            }
        }
    }

    @Override
    public void run() {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        UsbRequest request = new UsbRequest();
        request.initialize(usbDeviceConnection, endpointIn);
        while (true) {
            request.queue(buffer, 1);
            if (usbDeviceConnection.requestWait() == request) {
                byte rxCmd = buffer.get(0);
                if (rxCmd != 0) {
                    bar.setProgress((int) rxCmd);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            } else {
                break;
            }
        }
    }
}