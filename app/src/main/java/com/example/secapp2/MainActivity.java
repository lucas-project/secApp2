package com.example.secapp2;

import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.example.secapp2.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private TextView messageTextView;
    private Handler mainHandler;
    private boolean isListening = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null)
                        .setAnchorView(R.id.fab).show();
            }
        });
        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        // Initialize TextView using binding
        messageTextView = binding.appBarMain.contentMain.messageTextView;
        if (messageTextView == null) {
            Log.e(TAG, "messageTextView not found!");
        } else {
            messageTextView.setText("Waiting for messages...");
        }
        mainHandler = new Handler(Looper.getMainLooper());

        // Start listening to server in a background thread
        startServerListener();
    }

    private void startServerListener() {
        new Thread(() -> {
            while (isListening) {
                try {
                    Log.d(TAG, "Attempting to connect to server...");
                    URL url = new URL("http://170.64.170.80:3000/canBusData");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);

                    // Create sample data (you should replace this with real data)
                    List<Map<String, Object>> canBusDataList = new ArrayList<>();
                    Map<String, Object> sampleData = new HashMap<>();
                    sampleData.put("UTC", System.currentTimeMillis());
                    sampleData.put("UUID", "sample-uuid");
                    sampleData.put("O_SPD", "60");
                    sampleData.put("O_THR", 50L);
                    sampleData.put("A_BRKLT", 1L);
                    sampleData.put("A_RIND", 0L);
                    sampleData.put("A_LIND", 0L);
                    sampleData.put("A_ROLL", 0L);
                    sampleData.put("userId", 1L);
                    sampleData.put("vehicleNumPlate", "ABC123");
                    canBusDataList.add(sampleData);

                    // Prepare JSON data
                    JSONArray jsonArray = new JSONArray();
                    for (Map<String, Object> packet : canBusDataList) {
                        JSONObject jsonObject = new JSONObject(packet);
                        jsonArray.put(jsonObject);
                    }

                    JSONObject jsonInput = new JSONObject();
                    jsonInput.put("canBusData", jsonArray);

                    // Send the data
                    try (OutputStream os = connection.getOutputStream()) {
                        byte[] input = jsonInput.toString().getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Server response code: " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // Read the response
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream())
                        );
                        StringBuilder response = new StringBuilder();
                        String line;
                        
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        final String rawMessage = response.toString();
                        Log.d(TAG, "Received message: " + rawMessage);

                        // Format the message
                        String formattedMessage = formatMessage(rawMessage);

                        mainHandler.post(() -> {
                            if (messageTextView != null) {
                                messageTextView.setText(formattedMessage);
                            } else {
                                Log.e(TAG, "messageTextView is null when trying to update!");
                            }
                        });
                    } else {
                        Log.e(TAG, "Server returned error code: " + responseCode);
                        mainHandler.post(() -> {
                            if (messageTextView != null) {
                                messageTextView.setText("Error: Server returned code " + responseCode);
                            }
                        });
                    }

                    // Wait before next request
                    Thread.sleep(1000);

                } catch (Exception e) {
                    Log.e(TAG, "Error in server connection: ", e);
                    mainHandler.post(() -> {
                        if (messageTextView != null) {
                            messageTextView.setText("Error: " + e.getMessage());
                        }
                    });
                    
                    // Wait before retrying
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }).start();
    }

    private String formatMessage(String rawMessage) {
        try {
            // Parse the JSON message if it's in JSON format
            JSONObject json = new JSONObject(rawMessage);
            // Format it according to your needs
            // This is an example - modify according to your actual message structure
            StringBuilder formatted = new StringBuilder();
            formatted.append("Message received:\n");
            
            // Iterate through JSON keys and format them
            for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                String key = it.next();
                formatted.append(key).append(": ").append(json.get(key)).append("\n");
            }
            
            return formatted.toString();
        } catch (Exception e) {
            // If not JSON or other error, return raw message
            return rawMessage;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isListening = false;
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
}