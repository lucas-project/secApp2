package com.example.secapp2;

import android.os.Bundle;
import android.view.Menu;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private AppBarConfiguration mAppBarConfiguration;
    private TextView messageTextView;
    private Handler mainHandler;
    private OkHttpClient client;
    private static final String SERVER_URL = "http://170.64.170.80:3000/canBusData";
    private ScheduledExecutorService scheduler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.example.secapp2.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(view -> 
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null)
                    .setAnchorView(R.id.fab)
                    .show()
        );
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

        // Start the server listener using scheduler
        startServerListener();
    }

    private void startServerListener() {
        // Use scheduleWithFixedDelay instead of scheduleAtFixedRate
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(
            this::sendCanBusData,
            0,  // initial delay
            1,  // delay between executions
            TimeUnit.SECONDS
        );
    }

    private void sendCanBusData() {
        try {
            // Create sample data
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

            JSONArray jsonArray = new JSONArray();
            for (Map<String, Object> packet : canBusDataList) {
                JSONObject jsonObject = new JSONObject(packet);
                jsonArray.put(jsonObject);
            }

            JSONObject jsonInput = new JSONObject();
            jsonInput.put("canBusData", jsonArray);

            // Use the new RequestBody.create method
            RequestBody body = RequestBody.create(
                jsonInput.toString(),
                MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build();

            // Execute request asynchronously with @NonNull annotations
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Error sending data", e);
                    updateUI(getString(R.string.error_message, e.getMessage()));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    ResponseBody responseBody = response.body();
                    if (responseBody != null) {
                        try {
                            final String responseData = responseBody.string();
                            Log.d(TAG, "Server response: " + responseData);
                            updateUI(formatMessage(responseData));
                        } finally {
                            responseBody.close();
                        }
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error preparing data", e);
        }
    }

    private void updateUI(final String message) {
        mainHandler.post(() -> {
            if (messageTextView != null) {
                messageTextView.setText(message);
            }
        });
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
}