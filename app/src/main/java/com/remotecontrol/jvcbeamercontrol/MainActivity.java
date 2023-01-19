package com.remotecontrol.jvcbeamercontrol;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.DecimalFormat;


public class MainActivity extends AppCompatActivity {

    private Socket socket;
    //private static final int SERVER_PORT = 20554;
    private int SERVER_PORT = 0;
    private String SERVER_IP = "";
    private static final int SERVER_TIMEOUT = 5000;
    private OutputStream beamerOutputStream;
    private InputStream beamerInputStream;
    private Button statusIcon;
    private static final byte[] PJREQ = new byte[]  { 80, 74, 82, 69, 81 };
    private static final byte[] CONNECTION_CHECK = { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x0a };
    private static final byte[] POWER_OFF =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x57, (byte)0x30, (byte)0x0a };
    private static final byte[] POWER_ON =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x57, (byte)0x31, (byte)0x0a };
    final protected static String SUCCESSFUL_CONNECTION_REPLY = "06890100000A";
    private static final int CONNECTION_CHECK_INTERVAL = 30000;
    Handler handler = new Handler();
    Runnable runnable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        setTitle("JVC BeamerControl");
    }

    @Override
    protected void onResume() {
        // Create a thread in to check the connection every 'CONNECTION_CHECK_INTERVAL' seconds
        handler.postDelayed(runnable = () -> {
            handler.postDelayed(runnable,CONNECTION_CHECK_INTERVAL);
            try {
                statusIcon = findViewById(R.id.connectionStatusIcon);
                if (checkConnection()) {
                    statusIcon.setBackgroundColor(Color.GREEN);
                } else {
                    statusIcon.setBackgroundColor(Color.RED);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, CONNECTION_CHECK_INTERVAL);
        super.onResume();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(runnable);
        super.onPause();
        saveIPAddress(this.SERVER_IP);
    }

    @Override
    protected void onStart()
    {
        try {
            statusIcon = findViewById(R.id.connectionStatusIcon);
            readSavedIPAddress();
            readSavedPort();

            if (checkConnection()) {
                statusIcon.setBackgroundColor(Color.GREEN);
            } else {
                statusIcon.setBackgroundColor(Color.RED);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onStart();
        //adSavedIPAddress();
    }


    /**
     * Performs a 3-way TCP handshake to the beamer.
     * step 1: PJ_OK
     * step 2: PJREQ
     * step 3: PJACK
     */
    private boolean threeWayHandshake() throws IOException {
        boolean bool3WayHandshake;

        // Try to connect to the beamer
        try {
            // Re-initiate the socket
            socket = new Socket();
            SocketAddress socketAddress = new InetSocketAddress(SERVER_IP, SERVER_PORT);
            socket.connect(socketAddress, SERVER_TIMEOUT);
            beamerInputStream = socket.getInputStream();

            // Create buffer for the 3-way TCP handshake
            byte[] inputBuffer = new byte[5];
            beamerInputStream.read(inputBuffer);

            // Wait for the PJ_OK packet (in decimal 80 74 95 79 75)
            String response = new String(inputBuffer);

            if (response.equals("PJ_OK")) {
                System.out.println("checkConnection: PJ_OK received");
                //Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();
                beamerOutputStream.write(PJREQ);

                // Wait for PJACK packet
                beamerInputStream.read(inputBuffer);
                response = new String(inputBuffer);
                if (response.equals("PJACK")) {
                    bool3WayHandshake =  true;
                    // Do NOT close the socket!
                } else {
                    socket.close();
                    bool3WayHandshake = false;
                }
            } else {
                // PJ_OK not received
                System.out.println("checkConnection: ERROR! PJ_OK not received!");
                socket.close();
                bool3WayHandshake = false;
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
            bool3WayHandshake = false;
        }
        return bool3WayHandshake;
    }

    /**
     * Check for a correct connection to the beamer.
     * 1. Perform 3-way TCP handshake
     * 2. Verify connection
     * @return connection status to the beamer
     */
    private boolean checkConnection() throws IOException {
        boolean bSuccessfulConnect;

        // Try to connect to the beamer
        try {
            if (this.threeWayHandshake() && socket != null) {
                System.out.println("checkConnection: " + new java.util.Date());
                /*
                 * Check for correct connection to the beamer
                 * For the expected reply the receive buffer needs to be increased to 6
                 * Device --> Beamer: 21 89 01 00 00 0A
                 * Beamer --> Device: 06 89 01 00 00 0A
                 */
                beamerOutputStream.write(CONNECTION_CHECK);
                byte[] inputBuffer = new byte[6];
                beamerInputStream.read(inputBuffer);

                // In case of a successful connection, do not close the socket
                bSuccessfulConnect = BinAscii.hexlify(inputBuffer).equals(SUCCESSFUL_CONNECTION_REPLY);
                // Close the socket to make sure that no open socket remains
                socket.close();
            } else {
                bSuccessfulConnect = false;
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
            return false;
        }
        return bSuccessfulConnect;
    }

    /**
     * Power off the beamer
     * @param view current view
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    public void power_off(View view) throws IOException {
        try {
            if (threeWayHandshake() && socket != null) {
                //Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();

                //Switch off beamer
                beamerOutputStream.write(POWER_OFF);
                socket.close();
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
        }
    }

    /**
     * Power on the beamer
     * @param view current view
     * @throws ConnectException, SocketTimeoutException, UnknownHostException
     */
    public void power_on(View view) throws IOException {
        try {
            if (threeWayHandshake() && socket != null) {
                //Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();

                //Switch off beamer
                beamerOutputStream.write(POWER_ON);
                socket.close();
            }
        } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
            socket.close();
            e.printStackTrace();
        }
    }

    /**
     *
     * @param view current view
     */
    public void showSettingsDialog(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        Context context = this;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        builder.setTitle("Enter IP address and port");

        // Add a TextView here for the "Title" label, as noted in the comments
        final IPAddressText ipAddress = new IPAddressText(context);
        ipAddress.setHint("IP Address");
        layout.addView(ipAddress); // Notice this is an add method

        // Add another TextView here for the "Description" label
        final EditText port = new EditText(context);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setHint("Port Number");
        layout.addView(port); // Another

        if (!SERVER_IP.equals("")) ipAddress.setText(SERVER_IP);
        if (SERVER_PORT != 0) port.setText(new DecimalFormat("#").format(SERVER_PORT));

        builder.setView(layout);
        builder.setPositiveButton("OK", (dialog, which) -> {
            if (ipAddress.getText() != null) { SERVER_IP = ipAddress.getText().toString(); }
            SERVER_PORT = Integer.parseInt(port.getText().toString());
            saveIPAddress(SERVER_IP);
            savePort(SERVER_PORT);
            try {
                checkConnection();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    /**
     * Saves the given IP address from the settings dialog
     * @param IPAddress of the beamer
     */
   private void saveIPAddress(String IPAddress) {
       SharedPreferences sharedPref = getSharedPreferences("JVC BeamerControl", Context.MODE_PRIVATE);
       SharedPreferences.Editor editor = sharedPref.edit();
       editor.putString("SERVER_IP", IPAddress);
       editor.apply();
   }

    /**
     * Saves the given port from the settings dialog
     * @param port of the beamer
     */
    private void savePort(int port) {
        SharedPreferences sharedPref = getSharedPreferences("JVC BeamerControl", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("SERVER_PORT", port);
        editor.apply();
    }

    /**
     * Reads the last given IP address to the global variable to be used in the app
     */
    private void readSavedIPAddress() {
        SharedPreferences sharedPref = getSharedPreferences("JVC BeamerControl", Context.MODE_PRIVATE);
        SERVER_IP = sharedPref.getString("SERVER_IP", SERVER_IP);
    }

    /**
     * Reads the last given IP address to the global variable to be used in the app
     */
    private void readSavedPort() {
        SharedPreferences sharedPref = getSharedPreferences("JVC BeamerControl", Context.MODE_PRIVATE);
        SERVER_PORT = sharedPref.getInt("SERVER_PORT", SERVER_PORT);
    }
}
