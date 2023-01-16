package com.remotecontrol.jvcbeamercontrol;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.Calendar;

import com.remotecontrol.jvcbeamercontrol.BinAscii;

public class MainActivity extends AppCompatActivity {

    private Socket socket;
    private SocketAddress socketAddress;
    private static final int SERVER_PORT = 20554;
    private static final String SERVER_IP = "192.168.100.5";
    private static final int SERVER_TIMEOUT = 5000;
    private InputStream beamerInputStream;
    private OutputStream beamerOutputStream;
    private boolean bIsConnectable;
    private static final byte[] PJREQ = new byte[]  { 80, 74, 82, 69, 81 };
    private static final byte[] CONNECTION_CHECK = { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x0a };
    private static final byte[] POWER_OFF =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x57, (byte)0x30, (byte)0x0a };
    private static final byte[] POWER_ON =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x57, (byte)0x31, (byte)0x0a };
    final protected static String SUCCESSFULL_CONNECTION_REPLY = "06890100000A";
    private static final int CONNECTION_CHECK_INTERVAL = 30000;
    Handler handler = new Handler();
    Runnable runnable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    @Override
    protected void onResume() {
        // Create a thread in to check the connection every 'iConnectionCheckInterval' seconds
        //Runnable task = () -> handler.postDelayed(Thread.currentThread(), CONNECTION_CHECK_INTERVAL);
        handler.postDelayed(runnable = new Runnable() {
            @Override
            public void run() {
                handler.postDelayed(runnable,CONNECTION_CHECK_INTERVAL);
                checkConnection();
            }
        }, CONNECTION_CHECK_INTERVAL);

        //handler.postDelayed(Thread.currentThread(), CONNECTION_CHECK_INTERVAL);

        //new Thread(task).start();
        super.onResume();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(runnable);
        super.onPause();
    }

    @Override
    protected void onStart()
    {
        checkConnection();
        super.onStart();
    }

    /**
     * Performs a 3-way TCP handshake to the beamer
     * step 1: PJ_OK
     * step 2: PJREQ
     * step 3: PJACK
     */
    private Socket threeWayHandshake() {
        // Try to connect to the beamer
        try {
            //System.out.println("checkConnection: " + new java.util.Date());
            Button button = (Button)findViewById(R.id.connectionStatusIcon);

            socket = new Socket();
            socketAddress = new InetSocketAddress(SERVER_IP, SERVER_PORT);
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
                    System.out.println("checkConnection: PJACK received");
                } else {
                    bIsConnectable = false;
                    button.setBackgroundColor(Color.RED);
                }
            } else {
                // PJ_OK not received
                System.out.println("checkConnection: ERROR! PJ_OK not received!");
                bIsConnectable = false;
                button.setBackgroundColor(Color.RED);
            }

        } catch (ConnectException e) {
            // host and port combination not valid
            e.printStackTrace();
        } catch (SocketTimeoutException e) {
            bIsConnectable = false;
            e.printStackTrace();
        } catch (UnknownHostException e) {
            bIsConnectable = false;
            e.printStackTrace();
        } catch (IOException e) {
            bIsConnectable = false;
            e.printStackTrace();
        }
        return socket;
    }

    /**
     * Check for a correct connection to the beamer.
     * 1. Perform 3-way TCP handshake
     * 2. Verify connection
     * @return connection status to the beamer
     */
    private boolean checkConnection() {
        // Try to connect to the beamer
        try {
            //System.out.println("checkConnection: " + new java.util.Date());
            Button button = (Button)findViewById(R.id.connectionStatusIcon);

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
                    System.out.println("checkConnection: PJACK received");

                    /*
                     * Check for correct connection to the beamer
                     * For the expected reply the receive buffer needs to be increased to 6
                     * Device --> Beamer: 21 89 01 00 00 0A
                     * Beamer --> Device: 06 89 01 00 00 0A
                     */
                    beamerOutputStream.write(CONNECTION_CHECK);
                    inputBuffer = new byte[6];
                    beamerInputStream.read(inputBuffer);
                    if (BinAscii.hexlify(inputBuffer).equals(SUCCESSFULL_CONNECTION_REPLY)) {
                        bIsConnectable = true;
                        button.setBackgroundColor(Color.GREEN);
                    } else {
                        bIsConnectable = false;
                        button.setBackgroundColor(Color.RED);
                    }
                } else {
                    bIsConnectable = false;
                    button.setBackgroundColor(Color.RED);
                }
            } else {
                // PJ_OK not received
                System.out.println("checkConnection: ERROR! PJ_OK not received!");
                bIsConnectable = false;
                button.setBackgroundColor(Color.RED);
            }
            socket.close();
        } catch (ConnectException e) {
            // host and port combination not valid
            e.printStackTrace();
        } catch (SocketTimeoutException e) {
            bIsConnectable = false;
            e.printStackTrace();
        } catch (UnknownHostException e) {
            bIsConnectable = false;
            e.printStackTrace();
        } catch (IOException e) {
            bIsConnectable = false;
            e.printStackTrace();
        }
        return bIsConnectable;
    }

    /**
     * Power off the beamer
     * @param view
     * @throws IOException
     */
    public void power_off(View view) throws IOException {
        try {
            if (bIsConnectable) {
                socket = new Socket();
                SocketAddress socketAddress = new InetSocketAddress(SERVER_IP, SERVER_PORT);
                socket.connect(socketAddress, SERVER_TIMEOUT);
                beamerInputStream = socket.getInputStream();

                //Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();
                beamerOutputStream.write(PJREQ);

                //Switch off beamer
                beamerOutputStream.write(POWER_OFF);

                socket.close();
            }
        } catch (ConnectException e) {
            // host and port combination not valid
            e.printStackTrace();
        } catch (SocketTimeoutException e) {
            bIsConnectable = false;
            e.printStackTrace();
        } catch (UnknownHostException e) {
            bIsConnectable = false;
            e.printStackTrace();
        } catch (IOException e) {
            bIsConnectable = false;
            e.printStackTrace();
        }
    }

    /**
     * Power on the beamer
     * @param view
     * @throws IOException
     */
    public void power_on(View view) throws IOException {
        try {
            if (bIsConnectable) {
                socket = new Socket();
                SocketAddress socketAddress = new InetSocketAddress(SERVER_IP, SERVER_PORT);
                socket.connect(socketAddress, SERVER_TIMEOUT);
                beamerInputStream = socket.getInputStream();

                //Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();
                beamerOutputStream.write(PJREQ);

                //Switch off beamer
                beamerOutputStream.write(POWER_ON);

                socket.close();
            }
        } catch (ConnectException e) {
            // host and port combination not valid
            e.printStackTrace();
        } catch (SocketTimeoutException e) {
            bIsConnectable = false;
            e.printStackTrace();
        } catch (UnknownHostException e) {
            bIsConnectable = false;
            e.printStackTrace();
        } catch (IOException e) {
            bIsConnectable = false;
            e.printStackTrace();
        }
    }
}
