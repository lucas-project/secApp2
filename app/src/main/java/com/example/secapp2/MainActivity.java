package com.example.secapp2;

import android.os.Bundle;
import android.view.Menu;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONException;
import com.google.android.material.navigation.NavigationView;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.example.secapp2.databinding.ActivityMainBinding;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import androidx.annotation.NonNull;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private AppBarConfiguration mAppBarConfiguration;
    private TextView messageTextView;
    private Handler mainHandler;
    private OkHttpClient client;
    private static final String REGISTER_URL = "http://170.64.170.80:3000/registerApp";
    private static final String WARNING_URL = "http://170.64.170.80:3000/warningPlayed";
    private static final int LOCAL_PORT = 8080; // You can change this port as needed
    private ScheduledExecutorService scheduler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.example.secapp2.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);
        
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        // Use string resource instead of literal
        messageTextView = binding.appBarMain.contentMain.messageTextView;
        messageTextView.setText(R.string.waiting_for_messages);
        
        mainHandler = new Handler(Looper.getMainLooper());

        // Initialize OkHttpClient with configured timeouts
        client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

        // Start the warning server
        startWarningServer();

        // Start the server listener using scheduler
        startServerListener();
    }

    private void startServerListener() {
        // Register the app first
        registerApp();

        // Remove the canBusData scheduler
        scheduler = Executors.newSingleThreadScheduledExecutor();
        
        // Keep only the warning scheduler
        scheduler.scheduleWithFixedDelay(
            this::checkWarnings,
            0,  // initial delay
            2,  // delay between executions
            TimeUnit.SECONDS
        );
    }

    private void updateUI(final String message) {
        mainHandler.post(() -> {
            if (messageTextView != null) {
                messageTextView.setText(message);
            }
        });
    }


    private void registerApp() {
        try {
            // Get the device's IP address
            String localIp = getLocalIpAddress();
            Log.d(TAG, "Registering app with IP: " + localIp + " and port: " + LOCAL_PORT);
            
            JSONObject jsonInput = new JSONObject();
            jsonInput.put("ip", localIp);
            jsonInput.put("port", LOCAL_PORT);

            RequestBody body = RequestBody.create(
                jsonInput.toString(),
                MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                .url(REGISTER_URL)
                .post(body)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Error registering app", e);
                    updateUI(getString(R.string.error_message, e.getMessage()));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    ResponseBody responseBody = response.body();
                    if (responseBody != null) {
                        try {
                            final String responseData = responseBody.string();
                            Log.d(TAG, "Registration response: " + responseData);
                            if (response.isSuccessful()) {
                                updateUI("App registered successfully");
                            } else {
                                Log.e(TAG, "Registration failed with code: " + response.code());
                                updateUI("Registration failed: " + responseData);
                            }
                        } finally {
                            responseBody.close();
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error preparing registration data", e);
        }
    }

    private void checkWarnings() {
        try {
            JSONObject jsonInput = new JSONObject();
            jsonInput.put("hmiTriggered", true);
            
            // Also include the registration information in warning check
            jsonInput.put("ip", getLocalIpAddress());
            jsonInput.put("port", LOCAL_PORT);

            Log.d(TAG, "Sending warning check: " + jsonInput);
            
            RequestBody body = RequestBody.create(
                jsonInput.toString(),
                MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                .url(WARNING_URL)
                .post(body)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Error checking warnings", e);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    ResponseBody responseBody = response.body();
                    if (responseBody != null) {
                        String responseData = null;
                        try {
                            responseData = responseBody.string();
                            Log.d(TAG, "Warning response: " + responseData);
                            if (response.isSuccessful()) {
                                JSONObject jsonResponse = new JSONObject(responseData);
                                boolean success = jsonResponse.optBoolean("success", true);
                                Log.d(TAG, "Success value: " + success);
                                
                                if (!success) {
                                    String message = jsonResponse.optString("message", "Unknown warning");
                                    displayWarning("⚠️ Warning: " + message);
                                } else {
                                    displayWarning("No warning now");
                                }
                            } else {
                                Log.e(TAG, "Warning check failed with code: " + response.code());
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing JSON response: " + responseData, e);
                        } finally {
                            responseBody.close();
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error preparing warning check", e);
        }
    }

    private String getLocalIpAddress() {
        try {
            java.net.NetworkInterface networkInterface = java.net.NetworkInterface.getByName("wlan0");
            java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                java.net.InetAddress addr = addresses.nextElement();
                if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                    return addr.getHostAddress();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IP address", e);
        }
        return "127.0.0.1"; // fallback to localhost if we can't get the IP
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Shutdown the scheduler
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    // Add a new method to start the warning server
    private void startWarningServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(LOCAL_PORT)) {
                while (!Thread.interrupted()) {
                    try {
                        Socket socket = serverSocket.accept();
                        handleWarningRequest(socket);
                    } catch (IOException e) {
                        Log.e(TAG, "Error accepting connection", e);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error starting warning server", e);
            }
        }).start();
    }

    private void handleWarningRequest(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            StringBuilder requestData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                requestData.append(line);
            }

            // Read the body content
            int contentLength = 0;
            for (String header : requestData.toString().split("\n")) {
                if (header.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(header.split(":")[1].trim());
                    break;
                }
            }

            char[] body = new char[contentLength];
            int bytesRead = reader.read(body, 0, contentLength);
            
            if (bytesRead > 0) {
                String warningMessage = new String(body, 0, bytesRead);

                // Update UI with the warning message
                mainHandler.post(() -> updateUI("Received Warning: " + warningMessage));
            }

            // Send response
            PrintWriter writer = new PrintWriter(socket.getOutputStream());
            writer.println("HTTP/1.1 200 OK");
            writer.println("Content-Type: application/json");
            writer.println("Content-Length: 38");
            writer.println();
            writer.println("{\"message\":\"Warning received successfully\"}");
            writer.flush();

            socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error handling warning request", e);
        }
    }

    private void displayWarning(final String warningMessage) {
        mainHandler.post(() -> {
            TextView warningTextView = findViewById(R.id.warningTextView);
            if (warningTextView != null) {
                warningTextView.setText(warningMessage);
            }
        });
    }
}