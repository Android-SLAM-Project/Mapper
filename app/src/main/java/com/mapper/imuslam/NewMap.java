package com.mapper.imuslam;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;

public class NewMap extends Fragment {

    private TextView DimensionText, HeightText, WidthText, subtitle;
    private EditText MapHeight, MapWidth;
    LinearLayout HomeLayout,DimensionLayout;
    private CardView ImportMapImage,loadPreviousMapButton;
    private Button  ToMapStart;
    private Uri ImageUri;
    private boolean ImageSelected = false;

    public NewMap() { /* Required empty constructor */ }

    public static NewMap newInstance(String p1, String p2) {
        NewMap f = new NewMap();
        Bundle args = new Bundle();
        args.putString("param1", p1);
        args.putString("param2", p2);
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_new_map, container, false);
        HomeLayout=v.findViewById(R.id.home_layout);
        DimensionLayout=v.findViewById(R.id.Dimension_layout);
        // bind views
        DimensionText = v.findViewById(R.id.dimension_text);
        HeightText    = v.findViewById(R.id.height_text);
        WidthText     = v.findViewById(R.id.width_text);
        MapHeight     = v.findViewById(R.id.map_height);
        MapWidth      = v.findViewById(R.id.map_width);
        subtitle      = v.findViewById(R.id.new_map_subtitle);
        ImportMapImage       = v.findViewById(R.id.import_map_card);
        ToMapStart           = v.findViewById(R.id.map_pos_activity_button);
        loadPreviousMapButton= v.findViewById(R.id.load_recent_map_card);

        // hide inputs until image picked
//        setFieldsVisibility(GONE);
        DimensionLayout.setVisibility(GONE);
        setupBackPressed();

        // --- RESUME FLOW ---
        String lastMapPath = SessionManager.getLastMapPath(getContext());
        if (lastMapPath != null) {
            File metricsFile = new File(lastMapPath, "metrics.json");
            if (metricsFile.exists()) {
                loadPreviousMapButton.setVisibility(VISIBLE);
                loadPreviousMapButton.setOnClickListener(__ -> {
                    try {
                        String json = new Scanner(metricsFile).useDelimiter("\\A").next();
                        JSONObject m = new JSONObject(json);
                        // read dimensions
                        String mapH = String.valueOf(m.getDouble("h"));
                        String mapW = String.valueOf(m.getDouble("w"));
                        // read the exact content:// URI we saved in DisplayMap
                        String uriStr = m.getString("imageUri");

                        Uri resumeUri = Uri.parse(uriStr);
                        // (we already took persistable permission on pick)

                        Bundle args = new Bundle();
                        args.putString("imageUri", resumeUri.toString());
                        args.putString("mapHeight", mapH);
                        args.putString("mapWidth", mapW);
                        args.putString("mapFolderPath", lastMapPath);

                        PreprocessMap frag = new PreprocessMap();
                        frag.setArguments(args);
                        getParentFragmentManager().beginTransaction()
                                .replace(R.id.container, frag)
                                .addToBackStack(null)
                                .commit();

                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(),
                                getString(R.string.Error_loading_saved_map_metrics), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
        // --- END RESUME FLOW ---

        // permission request launcher
        ActivityResultLauncher<String> permLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        granted -> {
                            if (!granted) {
                                Toast.makeText(getContext(),
                                        getString(R.string.Storage_permission_denied), Toast.LENGTH_SHORT).show();
                            }
                        }
                );

        // image‐picker launcher (now using OPEN_DOCUMENT)
        @SuppressLint("WrongConstant") ActivityResultLauncher<Intent> pickLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            if (result.getResultCode() == Activity.RESULT_OK
                                    && result.getData() != null) {
                                Uri picked = result.getData().getData();
                                if (picked != null) {
                                    ImageUri = picked;

                                    // **persist** our read permission for this URI
                                    final int flags = result.getData().getFlags()
                                            & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                                    requireContext().getContentResolver()
                                            .takePersistableUriPermission(picked, flags);

                                    ImageSelected = true;

                                    DimensionLayout.setVisibility(VISIBLE);
                                    HomeLayout.setVisibility(GONE);
//                                    setFieldsVisibility(VISIBLE);
//                                    subtitle.setVisibility(GONE);
//                                    ImportMapImage.setVisibility(GONE);
//                                    loadPreviousMapButton.setVisibility(GONE);

                                    Toast.makeText(getContext(),
                                          getString(  R.string.Image_loaded_successfully), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(getContext(),
                                      getString(  R.string.Image_loading_failed), Toast.LENGTH_SHORT).show();
                            }
                        }
                );

        // “Import” button now triggers OPEN_DOCUMENT
        ImportMapImage.setOnClickListener(__ ->
                requestPermutationAndPick(permLauncher, pickLauncher)
        );

        ToMapStart.setOnClickListener(__ -> {
            if (ImageSelected
                    && !MapHeight.getText().toString().isEmpty()
                    && !MapWidth.getText().toString().isEmpty()) {

                Bundle args = new Bundle();
                args.putString("imageUri", ImageUri.toString());
                args.putString("mapHeight", MapHeight.getText().toString());
                args.putString("mapWidth", MapWidth.getText().toString());

                PreprocessMap frag = new PreprocessMap();
                frag.setArguments(args);
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.container, frag)
                        .addToBackStack(null)
                        .commit();

            } else {
                StringBuilder msg = new StringBuilder(getString(R.string.Please) + " ");

                if (!ImageSelected) {
                    msg.append(getString(R.string.Select_an_image)).append(" ");
                }

                if (MapHeight.getText().toString().isEmpty()) {
                    if (!ImageSelected) msg.append(getString(R.string.And)).append(" ");
                    msg.append(getString(R.string.Enter_map_height)).append(" ");
                }

                if (MapWidth.getText().toString().isEmpty()) {
                    if (!ImageSelected || MapHeight.getText().toString().isEmpty()) {
                        msg.append(getString(R.string.And)).append(" ");
                    }
                    msg.append(getString(R.string.Enter_map_width));
                }

                Toast.makeText(getContext(), msg.toString(), Toast.LENGTH_SHORT).show();
            }
        });

        return v;
    }

    private void requestPermutationAndPick(
            ActivityResultLauncher<String> permLauncher,
            ActivityResultLauncher<Intent> pickLauncher
    ) {
        String perm = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(
                requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(perm)) {
                Toast.makeText(getContext(),
                      getString(  R.string.Need_storage_access),
                        Toast.LENGTH_LONG).show();
            }
            permLauncher.launch(perm);
        } else {
            // open with persistable flags
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            pickLauncher.launch(i);
        }
    }

//    private void setFieldsVisibility(int v) {
//        DimensionText.setVisibility(v);
//        HeightText   .setVisibility(v);
//        WidthText    .setVisibility(v);
//        MapHeight    .setVisibility(v);
//        MapWidth     .setVisibility(v);
//        ToMapStart   .setVisibility(v);
//    }

    private void setupBackPressed() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        new AlertDialog.Builder(getContext())
                                .setMessage(getString(R.string.Exit_confirmation))
                                .setPositiveButton(getString(R.string.Yes), (d, w) -> requireActivity().finish())
                                .setNegativeButton(getString(R.string.No), null)
                                .show();
                    }
                }
        );
    }
}
