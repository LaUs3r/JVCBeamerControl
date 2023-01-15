package com.remotecontrol.jvcbeamercontrol;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
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
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import com.remotecontrol.jvcbeamercontrol.BinAscii;

public class MainActivity extends AppCompatActivity {

    private Socket socket;
    private static final int SERVERPORT = 20554;
    private static final String SERVER_IP = "192.168.100.5";
    private InputStream beamerInputStream;
    private OutputStream beamerOutputStream;
    private boolean bIsConnectable;
    private static final byte[] PJREQ = new byte[]  { 80, 74, 82, 69, 81 };
    private static final byte[] STANDBY = { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x0a };
    final protected static String SUCCESSFULL_CONNECTION_REPLY = "06890100000A";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        //new Thread(new ClientThread()).start();
    }

    public boolean checkConnection() {
        // Try to connect to the beamer
        try {
            Socket socket = new Socket(SERVER_IP, SERVERPORT);
            beamerInputStream = socket.getInputStream();

            byte[] inputBuffer = new byte[5];
            try {
                beamerInputStream.read(inputBuffer);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Wait for the PJ_OK packet (in decimal 80 74 95 79 75)
            String response = new String(inputBuffer);
            System.out.println("checkConnection: " + response);
            if (response.equals("PJ_OK")) {
                System.out.println("checkConnection: PJ_OK received");
                //Send response PJREQ (in decimal 80 74 82 69 81)
                beamerOutputStream = socket.getOutputStream();
                beamerOutputStream.write(PJREQ);

                // Wait for PJACK packet
                beamerInputStream.read(inputBuffer);
                response = new String(inputBuffer);
                if (response.equals("PJACK")) {
                    System.out.println("PJACK received");
                    beamerOutputStream.write(STANDBY);

                    /*
                     * Check connection in standby.
                     * For the expected reply the receive buffer needs to be increased to 6
                     */
                    inputBuffer = new byte[6];
                    beamerInputStream.read(inputBuffer);
                    if (BinAscii.hexlify(inputBuffer).equals(SUCCESSFULL_CONNECTION_REPLY)) {
                        bIsConnectable = true;
                    } else {
                        bIsConnectable = false;
                    }
                } else {
                    bIsConnectable = false;
                }
            } else {
                // PJ_OK not received
                System.out.println("checkConnection: ERROR! PJ_OK not received!");
                bIsConnectable = false;
            }
        } catch (UnknownHostException e) {
            bIsConnectable = false;
            e.printStackTrace();
        } catch (IOException e) {
            bIsConnectable = false;
            e.printStackTrace();
        } catch (Exception e) {
            bIsConnectable = false;
            e.printStackTrace();
        }
        return bIsConnectable;
    }

    public void power_off(View view) throws IOException {
        checkConnection();
        Button button = (Button)findViewById(R.id.power_off);
        System.out.println("power_off");

        try {
            Socket socket = new Socket(SERVER_IP, SERVERPORT);
            beamerInputStream = socket.getInputStream();

            byte[] buffer = new byte[5];
            try {
                beamerInputStream.read(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // PJ_OK is in decimal 80 74 95 79 75
            String response = new String(buffer);
            System.out.println("buffer2: " + response);
            if (response.equals("PJ_OK")) {
                System.out.println("PJ_OK received");
            }

            //Send response
            // PJREQ is in decimal 80 74 82 69 81
            beamerOutputStream = socket.getOutputStream();
            byte[] reply = new byte[5];
            reply[0] = 80;
            reply[1] = 74;
            reply[2] = 82;
            reply[3] = 69;
            reply[4] = 81;
            beamerOutputStream.write(reply);


            try {
                beamerInputStream.read(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // PJ_OK is in decimal 80 74 95 79 75
            response = new String(buffer);
            System.out.println("buffer3: " + response);

            if (response.equals("PJACK")) {
                System.out.println("PJACK received");
            }

            //switch on beamer
            byte [] reply2 =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x57, (byte)0x31, (byte)0x0a };

            //sock.send(b'\x21\x89\x01\x50\x57\x30\x0a')
            beamerOutputStream.write(reply2);

            socket.close();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void power_on(View view) throws IOException {
        Button button = (Button)findViewById(R.id.power_on);
        System.out.println("power_on");

        try {
            Socket socket = new Socket(SERVER_IP, SERVERPORT);
            beamerInputStream = socket.getInputStream();

            byte[] buffer = new byte[5];
            try {
                beamerInputStream.read(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // PJ_OK is in decimal 80 74 95 79 75
            String response = new String(buffer);
            System.out.println("buffer2: " + response);
            if (response.equals("PJ_OK")) {
                System.out.println("PJ_OK received");
            }

            //Send response
            // PJREQ is in decimal 80 74 82 69 81
            beamerOutputStream = socket.getOutputStream();
            byte[] reply = new byte[5];
            reply[0] = 80;
            reply[1] = 74;
            reply[2] = 82;
            reply[3] = 69;
            reply[4] = 81;
            beamerOutputStream.write(reply);


            try {
                beamerInputStream.read(buffer);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // PJ_OK is in decimal 80 74 95 79 75
            response = new String(buffer);
            System.out.println("buffer3: " + response);

            if (response.equals("PJACK")) {
                System.out.println("PJACK received");
            }

            //switch on beamer
            byte [] reply2 =  { (byte)0x21, (byte)0x89, (byte)0x01, (byte)0x50, (byte)0x57, (byte)0x31, (byte)0x0a };

            //sock.send(b'\x21\x89\x01\x50\x57\x30\x0a')
            beamerOutputStream.write(reply2);

            socket.close();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}