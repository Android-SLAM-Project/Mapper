package com.mapper.imuslam;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BaseActivity extends AppCompatActivity {
    private Fragment currentFragment;
    Settings settings;
    BottomNavigationView navBar;
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Enable edge-to-edge by letting us handle insets manually.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
        settings = Settings.getInstance();
//        settings.applyOrientation(this);
        // Ensure this matches your layout file
//        LogManager.initialize(this);
        // Request necessary permissions at runtime
        requestPermissionsIfNecessary();

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );
        String fragmentToLoad = getIntent().getStringExtra("fragmentToLoad");


        // Set up the AppBar (Toolbar)
        Toolbar appBar = findViewById(R.id.appbar);
        setSupportActionBar(appBar);
        String mapFolderPath = getIntent().getStringExtra("mapFolderPath");

// Handle resume navigation AFTER initializing navBar
         navBar = findViewById(R.id.navBar);
        if ("preprocessSavedMap".equals(fragmentToLoad)) {
            PreprocessMap fragment = new PreprocessMap();
            Bundle args = new Bundle();
            args.putString("mapFolderPath", mapFolderPath);
            fragment.setArguments(args);
            loadFragment(fragment);
            navBar.setSelectedItemId(R.id.import_map); // Sync nav bar
        }

        // Set up the BottomNavigationView (NavBar)
        navBar.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.import_map) {
//                if (currentFragment instanceof SavedMap) {
//                    confirmSwitchToImportMap();
//                } else {
                    loadFragment(new NewMap());
//                }
                return true;
            }
            if (item.getItemId() == R.id.saved_map) {
                if (currentFragment instanceof PreprocessMap || currentFragment instanceof NewMap) {
                    confirmSwitchToSavedMap();
                } else {
                    loadFragment(new SavedMap());
                }
                return true;
            }
            return false;
        });

        // Load the default fragment
        if (savedInstanceState == null) {
            if (fragmentToLoad != null && fragmentToLoad.equals("savedMap")) {
                navBar.setSelectedItemId(R.id.saved_map);
            } else {
                navBar.setSelectedItemId(R.id.import_map);
            }
        }

        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            int bottomInset = insets.getSystemWindowInsetBottom();
            navBar.setPadding(0, 0, 0, bottomInset); // Apply bottom padding
            return insets;
        });
    }
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        LogManager.handleActivityResult(this, requestCode, resultCode, data); // SAF handling
//    }



    /**
     * Checks and requests necessary permissions based on the device's API level.
     */
    private void requestPermissionsIfNecessary() {
        List<String> permissionsToRequest = new ArrayList<>();

        // Camera permission is required for all API levels.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }

        // Activity recognition permission.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION);
            }
        }

        // For Android 13 (API 33) and above, request READ_MEDIA_IMAGES.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // For devices below API 33, request legacy storage permissions.
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    // Handle the permission request response
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "Not all permissions were granted.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadFragment(Fragment fragment) {
        currentFragment = fragment;
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    private void confirmSwitchToSavedMap() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("All progress will be gone. Are you sure you want to continue?")
                .setPositiveButton("Yes", (d, which) -> loadFragment(new SavedMap()))
                .setNegativeButton("No", (d, which) -> {
                    // user cancelled -> restore previous selection
                    navBar.setSelectedItemId(R.id.import_map);
                })
                .setOnCancelListener(d -> {
                    // user cancelled -> restore previous selection
                    navBar.setSelectedItemId(R.id.import_map);
                })
                .create();

        // Make the background rounded + semi-transparent
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                int width = (int) (dm.widthPixels * 0.80); // 80% of screen width
                int height = WindowManager.LayoutParams.WRAP_CONTENT; // or a dp -> px value if you want taller
                window.setLayout(width, height);
                window.setBackgroundDrawableResource(R.drawable.rounded_dialog_bg);
            }
        });

        dialog.show();
    }

    private void confirmSwitchToImportMap() {
        AlertDialog dialog =new AlertDialog.Builder(this)
                .setMessage("Saved Map will be Exited. Are you sure you want to continue?")
                .setPositiveButton("Yes", (d, which) -> loadFragment(new NewMap()))
                .setNegativeButton("No", (d, which) -> {
                    // user cancelled -> restore previous selection
                    navBar.setSelectedItemId(R.id.saved_map);
                })
                .setOnCancelListener(d -> {
                    // user cancelled -> restore previous selection
                    navBar.setSelectedItemId(R.id.saved_map);
                })
                .create();

        // Make the background rounded + semi-transparent
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                int width = (int) (dm.widthPixels * 0.80); // 80% of screen width
                int height = WindowManager.LayoutParams.WRAP_CONTENT; // or a dp -> px value if you want taller
                window.setLayout(width, height);
                window.setBackgroundDrawableResource(R.drawable.rounded_dialog_bg);
            }
        });

        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.appbar_items, menu);

        if (menu.getClass().getSimpleName().equals("MenuBuilder")) {
            try {
                Method m = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                m.setAccessible(true);
                m.invoke(menu, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.import_new_map) {
            startActivity(new Intent(BaseActivity.this, BaseActivity.class));
            return true;
        } else if (item.getItemId() == R.id.settings) {
            showSwitchDialog();
            return true;
        } else if (item.getItemId() == R.id.exit) {
            confirmExit();
            return true;
        } else {
            return false;
        }
    }

    // Updated settings dialog that includes both the trailing toggle and an EditText for the constant.
    private void showSwitchDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.settings, null);
        Switch switchOption = dialogView.findViewById(R.id.switch1);
//        Switch potraitFlagSwitch = dialogView.findViewById(R.id.potrait_flag_switch);

        EditText constantEditText = dialogView.findViewById(R.id.editTextConstant);
        switchOption.setChecked(settings.getTrailingFlag());
//        potraitFlagSwitch.setChecked(settings.getPotraitFlag());
        constantEditText.setText(String.valueOf(settings.getConstant()));
        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {
                    settings.setTrailing_flag(switchOption.isChecked());
//                    settings.setPotraitFlag(potraitFlagSwitch.isChecked());
                    try {
                        float newConstant = Float.parseFloat(constantEditText.getText().toString());
                        settings.setConstant(newConstant);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid constant value", Toast.LENGTH_SHORT).show();
                    }
//                    settings.applyOrientation(this);
//                    recreate();
                    Toast.makeText(this, "Settings updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmExit() {
        if (currentFragment instanceof NewMap) {
            new AlertDialog.Builder(this)
                    .setMessage("All progress will be gone if not saved. Are you sure you want to exit?")
                    .setPositiveButton("Yes", (dialog, which) -> dispose())
                    .setNegativeButton("No", null)
                    .show();
        } else if (currentFragment instanceof SavedMap) {
            new AlertDialog.Builder(this)
                    .setMessage("Are you sure you want to exit?")
                    .setPositiveButton("Yes", (dialog, which) -> dispose())
                    .setNegativeButton("No", null)
                    .show();
        }
    }

    void dispose() {
        getSupportFragmentManager().beginTransaction()
                .remove(Objects.requireNonNull(getSupportFragmentManager().findFragmentById(R.id.container)))
                .commitAllowingStateLoss();
        finishAffinity();
    }
}
